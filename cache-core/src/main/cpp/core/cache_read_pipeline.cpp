#include "cache_runtime.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>

namespace cachecore {

namespace {
// Keep reads reasonably sized; prefetch will maintain a warm window so the
// decode thread usually hits memory/disk.
constexpr int32_t kProviderFetchMaxBytes = 256 * 1024;
constexpr int32_t kPrefetchChunkBytes = 256 * 1024;
constexpr int64_t kPrefetchAheadMinBytes = 1024 * 1024;
constexpr int32_t kReadWaitTimeoutMs = 120;
constexpr int32_t kReadStallFailMs = 12 * 1000;

constexpr const char* kLogTag = "CacheReadPipeline";
}

bool CacheRuntime::WaitForReadable(
        const std::shared_ptr<SessionState>& session,
        int64_t offset,
        uint64_t expected_generation,
        int32_t timeout_ms) {
    if (session == nullptr || offset < 0 || timeout_ms <= 0) {
        return false;
    }

    std::unique_lock<std::mutex> lock(session->mutex);
    return session->data_cv.wait_for(
            lock,
            std::chrono::milliseconds(timeout_ms),
            [session, offset, expected_generation]() {
                if (session->closed.load()) {
                    return true;
                }
                if (session->read_generation.load() != expected_generation) {
                    return true;
                }
                if (session->prefetch_failed &&
                    session->prefetch_failure_generation == expected_generation &&
                    session->prefetch_failure_offset >= 0 &&
                    offset >= session->prefetch_failure_offset) {
                    return true;
                }
                if (session->storage.content_length >= 0 && offset >= session->storage.content_length) {
                    return true;
                }

                const int64_t window_start = session->memory_cache.WindowStart();
                const int64_t window_end = session->memory_cache.WindowEnd();
                if (window_end > offset && offset >= window_start) {
                    return true;
                }
                return session->local_cache.GetTrunkIndex().AvailableSize(offset) > 0;
            });
}

void CacheRuntime::EnsurePrefetch(
        const std::shared_ptr<SessionState>& session,
        int64_t offset,
        uint64_t expected_generation) {
    if (session == nullptr || offset < 0) {
        return;
    }

    bool should_start = false;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->closed.load() || session->read_generation.load() != expected_generation) {
            return;
        }

        session->prefetch_cursor_offset = offset;
        if (!session->prefetch_running || session->prefetch_generation != expected_generation) {
            session->prefetch_running = true;
            session->prefetch_generation = expected_generation;
            should_start = true;
        }
        session->prefetch_failed = false;
        session->prefetch_failure_generation = 0;
        session->prefetch_failure_offset = -1;
    }

    // Wake an existing prefetch loop if it's waiting.
    session->data_cv.notify_all();

    if (!should_start) {
        return;
    }

    const std::weak_ptr<SessionState> weak_session(session);
    const bool posted = render_loop_.Post([this, weak_session, expected_generation]() {
        PrefetchLoop(weak_session, expected_generation);
    });
    if (!posted) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "EnsurePrefetch post failed: expectedGen=%llu",
                static_cast<unsigned long long>(expected_generation));
        std::lock_guard<std::mutex> lock(session->mutex);
        session->prefetch_running = false;
        session->prefetch_generation = 0;
    } else {
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "EnsurePrefetch start: session=%lld expectedGen=%llu offset=%lld",
                static_cast<long long>(session->session_id),
                static_cast<unsigned long long>(expected_generation),
                static_cast<long long>(offset));
    }
}

void CacheRuntime::PrefetchLoop(
        std::weak_ptr<SessionState> weak_session,
        uint64_t expected_generation) {
    auto session = weak_session.lock();
    if (session == nullptr) {
        return;
    }
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "PrefetchLoop enter: session=%lld expectedGen=%llu",
            static_cast<long long>(session->session_id),
            static_cast<unsigned long long>(expected_generation));

    while (true) {
        int64_t cursor = 0;
        int64_t content_length = -1;
        int64_t target_end = 0;
        int64_t window_end = 0;
        int64_t fetch_offset = 0;
        int32_t fetch_size = 0;
        int64_t provider_handle = -1;

        {
            std::unique_lock<std::mutex> lock(session->mutex);
            if (session->closed.load() || session->read_generation.load() != expected_generation) {
                break;
            }

            cursor = std::max<int64_t>(0, session->prefetch_cursor_offset);
            content_length = session->storage.content_length;
            provider_handle = session->provider_handle;

            const int64_t capacity = std::max<int64_t>(0, session->memory_window_capacity_bytes);
            if (capacity <= 0) {
                // Nothing to prefetch into.
                session->prefetch_running = false;
                session->prefetch_generation = 0;
                break;
            }

            const int64_t target_ahead = std::min<int64_t>(
                    capacity,
                    std::max<int64_t>(kPrefetchAheadMinBytes, capacity));
            target_end = cursor + target_ahead;
            if (content_length >= 0) {
                if (cursor >= content_length) {
                    // EOF.
                    session->prefetch_running = false;
                    session->prefetch_generation = 0;
                    break;
                }
                target_end = std::min<int64_t>(target_end, content_length);
            }

            window_end = session->memory_cache.WindowEnd();
            fetch_offset = std::max<int64_t>(cursor, window_end);
            if (fetch_offset >= target_end) {
                // Already prefetched enough; wait for consumer to advance.
                session->data_cv.wait_for(lock, std::chrono::milliseconds(200));
                continue;
            }

            fetch_size = static_cast<int32_t>(std::min<int64_t>(
                    kPrefetchChunkBytes,
                    target_end - fetch_offset));
            fetch_size = std::max<int32_t>(1, fetch_size);
        }

        if (provider_bridge_ == nullptr || provider_handle <= 0) {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->prefetch_running = false;
            session->prefetch_generation = 0;
            break;
        }

        // Drop prefetch on cancellation/seek to avoid wasting network time.
        if (session->closed.load() || session->read_generation.load() != expected_generation) {
            break;
        }

        std::atomic<int32_t> delivered{0};
        ProviderBridge::StreamCallbacks callbacks;
        callbacks.on_data = [this, weak_session, expected_generation, &delivered](
                                    int64_t chunk_offset,
                                    const std::vector<uint8_t>& chunk) -> bool {
            if (chunk.empty()) {
                return true;
            }
            auto session = weak_session.lock();
            if (session == nullptr) {
                return false;
            }

            if (session->closed.load() || session->read_generation.load() != expected_generation) {
                return false;
            }

            bool accepted = false;
            {
                std::lock_guard<std::mutex> lock(session->mutex);
                if (!session->closed.load() &&
                    session->read_generation.load() == expected_generation) {
                    session->memory_cache.Write(chunk_offset, chunk);
                    session->memory_cached_bytes.store(static_cast<int64_t>(session->memory_cache.Size()));
                    session->storage.last_access_epoch_ms = NowEpochMs();
                    accepted = true;
                }
            }
            if (!accepted) {
                return false;
            }

            delivered.fetch_add(static_cast<int32_t>(chunk.size()));
            session->data_cv.notify_all();

            {
                std::lock_guard<std::mutex> lock(mutex_);
                TouchMemorySessionLocked(session->session_id);
                ReportSessionMemoryBytesLocked(session->session_id, session->memory_cached_bytes.load());
                EvictMemoryIfNeededLocked(session->session_id);
            }

            ScheduleBlockPersist(session, chunk_offset, std::vector<uint8_t>(chunk), expected_generation);
            return true;
        };

        const bool streamed = provider_bridge_->ReadAtStream(
                provider_handle,
                fetch_offset,
                fetch_size,
                callbacks);
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "PrefetchLoop fetch done: session=%lld offset=%lld size=%d delivered=%d",
                static_cast<long long>(session->session_id),
                static_cast<long long>(fetch_offset),
                fetch_size,
                delivered.load());

        {
            std::lock_guard<std::mutex> lock(session->mutex);
            if (!session->closed.load() && session->read_generation.load() == expected_generation) {
                if (delivered.load() > 0) {
                    session->prefetch_failed = false;
                    session->prefetch_failure_generation = 0;
                    session->prefetch_failure_offset = -1;
                } else if (!streamed) {
                    session->prefetch_failed = true;
                    session->prefetch_failure_generation = expected_generation;
                    session->prefetch_failure_offset = fetch_offset;
                }
            }
        }
        session->data_cv.notify_all();

        if (delivered.load() <= 0) {
            if (!streamed) {
                std::lock_guard<std::mutex> lock(session->mutex);
                if (session->prefetch_generation == expected_generation) {
                    session->prefetch_running = false;
                    session->prefetch_generation = 0;
                }
                break;
            }
            // Avoid spinning hard on repeated failures.
            std::unique_lock<std::mutex> lock(session->mutex);
            if (session->closed.load() || session->read_generation.load() != expected_generation) {
                break;
            }
            session->data_cv.wait_for(lock, std::chrono::milliseconds(200));
        }
    }

    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->prefetch_generation == expected_generation) {
            session->prefetch_running = false;
            session->prefetch_generation = 0;
        }
    }
    session->data_cv.notify_all();
}

std::vector<uint8_t> CacheRuntime::ReadAtInternal(
        const std::shared_ptr<SessionState>& session,
        int64_t offset,
        int32_t size) {
    if (session == nullptr || offset < 0 || size <= 0 || session->closed.load()) {
        return {};
    }

    // FFmpeg will keep calling our read callback; always return the bytes we have
    // available, and block until at least 1 byte is readable (unless EOF/close).
    const auto stall_start = std::chrono::steady_clock::now();
    while (true) {
        int64_t known_content_length = -1;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            known_content_length = session->storage.content_length;
        }
        if (known_content_length < 0 || offset >= known_content_length) {
            (void) RefreshContentLengthFromProvider(session);
        }

        if (session->closed.load()) {
            return {};
        }
        const uint64_t generation = session->read_generation.load();
        EnsurePrefetch(session, offset, generation);

        int64_t content_length = -1;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            content_length = session->storage.content_length;
            session->prefetch_cursor_offset = offset;
        }

        if (content_length >= 0 && offset >= content_length) {
            // EOF.
            return {};
        }

        int32_t desired = size;
        if (content_length >= 0) {
            desired = std::min<int32_t>(
                    desired,
                    static_cast<int32_t>(std::max<int64_t>(0, content_length - offset)));
        }
        if (desired <= 0) {
            return {};
        }

        std::vector<uint8_t> segment;
        bool hit = false;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            const int64_t window_start = session->memory_cache.WindowStart();
            const int64_t window_end = session->memory_cache.WindowEnd();
            if (offset >= window_start && window_end > offset) {
                const int64_t available = window_end - offset;
                const int32_t read_size = static_cast<int32_t>(std::min<int64_t>(
                        desired,
                        available));
                if (read_size > 0) {
                    hit = session->memory_cache.Read(offset, read_size, &segment);
                }
            }
            if (hit) {
                session->memory_cached_bytes.store(static_cast<int64_t>(session->memory_cache.Size()));
            }
        }

        if (hit && !segment.empty()) {
            std::lock_guard<std::mutex> lock(mutex_);
            TouchMemorySessionLocked(session->session_id);
            ReportSessionMemoryBytesLocked(session->session_id, session->memory_cached_bytes.load());
            EvictMemoryIfNeededLocked(session->session_id);
            return segment;
        }

        {
            std::lock_guard<std::mutex> lock(session->mutex);
            const int64_t available = session->local_cache.GetTrunkIndex().AvailableSize(offset);
            if (available > 0) {
                const auto read_size = std::min<int32_t>(desired, static_cast<int32_t>(available));
                segment = session->local_cache.Read(offset, read_size);
                if (!segment.empty()) {
                    session->memory_cache.Write(offset, segment);
                    session->memory_cached_bytes.store(static_cast<int64_t>(session->memory_cache.Size()));
                    hit = true;
                }
            }
        }

        if (hit && !segment.empty()) {
            std::lock_guard<std::mutex> lock(mutex_);
            TouchMemorySessionLocked(session->session_id);
            ReportSessionMemoryBytesLocked(session->session_id, session->memory_cached_bytes.load());
            EvictMemoryIfNeededLocked(session->session_id);
            return segment;
        }

        EnsurePrefetch(session, offset, generation);
        (void) WaitForReadable(session, offset, generation, kReadWaitTimeoutMs);

        bool prefetch_failed = false;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            prefetch_failed =
                    session->prefetch_failed &&
                    session->prefetch_failure_generation == generation &&
                    session->prefetch_failure_offset >= 0 &&
                    offset >= session->prefetch_failure_offset;
        }
        if (prefetch_failed) {
            int32_t streamed_bytes = 0;
            bool cancelled = false;
            if (FetchBlockFromProvider(
                        session,
                        offset,
                        desired,
                        generation,
                        &streamed_bytes,
                        &cancelled) &&
                streamed_bytes > 0) {
                {
                    std::lock_guard<std::mutex> lock(session->mutex);
                    if (session->read_generation.load() == generation) {
                        session->prefetch_failed = false;
                        session->prefetch_failure_generation = 0;
                        session->prefetch_failure_offset = -1;
                    }
                }
                session->data_cv.notify_all();
                continue;
            }
            if (cancelled) {
                continue;
            }
            return {};
        }

        const auto stall_elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - stall_start);
        if (stall_elapsed_ms.count() >= kReadStallFailMs) {
            __android_log_print(
                    ANDROID_LOG_WARN,
                    kLogTag,
                    "ReadAtInternal stall timeout: session=%lld offset=%lld size=%d waitedMs=%lld",
                    static_cast<long long>(session->session_id),
                    static_cast<long long>(offset),
                    size,
                    static_cast<long long>(stall_elapsed_ms.count()));
            return {};
        }
    }
}

bool CacheRuntime::FetchBlockFromProvider(
        const std::shared_ptr<SessionState>& session,
        int64_t request_offset,
        int32_t request_size,
        uint64_t expected_generation,
        int32_t* out_streamed_bytes,
        bool* out_cancelled) {
    if (session == nullptr || out_streamed_bytes == nullptr || out_cancelled == nullptr || request_size <= 0) {
        return false;
    }

    *out_cancelled = false;
    *out_streamed_bytes = 0;

    int32_t fetch_size = request_size;
    int32_t block_size_hint = request_size;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        block_size_hint = std::max(1, session->storage.block_size_bytes);
        fetch_size = std::max<int32_t>(
                request_size,
                std::min<int32_t>(block_size_hint, kProviderFetchMaxBytes));
        if (session->storage.content_length >= 0) {
            const int64_t remaining = std::max<int64_t>(0, session->storage.content_length - request_offset);
            if (remaining <= 0) {
                return false;
            }
            fetch_size = static_cast<int32_t>(std::min<int64_t>(fetch_size, remaining));
        }
    }
    fetch_size = std::max<int32_t>(1, fetch_size);

    const int32_t max_attempts = std::max(1, read_retry_count_ + 1);
    for (int32_t attempt = 0; attempt < max_attempts; ++attempt) {
        if (session->closed.load() || session->read_generation.load() != expected_generation) {
            *out_cancelled = true;
            return false;
        }

        if (provider_bridge_ == nullptr || session->provider_handle <= 0) {
            return false;
        }

        std::atomic<int32_t> streamed_bytes{0};
        ProviderBridge::StreamCallbacks callbacks;
        callbacks.on_data = [this, session, expected_generation, &streamed_bytes](
                                    int64_t chunk_offset,
                                    const std::vector<uint8_t>& chunk) -> bool {
            if (chunk.empty()) {
                return true;
            }

            bool accepted = false;
            {
                std::lock_guard<std::mutex> lock(session->mutex);
                if (!session->closed.load() &&
                    session->read_generation.load() == expected_generation) {
                    session->memory_cache.Write(chunk_offset, chunk);
                    session->memory_cached_bytes.store(static_cast<int64_t>(session->memory_cache.Size()));
                    session->storage.last_access_epoch_ms = NowEpochMs();
                    accepted = true;
                }
            }
            if (!accepted) {
                return false;
            }

            streamed_bytes.fetch_add(static_cast<int32_t>(chunk.size()));
            ScheduleBlockPersist(session, chunk_offset, std::vector<uint8_t>(chunk), expected_generation);
            return true;
        };

        bool streamed = false;
        const bool posted = render_loop_.PostAndWait([this, &streamed, session, request_offset, fetch_size, &callbacks] {
            streamed = provider_bridge_->ReadAtStream(
                    session->provider_handle,
                    request_offset,
                    fetch_size,
                    callbacks);
        });
        if (!posted) {
            // Fallback to current JNI caller thread when async worker cannot resolve
            // provider callbacks (for example class-loader scoped JNI lookup issues).
            streamed = provider_bridge_->ReadAtStream(
                    session->provider_handle,
                    request_offset,
                    fetch_size,
                    callbacks);
        }

        const int32_t delivered = streamed_bytes.load();
        if (delivered > 0) {
            *out_streamed_bytes = delivered;
            return true;
        }

        if (session->closed.load() || session->read_generation.load() != expected_generation) {
            *out_cancelled = true;
            return false;
        }
    }

    return false;
}

void CacheRuntime::ScheduleBlockPersist(
        const std::shared_ptr<SessionState>& session,
        int64_t block_start_offset,
        std::vector<uint8_t> bytes,
        uint64_t /*expected_generation*/) {
    if (session == nullptr || bytes.empty() || block_start_offset < 0) {
        return;
    }

    const std::weak_ptr<SessionState> weak_session(session);
    const bool posted = write_loop_.Post([this, weak_session, block_start_offset, bytes = std::move(bytes)]() mutable {
        auto locked = weak_session.lock();
        if (locked == nullptr) {
            return;
        }

        std::lock_guard<std::mutex> lock(locked->mutex);
        if (!locked->local_cache.Write(block_start_offset, bytes)) {
            return;
        }

        locked->storage.completed_ranges = locked->local_cache.GetTrunkIndex().Ranges();
        locked->storage.cached_blocks =
                locked->local_cache.GetTrunkIndex().ToBlockSet(locked->storage.block_size_bytes);

        if (provider_bridge_ != nullptr && locked->provider_handle > 0) {
            const auto content_length = provider_bridge_->QueryContentLength(locked->provider_handle);
            if (content_length > 0 &&
                (locked->storage.content_length < 0 || content_length > locked->storage.content_length)) {
                locked->storage.content_length = content_length;
            }
        }

        locked->storage.last_access_epoch_ms = NowEpochMs();
        PersistConfigLocked(*locked);
    });

    if (!posted) {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->local_cache.Write(block_start_offset, bytes)) {
            session->storage.completed_ranges = session->local_cache.GetTrunkIndex().Ranges();
            session->storage.cached_blocks =
                    session->local_cache.GetTrunkIndex().ToBlockSet(session->storage.block_size_bytes);
            if (provider_bridge_ != nullptr && session->provider_handle > 0) {
                const auto content_length = provider_bridge_->QueryContentLength(session->provider_handle);
                if (content_length > 0 &&
                    (session->storage.content_length < 0 || content_length > session->storage.content_length)) {
                    session->storage.content_length = content_length;
                }
            }
            session->storage.last_access_epoch_ms = NowEpochMs();
            PersistConfigLocked(*session);
        }
    }
}

}  // namespace cachecore
