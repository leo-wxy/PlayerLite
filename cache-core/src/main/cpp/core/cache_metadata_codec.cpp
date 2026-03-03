#include "cache_runtime.h"

#include <algorithm>
#include <regex>
#include <sstream>

namespace cachecore {

std::optional<int64_t> CacheRuntime::ParseLongField(const std::string& json, const std::string& field) {
    const std::regex pattern("\"" + field + "\"\\s*:\\s*(-?\\d+)");
    std::smatch match;
    if (!std::regex_search(json, match, pattern) || match.size() < 2) {
        return std::nullopt;
    }
    try {
        return std::stoll(match[1].str());
    } catch (...) {
        return std::nullopt;
    }
}

std::unordered_set<int64_t> CacheRuntime::ParseLongSetField(const std::string& json, const std::string& field) {
    const std::regex pattern("\"" + field + "\"\\s*:\\s*\\[([\\s\\S]*?)]");
    std::smatch match;
    if (!std::regex_search(json, match, pattern) || match.size() < 2) {
        return {};
    }
    const std::string body = match[1].str();
    if (body.empty()) {
        return {};
    }

    std::unordered_set<int64_t> values;
    std::stringstream ss(body);
    std::string token;
    while (std::getline(ss, token, ',')) {
        try {
            std::size_t consumed = 0;
            const auto value = std::stoll(token, &consumed);
            (void) consumed;
            values.insert(value);
        } catch (...) {
        }
    }
    return values;
}

std::string CacheRuntime::EscapeJson(const std::string& raw) {
    std::string out;
    out.reserve(raw.size() + 16);
    for (char c : raw) {
        switch (c) {
            case '\\':
                out += "\\\\";
                break;
            case '\"':
                out += "\\\"";
                break;
            case '\n':
                out += "\\n";
                break;
            case '\r':
                out += "\\r";
                break;
            case '\t':
                out += "\\t";
                break;
            default:
                out.push_back(c);
                break;
        }
    }
    return out;
}

std::string CacheRuntime::BuildConfigJson(
        const std::string& resource_key,
        int32_t block_size_bytes,
        int64_t content_length,
        int64_t duration_ms,
        const std::unordered_set<int64_t>& block_indexes,
        int64_t last_access_epoch_ms) {
    std::vector<int64_t> sorted_blocks(block_indexes.begin(), block_indexes.end());
    std::sort(sorted_blocks.begin(), sorted_blocks.end());

    std::ostringstream blocks_builder;
    for (std::size_t index = 0; index < sorted_blocks.size(); ++index) {
        if (index > 0) {
            blocks_builder << ", ";
        }
        blocks_builder << sorted_blocks[index];
    }

    std::ostringstream json;
    json << "{\n";
    json << "  \"version\": 1,\n";
    json << "  \"resourceKey\": \"" << EscapeJson(resource_key) << "\",\n";
    json << "  \"contentLength\": " << content_length << ",\n";
    json << "  \"durationMs\": " << duration_ms << ",\n";
    json << "  \"blockSizeBytes\": " << block_size_bytes << ",\n";
    json << "  \"blocks\": [" << blocks_builder.str() << "],\n";
    json << "  \"completedRanges\": [],\n";
    json << "  \"lastAccessEpochMs\": " << last_access_epoch_ms << "\n";
    json << "}\n";
    return json.str();
}

std::string CacheRuntime::BuildLookupJson(const CacheLookupSnapshot& snapshot) {
    std::ostringstream blocks_builder;
    for (std::size_t index = 0; index < snapshot.cached_blocks.size(); ++index) {
        if (index > 0) {
            blocks_builder << ", ";
        }
        blocks_builder << snapshot.cached_blocks[index];
    }

    std::ostringstream json;
    json << "{";
    json << "\"resourceKey\":\"" << EscapeJson(snapshot.resource_key) << "\",";
    json << "\"dataFilePath\":\"" << EscapeJson(snapshot.data_file_path) << "\",";
    json << "\"configFilePath\":\"" << EscapeJson(snapshot.config_file_path) << "\",";
    json << "\"extraFilePath\":\"" << EscapeJson(snapshot.extra_file_path) << "\",";
    json << "\"dataFileSizeBytes\":" << snapshot.data_file_size_bytes << ",";
    json << "\"blockSizeBytes\":" << snapshot.block_size_bytes << ",";
    json << "\"contentLength\":" << snapshot.content_length << ",";
    json << "\"durationMs\":" << snapshot.duration_ms << ",";
    json << "\"cachedBlocks\":[" << blocks_builder.str() << "],";
    json << "\"lastAccessEpochMs\":" << snapshot.last_access_epoch_ms;
    json << "}";
    return json.str();
}

std::string CacheRuntime::BuildLookupArrayJson(const std::vector<CacheLookupSnapshot>& snapshots) {
    std::ostringstream out;
    out << "[";
    for (std::size_t index = 0; index < snapshots.size(); ++index) {
        if (index > 0) {
            out << ",";
        }
        out << BuildLookupJson(snapshots[index]);
    }
    out << "]";
    return out.str();
}

}  // namespace cachecore
