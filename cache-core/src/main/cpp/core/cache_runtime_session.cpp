#include "cache_runtime.h"

namespace cachecore {

int64_t CacheRuntime::OpenSession(const std::string& resource_key) {
    if (resource_key.empty()) {
        return -1;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        return -1;
    }
    const int64_t session_id = next_session_id_++;
    sessions_[session_id] = resource_key;
    return session_id;
}

bool CacheRuntime::CloseSession(int64_t session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    return sessions_.erase(session_id) > 0;
}

}  // namespace cachecore

