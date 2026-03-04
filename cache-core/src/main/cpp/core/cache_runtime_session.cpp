#include "cache_runtime.h"

#include <algorithm>

namespace cachecore {

int64_t CacheRuntime::OpenSession(
        const std::string& resource_key,
        int32_t block_size_bytes,
        int64_t content_length_hint,
        int64_t duration_ms_hint,
        int64_t provider_handle) {
    if (resource_key.empty() || provider_handle <= 0 || block_size_bytes <= 0) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        return -1;
    }

    TriggerDiskCleanupLocked();

    SessionStorageFiles storage;
    if (!EnsureStorageFilesLocked(
                resource_key,
                block_size_bytes,
                content_length_hint,
                duration_ms_hint,
                &storage)) {
        return -1;
    }

    auto session = std::make_shared<SessionState>();
    session->session_id = next_session_id_++;
    session->resource_key = resource_key;
    session->provider_handle = provider_handle;
    session->cursor.offset = 0;
    session->storage = storage;

    session->local_cache.Reset(session->storage.data_file, session->storage.block_size_bytes);
    session->local_cache.MutableTrunkIndex().SetRanges(session->storage.completed_ranges);

    const int64_t bounded_cap = std::max<int64_t>(0, memory_cache_cap_bytes_);
    const int64_t preferred_window = std::max<int64_t>(
            session->storage.block_size_bytes * 32LL,
            2 * 1024 * 1024LL);
    const int64_t minimum_window = std::max<int64_t>(
            session->storage.block_size_bytes * 16LL,
            1024 * 1024LL);

    int64_t target_window = std::min<int64_t>(preferred_window, bounded_cap);
    if (bounded_cap >= minimum_window) {
        target_window = std::max<int64_t>(target_window, minimum_window);
    }
    session->memory_window_capacity_bytes = std::max<int64_t>(0, target_window);
    session->memory_cache.Reset(static_cast<std::size_t>(session->memory_window_capacity_bytes));
    session->memory_cached_bytes.store(0);

    sessions_[session->session_id] = session;
    ReportSessionMemoryBytesLocked(session->session_id, 0);
    TouchMemorySessionLocked(session->session_id);
    EvictMemoryIfNeededLocked(session->session_id);

    // Kick off background prefetch early to improve startup latency.
    EnsurePrefetch(session, session->cursor.offset, session->read_generation.load());

    return session->session_id;
}

bool CacheRuntime::CloseSession(int64_t session_id) {
    auto session = GetSession(session_id);
    if (session == nullptr) {
        return false;
    }

    session->closed.store(true);
    session->read_generation.fetch_add(1);
    session->data_cv.notify_all();

    if (provider_bridge_ != nullptr && session->provider_handle > 0) {
        provider_bridge_->CancelInFlightRead(session->provider_handle);
    }

    (void) write_loop_.WaitIdle();

    {
        std::lock_guard<std::mutex> lock(session->mutex);
        PersistConfigLocked(*session);
    }

    if (provider_bridge_ != nullptr && session->provider_handle > 0) {
        provider_bridge_->Close(session->provider_handle);
    }

    std::lock_guard<std::mutex> lock(mutex_);
    RemoveSessionMemoryLocked(session_id);
    sessions_.erase(session_id);
    return true;
}

std::vector<uint8_t> CacheRuntime::Read(int64_t session_id, int32_t size) {
    if (size <= 0) {
        return {};
    }
    auto session = GetSession(session_id);
    if (session == nullptr || session->closed.load()) {
        return {};
    }

    int64_t offset = 0;
    uint64_t generation = 0;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->closed.load()) {
            return {};
        }
        offset = session->cursor.offset;
        generation = session->read_generation.load();
    }

    const auto bytes = ReadAtInternal(session, offset, size);

    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (!session->closed.load() &&
            session->read_generation.load() == generation &&
            session->cursor.offset == offset) {
            session->cursor.offset = offset + static_cast<int64_t>(bytes.size());
            session->prefetch_cursor_offset = session->cursor.offset;
        }
    }
    session->data_cv.notify_all();

    return bytes;
}

std::vector<uint8_t> CacheRuntime::ReadAt(int64_t session_id, int64_t offset, int32_t size) {
    if (size <= 0 || offset < 0) {
        return {};
    }
    auto session = GetSession(session_id);
    if (session == nullptr || session->closed.load()) {
        return {};
    }
    return ReadAtInternal(session, offset, size);
}

int64_t CacheRuntime::Seek(int64_t session_id, int64_t offset, int32_t whence) {
    auto session = GetSession(session_id);
    if (session == nullptr || session->closed.load()) {
        return -1;
    }

    session->read_generation.fetch_add(1);
    session->data_cv.notify_all();

    if (provider_bridge_ != nullptr && session->provider_handle > 0) {
        provider_bridge_->CancelInFlightRead(session->provider_handle);
    }

    int64_t base = 0;
    if (whence == 2) {
        int64_t length = -1;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            length = session->storage.content_length;
        }
        if (length < 0 && provider_bridge_ != nullptr && session->provider_handle > 0) {
            length = provider_bridge_->QueryContentLength(session->provider_handle);
            if (length >= 0) {
                std::lock_guard<std::mutex> lock(session->mutex);
                session->storage.content_length = length;
                PersistConfigLocked(*session);
            }
        }
        base = std::max<int64_t>(0, length);
    }

    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->closed.load()) {
        return -1;
    }

    switch (whence) {
        case 0:  // SEEK_SET
            base = 0;
            break;
        case 1:  // SEEK_CUR
            base = session->cursor.offset;
            break;
        case 2:  // SEEK_END
            break;
        default:
            return -1;
    }

    session->cursor.offset = std::max<int64_t>(0, base + offset);
    session->prefetch_cursor_offset = session->cursor.offset;
    session->data_cv.notify_all();
    return session->cursor.offset;
}

void CacheRuntime::CancelPendingRead(int64_t session_id) {
    auto session = GetSession(session_id);
    if (session == nullptr || session->closed.load()) {
        return;
    }

    session->read_generation.fetch_add(1);
    session->data_cv.notify_all();
    if (provider_bridge_ != nullptr && session->provider_handle > 0) {
        provider_bridge_->CancelInFlightRead(session->provider_handle);
    }
}

bool CacheRuntime::ReleaseProviderHandle(int64_t provider_handle) {
    if (provider_handle <= 0) {
        return false;
    }
    if (provider_bridge_ != nullptr) {
        provider_bridge_->Close(provider_handle);
    }
    return true;
}

}  // namespace cachecore
