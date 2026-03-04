#include "cache_runtime.h"

namespace cachecore {

bool CacheRuntime::TriggerDiskCleanupLocked() {
    if (!initialized_) {
        return false;
    }
    return cache_control_.CleanupIfNeeded(cache_root_path_, CollectUsingResourceKeysLocked());
}

bool CacheRuntime::ClearAll() {
    std::vector<std::shared_ptr<SessionState>> sessions;
    std::filesystem::path root;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!initialized_) {
            return false;
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
        root = cache_root_path_;
    }

    for (const auto& session : sessions) {
        if (session == nullptr) {
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

    std::error_code ec;
    if (!std::filesystem::exists(root, ec) || ec) {
        std::lock_guard<std::mutex> lock(mutex_);
        return EnsureRootDirLocked();
    }

    for (const auto& entry : std::filesystem::directory_iterator(root)) {
        std::filesystem::remove_all(entry.path(), ec);
        if (ec) {
            return false;
        }
    }

    std::lock_guard<std::mutex> lock(mutex_);
    return EnsureRootDirLocked();
}

}  // namespace cachecore
