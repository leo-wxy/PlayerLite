#include "cache_runtime.h"

#include <algorithm>
#include <cstring>

namespace cachecore {

std::optional<CacheLookupSnapshot> CacheRuntime::Lookup(const std::string& resource_key) {
    if (resource_key.empty()) {
        return std::nullopt;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_) {
        return std::nullopt;
    }
    return LookupLocked(resource_key);
}

std::vector<CacheLookupSnapshot> CacheRuntime::LookupByPrefix(const std::string& prefix, int32_t limit) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || limit <= 0) {
        return {};
    }

    auto keys_set = CollectResourceKeysLocked();
    std::vector<std::string> keys;
    keys.reserve(keys_set.size());
    for (const auto& key : keys_set) {
        if (key.rfind(prefix, 0) == 0) {
            keys.push_back(key);
        }
    }
    std::sort(keys.begin(), keys.end());
    if (static_cast<int32_t>(keys.size()) > limit) {
        keys.resize(limit);
    }

    std::vector<CacheLookupSnapshot> snapshots;
    snapshots.reserve(keys.size());
    for (const auto& key : keys) {
        auto snapshot = LookupLocked(key);
        if (snapshot.has_value()) {
            snapshots.push_back(snapshot.value());
        }
    }
    return snapshots;
}

std::optional<CacheLookupSnapshot> CacheRuntime::LookupLocked(const std::string& resource_key) {
    const auto data_file = cache_root_path_ / (resource_key + ".data");
    const auto config_file = cache_root_path_ / (resource_key + "_config.json");
    const auto extra_file = cache_root_path_ / (resource_key + "_extra.json");

    std::error_code ec;
    const bool data_exists = std::filesystem::exists(data_file, ec) && !ec;
    const bool config_exists = std::filesystem::exists(config_file, ec) && !ec;
    const bool extra_exists = std::filesystem::exists(extra_file, ec) && !ec;
    if (!data_exists && !config_exists && !extra_exists) {
        return std::nullopt;
    }

    CacheLookupSnapshot snapshot;
    snapshot.resource_key = resource_key;
    snapshot.data_file_path = data_file.string();
    snapshot.config_file_path = config_file.string();
    snapshot.extra_file_path = extra_file.string();
    snapshot.data_file_size_bytes = data_exists ? static_cast<int64_t>(std::filesystem::file_size(data_file, ec)) : 0;
    if (ec) {
        snapshot.data_file_size_bytes = 0;
    }

    if (config_exists) {
        const auto parsed = ReadStorageSnapshotLocked(
                config_file,
                64 * 1024,
                -1,
                -1);
        snapshot.block_size_bytes = parsed.block_size_bytes;
        snapshot.content_length = parsed.content_length;
        snapshot.duration_ms = parsed.duration_ms;
        snapshot.last_access_epoch_ms = parsed.last_access_epoch_ms;
        snapshot.cached_blocks.assign(parsed.block_indexes.begin(), parsed.block_indexes.end());
        std::sort(snapshot.cached_blocks.begin(), snapshot.cached_blocks.end());
    }

    return snapshot;
}

std::unordered_set<std::string> CacheRuntime::CollectResourceKeysLocked() const {
    std::unordered_set<std::string> keys;
    std::error_code ec;
    if (!std::filesystem::exists(cache_root_path_, ec) || ec) {
        return keys;
    }

    for (const auto& entry : std::filesystem::directory_iterator(cache_root_path_)) {
        const auto file_name = entry.path().filename().string();
        auto resource_key = ParseResourceKeyFromFileName(file_name);
        if (resource_key.has_value() && !resource_key.value().empty()) {
            keys.insert(resource_key.value());
        }
    }
    return keys;
}

std::optional<std::string> CacheRuntime::ParseResourceKeyFromFileName(const std::string& file_name) {
    constexpr const char* kDataSuffix = ".data";
    constexpr const char* kConfigSuffix = "_config.json";
    constexpr const char* kExtraSuffix = "_extra.json";

    if (file_name.size() > std::strlen(kConfigSuffix) &&
        file_name.rfind(kConfigSuffix) == file_name.size() - std::strlen(kConfigSuffix)) {
        return file_name.substr(0, file_name.size() - std::strlen(kConfigSuffix));
    }
    if (file_name.size() > std::strlen(kExtraSuffix) &&
        file_name.rfind(kExtraSuffix) == file_name.size() - std::strlen(kExtraSuffix)) {
        return file_name.substr(0, file_name.size() - std::strlen(kExtraSuffix));
    }
    if (file_name.size() > std::strlen(kDataSuffix) &&
        file_name.rfind(kDataSuffix) == file_name.size() - std::strlen(kDataSuffix)) {
        return file_name.substr(0, file_name.size() - std::strlen(kDataSuffix));
    }
    return std::nullopt;
}

}  // namespace cachecore
