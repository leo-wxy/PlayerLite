#include "cache_runtime.h"

namespace cachecore {

bool CacheRuntime::ClearAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        return false;
    }

    for (auto& item : sessions_) {
        auto& session = item.second;
        if (session == nullptr || session->closed) {
            continue;
        }
        if (provider_bridge_ != nullptr && session->provider_handle > 0) {
            provider_bridge_->Close(session->provider_handle);
        }
        session->closed = true;
        session->memory_blocks.clear();
    }
    sessions_.clear();
    memory_lru_order_.clear();
    memory_lru_index_.clear();
    current_memory_bytes_ = 0;

    std::error_code ec;
    if (!std::filesystem::exists(cache_root_path_, ec) || ec) {
        return EnsureRootDirLocked();
    }

    for (const auto& entry : std::filesystem::directory_iterator(cache_root_path_)) {
        std::filesystem::remove_all(entry.path(), ec);
        if (ec) {
            return false;
        }
    }
    return EnsureRootDirLocked();
}

}  // namespace cachecore

