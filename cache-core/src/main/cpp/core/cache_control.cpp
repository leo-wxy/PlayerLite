#include "cache_control.h"

#include <algorithm>
#include <cstring>
#include <fstream>
#include <regex>
#include <sstream>
#include <unordered_map>
#include <vector>

namespace {
struct CacheEntry {
    std::string resource_key;
    std::vector<std::filesystem::path> files;
    int64_t total_bytes = 0;
    int64_t last_access_epoch_ms = -1;
};
}  // namespace

namespace cachecore {

void CacheControl::Configure(int64_t max_bytes, double clean_range_min, double clean_range_max) {
    if (max_bytes > 0) {
        max_bytes_ = max_bytes;
    }
    if (clean_range_min > 0.0 && clean_range_min <= 1.0) {
        clean_range_min_ = clean_range_min;
    }
    if (clean_range_max > 0.0 && clean_range_max <= 1.0) {
        clean_range_max_ = clean_range_max;
    }
    if (clean_range_min_ > clean_range_max_) {
        clean_range_min_ = clean_range_max_;
    }
}

bool CacheControl::CleanupIfNeeded(
        const std::filesystem::path& root,
        const std::unordered_set<std::string>& using_resource_keys) const {
    if (max_bytes_ <= 0) {
        return true;
    }

    std::error_code ec;
    if (!std::filesystem::exists(root, ec) || ec) {
        return true;
    }

    std::unordered_map<std::string, CacheEntry> grouped;
    for (const auto& entry : std::filesystem::directory_iterator(root)) {
        if (!entry.is_regular_file()) {
            continue;
        }
        const auto file_name = entry.path().filename().string();
        const auto key = ParseResourceKeyFromFileName(file_name);
        if (key.empty()) {
            continue;
        }

        auto& cache_entry = grouped[key];
        cache_entry.resource_key = key;
        cache_entry.files.push_back(entry.path());

        const auto size = static_cast<int64_t>(entry.file_size(ec));
        if (!ec && size > 0) {
            cache_entry.total_bytes += size;
        }

        if (file_name.size() > std::strlen("_config.json") &&
            file_name.rfind("_config.json") == file_name.size() - std::strlen("_config.json")) {
            cache_entry.last_access_epoch_ms = std::max(
                    cache_entry.last_access_epoch_ms,
                    ParseLastAccessEpochMs(entry.path()));
        }
    }

    std::vector<CacheEntry> entries;
    entries.reserve(grouped.size());

    int64_t total_bytes = 0;
    for (auto& item : grouped) {
        total_bytes += item.second.total_bytes;
        entries.push_back(std::move(item.second));
    }

    const int64_t trigger_threshold = static_cast<int64_t>(max_bytes_ * clean_range_max_);
    if (total_bytes <= trigger_threshold) {
        return true;
    }

    const int64_t target_threshold = static_cast<int64_t>(max_bytes_ * clean_range_min_);
    std::sort(entries.begin(), entries.end(), [](const CacheEntry& left, const CacheEntry& right) {
        if (left.last_access_epoch_ms != right.last_access_epoch_ms) {
            return left.last_access_epoch_ms < right.last_access_epoch_ms;
        }
        return left.resource_key < right.resource_key;
    });

    for (const auto& item : entries) {
        if (total_bytes <= target_threshold) {
            break;
        }
        if (using_resource_keys.find(item.resource_key) != using_resource_keys.end()) {
            continue;
        }

        for (const auto& file : item.files) {
            std::filesystem::remove(file, ec);
            if (ec) {
                return false;
            }
        }
        total_bytes -= item.total_bytes;
    }

    return true;
}

std::string CacheControl::ParseResourceKeyFromFileName(const std::string& file_name) {
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
    return "";
}

int64_t CacheControl::ParseLastAccessEpochMs(const std::filesystem::path& config_file) {
    std::ifstream input(config_file, std::ios::binary);
    std::stringstream buffer;
    buffer << input.rdbuf();
    const auto content = buffer.str();
    if (content.empty()) {
        return -1;
    }

    const std::regex pattern("\\\"lastAccessEpochMs\\\"\\s*:\\s*(-?\\d+)");
    std::smatch match;
    if (!std::regex_search(content, match, pattern) || match.size() < 2) {
        return -1;
    }

    try {
        return std::stoll(match[1].str());
    } catch (...) {
        return -1;
    }
}

}  // namespace cachecore
