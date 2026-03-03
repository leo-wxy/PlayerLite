#include "cache_runtime.h"

#include <chrono>

namespace cachecore {

bool CacheRuntime::Init(const std::string& cache_root_path, int64_t memory_cache_cap_bytes) {
    if (cache_root_path.empty() || memory_cache_cap_bytes <= 0) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    cache_root_path_ = std::filesystem::path(cache_root_path);
    memory_cache_cap_bytes_ = memory_cache_cap_bytes;
    next_session_id_ = 1;
    sessions_.clear();
    memory_lru_order_.clear();
    memory_lru_index_.clear();
    current_memory_bytes_ = 0;

    initialized_ = EnsureRootDirLocked();
    return initialized_;
}

void CacheRuntime::Shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& item : sessions_) {
        auto& session = item.second;
        if (session == nullptr || session->closed) {
            continue;
        }
        if (provider_bridge_ != nullptr && session->provider_handle > 0) {
            provider_bridge_->Close(session->provider_handle);
        }
        session->closed = true;
    }
    sessions_.clear();
    memory_lru_order_.clear();
    memory_lru_index_.clear();
    current_memory_bytes_ = 0;
    next_session_id_ = 1;
    cache_root_path_.clear();
    initialized_ = false;
}

bool CacheRuntime::IsInitialized() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return initialized_;
}

void CacheRuntime::SetProviderBridge(ProviderBridge* bridge) {
    std::lock_guard<std::mutex> lock(mutex_);
    provider_bridge_ = bridge;
}

bool CacheRuntime::EnsureRootDirLocked() {
    if (cache_root_path_.empty()) {
        return false;
    }
    std::error_code ec;
    if (std::filesystem::exists(cache_root_path_, ec)) {
        return !ec;
    }
    std::filesystem::create_directories(cache_root_path_, ec);
    return !ec;
}

std::filesystem::path CacheRuntime::RootPathLocked() const {
    return cache_root_path_;
}

int64_t CacheRuntime::NowEpochMs() {
    const auto now = std::chrono::time_point_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now());
    return now.time_since_epoch().count();
}

}  // namespace cachecore

