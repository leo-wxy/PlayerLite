#include "cache_runtime.h"

namespace cachecore {

void CacheRuntime::TouchMemorySessionLocked(int64_t session_id) {
    auto found = memory_lru_index_.find(session_id);
    if (found != memory_lru_index_.end()) {
        memory_lru_order_.erase(found->second);
        memory_lru_order_.push_front(session_id);
        found->second = memory_lru_order_.begin();
        return;
    }

    memory_lru_order_.push_front(session_id);
    memory_lru_index_[session_id] = memory_lru_order_.begin();
}

void CacheRuntime::ReportSessionMemoryBytesLocked(int64_t session_id, int64_t bytes) {
    const int64_t bounded = bytes > 0 ? bytes : 0;
    const auto found = memory_session_bytes_.find(session_id);
    const int64_t old_value = found == memory_session_bytes_.end() ? 0 : found->second;
    memory_session_bytes_[session_id] = bounded;
    current_memory_bytes_ += (bounded - old_value);
    if (current_memory_bytes_ < 0) {
        current_memory_bytes_ = 0;
    }
}

void CacheRuntime::EvictMemoryIfNeededLocked(int64_t protected_session_id) {
    if (memory_cache_cap_bytes_ <= 0) {
        return;
    }

    while (current_memory_bytes_ > memory_cache_cap_bytes_ && !memory_lru_order_.empty()) {
        int64_t candidate = -1;
        for (auto it = memory_lru_order_.rbegin(); it != memory_lru_order_.rend(); ++it) {
            if (*it == protected_session_id) {
                continue;
            }
            candidate = *it;
            break;
        }

        if (candidate < 0) {
            break;
        }

        auto index_it = memory_lru_index_.find(candidate);
        if (index_it != memory_lru_index_.end()) {
            memory_lru_order_.erase(index_it->second);
            memory_lru_index_.erase(index_it);
        }

        auto session_it = sessions_.find(candidate);
        if (session_it == sessions_.end() || session_it->second == nullptr) {
            ReportSessionMemoryBytesLocked(candidate, 0);
            continue;
        }

        auto session = session_it->second;
        {
            std::lock_guard<std::mutex> lock(session->mutex);
            session->memory_cache.Clear();
            session->memory_cached_bytes.store(0);
        }
        ReportSessionMemoryBytesLocked(candidate, 0);
    }
}

void CacheRuntime::RemoveSessionMemoryLocked(int64_t session_id) {
    auto index_it = memory_lru_index_.find(session_id);
    if (index_it != memory_lru_index_.end()) {
        memory_lru_order_.erase(index_it->second);
        memory_lru_index_.erase(index_it);
    }

    auto bytes_it = memory_session_bytes_.find(session_id);
    if (bytes_it != memory_session_bytes_.end()) {
        current_memory_bytes_ -= bytes_it->second;
        memory_session_bytes_.erase(bytes_it);
    }

    if (current_memory_bytes_ < 0) {
        current_memory_bytes_ = 0;
    }
}

}  // namespace cachecore
