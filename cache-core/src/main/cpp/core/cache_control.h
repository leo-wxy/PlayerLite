#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <unordered_set>

namespace cachecore {

class CacheControl {
public:
    void Configure(int64_t max_bytes, double clean_range_min, double clean_range_max);

    bool CleanupIfNeeded(
            const std::filesystem::path& root,
            const std::unordered_set<std::string>& using_resource_keys) const;

private:
    static std::string ParseResourceKeyFromFileName(const std::string& file_name);
    static int64_t ParseLastAccessEpochMs(const std::filesystem::path& config_file);

    int64_t max_bytes_ = 500L * 1024L * 1024L;
    double clean_range_min_ = 0.8;
    double clean_range_max_ = 1.0;
};

}  // namespace cachecore
