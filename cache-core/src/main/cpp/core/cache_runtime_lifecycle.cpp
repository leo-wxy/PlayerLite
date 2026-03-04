#include "cache_runtime.h"

#include <chrono>

namespace cachecore {

bool CacheRuntime::Init(const std::string& cache_root_path, int64_t memory_cache_cap_bytes) {
    return Init(
            cache_root_path,
            memory_cache_cap_bytes,
            500L * 1024L * 1024L,
            0.8,
            1.0,
            3);
}

bool CacheRuntime::Init(
        const std::string& cache_root_path,
        int64_t memory_cache_cap_bytes,
        int64_t disk_cache_max_bytes,
        double disk_cache_clean_range_min,
        double disk_cache_clean_range_max,
        int32_t read_retry_count) {
    if (cache_root_path.empty() || memory_cache_cap_bytes <= 0 || disk_cache_max_bytes <= 0 ||
        disk_cache_clean_range_min <= 0.0 || disk_cache_clean_range_max <= 0.0 ||
        disk_cache_clean_range_min > disk_cache_clean_range_max ||
        disk_cache_clean_range_max > 1.0 || read_retry_count < 0) {
        return false;
    }

    Shutdown();

    std::lock_guard<std::mutex> lock(mutex_);
    cache_root_path_ = std::filesystem::path(cache_root_path);
    memory_cache_cap_bytes_ = memory_cache_cap_bytes;
    disk_cache_max_bytes_ = disk_cache_max_bytes;
    disk_cache_clean_range_min_ = disk_cache_clean_range_min;
    disk_cache_clean_range_max_ = disk_cache_clean_range_max;
    read_retry_count_ = read_retry_count;

    next_session_id_ = 1;
    sessions_.clear();
    memory_lru_order_.clear();
    memory_lru_index_.clear();
    memory_session_bytes_.clear();
    current_memory_bytes_ = 0;

    cache_control_.Configure(
            disk_cache_max_bytes_,
            disk_cache_clean_range_min_,
            disk_cache_clean_range_max_);

    render_loop_.Start("cache-render");
    write_loop_.Start("cache-write");

    initialized_ = EnsureRootDirLocked();
    if (!initialized_) {
        render_loop_.Stop(false);
        write_loop_.Stop(false);
    }
    return initialized_;
}

void CacheRuntime::Shutdown() {
    std::vector<std::shared_ptr<SessionState>> sessions;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!initialized_ && sessions_.empty()) {
            render_loop_.Stop(false);
            write_loop_.Stop(false);
            return;
        }

        for (auto& item : sessions_) {
            if (item.second != nullptr) {
                sessions.push_back(item.second);
            }
        }

        sessions_.clear();
        memory_lru_order_.clear();
        memory_lru_index_.clear();
        memory_session_bytes_.clear();
        current_memory_bytes_ = 0;
        next_session_id_ = 1;
        initialized_ = false;
        cache_root_path_.clear();
    }

    for (const auto& session : sessions) {
        if (!session) {
            continue;
        }
        session->closed.store(true);
        session->read_generation.fetch_add(1);
        if (provider_bridge_ != nullptr && session->provider_handle > 0) {
            provider_bridge_->CancelInFlightRead(session->provider_handle);
            provider_bridge_->Close(session->provider_handle);
        }
    }

    (void) write_loop_.WaitIdle();
    render_loop_.Stop(false);
    write_loop_.Stop(true);
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

std::shared_ptr<CacheRuntime::SessionState> CacheRuntime::GetSession(int64_t session_id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        return nullptr;
    }
    auto found = sessions_.find(session_id);
    if (found == sessions_.end()) {
        return nullptr;
    }
    return found->second;
}

std::unordered_set<std::string> CacheRuntime::CollectUsingResourceKeysLocked() const {
    std::unordered_set<std::string> keys;
    for (const auto& item : sessions_) {
        if (item.second == nullptr || item.second->closed.load()) {
            continue;
        }
        keys.insert(item.second->resource_key);
    }
    return keys;
}

}  // namespace cachecore
