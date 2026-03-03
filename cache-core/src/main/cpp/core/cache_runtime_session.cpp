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

    SessionStorageFiles storage;
    if (!EnsureStorageFilesLocked(
                resource_key,
                block_size_bytes,
                content_length_hint,
                duration_ms_hint,
                &storage)) {
        return -1;
    }

    const int64_t session_id = next_session_id_++;
    auto session = std::make_shared<SessionState>();
    session->session_id = session_id;
    session->resource_key = resource_key;
    session->provider_handle = provider_handle;
    session->current_offset = 0;
    session->closed = false;
    session->storage = std::move(storage);

    sessions_[session_id] = session;
    return session_id;
}

bool CacheRuntime::CloseSession(int64_t session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = sessions_.find(session_id);
    if (found == sessions_.end() || found->second == nullptr) {
        return false;
    }

    auto session = found->second;
    if (!session->closed) {
        PersistConfigLocked(*session);
        if (provider_bridge_ != nullptr && session->provider_handle > 0) {
            provider_bridge_->Close(session->provider_handle);
        }
        session->closed = true;
    }
    RemoveSessionMemoryLocked(*session);
    sessions_.erase(found);
    return true;
}

std::vector<uint8_t> CacheRuntime::Read(int64_t session_id, int32_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = sessions_.find(session_id);
    if (found == sessions_.end() || found->second == nullptr || found->second->closed || size <= 0) {
        return {};
    }
    auto session = found->second.get();
    const auto result = ReadAtLocked(session, session->current_offset, size);
    session->current_offset += static_cast<int64_t>(result.size());
    return result;
}

std::vector<uint8_t> CacheRuntime::ReadAt(int64_t session_id, int64_t offset, int32_t size) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = sessions_.find(session_id);
    if (found == sessions_.end() || found->second == nullptr || found->second->closed || size <= 0 || offset < 0) {
        return {};
    }
    return ReadAtLocked(found->second.get(), offset, size);
}

int64_t CacheRuntime::Seek(int64_t session_id, int64_t offset, int32_t whence) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = sessions_.find(session_id);
    if (found == sessions_.end() || found->second == nullptr || found->second->closed) {
        return -1;
    }
    auto session = found->second.get();

    if (provider_bridge_ != nullptr && session->provider_handle > 0) {
        provider_bridge_->CancelInFlightRead(session->provider_handle);
    }

    int64_t base = 0;
    switch (whence) {
        case 0:  // SEEK_SET
            base = 0;
            break;
        case 1:  // SEEK_CUR
            base = session->current_offset;
            break;
        case 2:  // SEEK_END
            if (session->storage.content_length >= 0) {
                base = session->storage.content_length;
            } else if (provider_bridge_ != nullptr && session->provider_handle > 0) {
                const auto queried = provider_bridge_->QueryContentLength(session->provider_handle);
                base = queried >= 0 ? queried : 0;
            } else {
                base = 0;
            }
            break;
        default:
            return -1;
    }

    const int64_t target = std::max<int64_t>(0, base + offset);
    session->current_offset = target;
    return session->current_offset;
}

void CacheRuntime::CancelPendingRead(int64_t session_id) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto found = sessions_.find(session_id);
    if (found == sessions_.end() || found->second == nullptr || found->second->closed) {
        return;
    }
    if (provider_bridge_ != nullptr && found->second->provider_handle > 0) {
        provider_bridge_->CancelInFlightRead(found->second->provider_handle);
    }
}

bool CacheRuntime::ReleaseProviderHandle(int64_t provider_handle) {
    if (provider_handle <= 0) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (provider_bridge_ != nullptr) {
        provider_bridge_->Close(provider_handle);
    }
    return true;
}

}  // namespace cachecore

