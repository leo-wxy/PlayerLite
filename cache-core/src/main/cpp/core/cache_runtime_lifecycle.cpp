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

}  // namespace cachecore

