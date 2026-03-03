#pragma once

#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>

namespace cachecore {

class CacheRuntime {
public:
    bool Init(const std::string& cache_root_path);
    void Shutdown();
    bool IsInitialized() const;

    int64_t OpenSession(const std::string& resource_key);
    bool CloseSession(int64_t session_id);

private:
    mutable std::mutex mutex_;
    bool initialized_ = false;
    std::string cache_root_path_;
    int64_t next_session_id_ = 1;
    std::unordered_map<int64_t, std::string> sessions_;
};

}  // namespace cachecore
