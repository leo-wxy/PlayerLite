#include "cache_runtime.h"

namespace cachecore {

bool CacheRuntime::Init(const std::string& cache_root_path) {
    if (cache_root_path.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    cache_root_path_ = cache_root_path;
    sessions_.clear();
    next_session_id_ = 1;
    initialized_ = true;
    return true;
}

void CacheRuntime::Shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    sessions_.clear();
    cache_root_path_.clear();
    next_session_id_ = 1;
    initialized_ = false;
}

bool CacheRuntime::IsInitialized() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return initialized_;
}

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
