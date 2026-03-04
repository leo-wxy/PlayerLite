#include "cache_runtime.h"

#include <algorithm>
#include <cctype>
#include <sstream>

namespace cachecore {

std::optional<int64_t> CacheRuntime::ParseLongField(const std::string& json, const std::string& field) {
    const std::string key = "\"" + field + "\"";
    const auto key_pos = json.find(key);
    if (key_pos == std::string::npos) {
        return std::nullopt;
    }
    const auto colon_pos = json.find(':', key_pos + key.size());
    if (colon_pos == std::string::npos) {
        return std::nullopt;
    }

    std::size_t value_start = colon_pos + 1;
    while (value_start < json.size() &&
           std::isspace(static_cast<unsigned char>(json[value_start])) != 0) {
        ++value_start;
    }
    if (value_start >= json.size()) {
        return std::nullopt;
    }

    std::size_t value_end = value_start;
    if (json[value_end] == '-') {
        ++value_end;
    }
    const auto digits_start = value_end;
    while (value_end < json.size() &&
           std::isdigit(static_cast<unsigned char>(json[value_end])) != 0) {
        ++value_end;
    }
    if (value_end == digits_start) {
        return std::nullopt;
    }

    try {
        return std::stoll(json.substr(value_start, value_end - value_start));
    } catch (...) {
        return std::nullopt;
    }
}

std::unordered_set<int64_t> CacheRuntime::ParseLongSetField(const std::string& json, const std::string& field) {
    const std::string key = "\"" + field + "\"";
    const auto key_pos = json.find(key);
    if (key_pos == std::string::npos) {
        return {};
    }
    const auto colon_pos = json.find(':', key_pos + key.size());
    if (colon_pos == std::string::npos) {
        return {};
    }
    const auto array_start = json.find('[', colon_pos + 1);
    if (array_start == std::string::npos) {
        return {};
    }

    std::size_t array_end = std::string::npos;
    int depth = 0;
    for (std::size_t index = array_start; index < json.size(); ++index) {
        const char ch = json[index];
        if (ch == '[') {
            ++depth;
        } else if (ch == ']') {
            --depth;
            if (depth == 0) {
                array_end = index;
                break;
            }
        }
    }
    if (array_end == std::string::npos || array_end <= array_start) {
        return {};
    }

    std::unordered_set<int64_t> values;
    std::stringstream body(json.substr(array_start + 1, array_end - array_start - 1));
    std::string token;
    while (std::getline(body, token, ',')) {
        auto start = token.find_first_not_of(" \t\r\n");
        if (start == std::string::npos) {
            continue;
        }
        auto end = token.find_last_not_of(" \t\r\n");
        const auto trimmed = token.substr(start, end - start + 1);
        try {
            values.insert(std::stoll(trimmed));
        } catch (...) {
        }
    }
    return values;
}

std::vector<Range> CacheRuntime::ParseRangesField(const std::string& json, const std::string& field) {
    const std::string key = "\"" + field + "\"";
    const auto key_pos = json.find(key);
    if (key_pos == std::string::npos) {
        return {};
    }
    const auto colon_pos = json.find(':', key_pos + key.size());
    if (colon_pos == std::string::npos) {
        return {};
    }
    const auto array_start = json.find('[', colon_pos + 1);
    if (array_start == std::string::npos) {
        return {};
    }

    std::size_t array_end = std::string::npos;
    int depth = 0;
    for (std::size_t index = array_start; index < json.size(); ++index) {
        const char ch = json[index];
        if (ch == '[') {
            ++depth;
        } else if (ch == ']') {
            --depth;
            if (depth == 0) {
                array_end = index;
                break;
            }
        }
    }
    if (array_end == std::string::npos || array_end <= array_start) {
        return {};
    }

    const std::string body = json.substr(array_start + 1, array_end - array_start - 1);
    std::vector<Range> ranges;

    std::size_t search_start = 0;
    while (search_start < body.size()) {
        const auto object_start = body.find('{', search_start);
        if (object_start == std::string::npos) {
            break;
        }

        std::size_t object_end = std::string::npos;
        int object_depth = 0;
        for (std::size_t index = object_start; index < body.size(); ++index) {
            const char ch = body[index];
            if (ch == '{') {
                ++object_depth;
            } else if (ch == '}') {
                --object_depth;
                if (object_depth == 0) {
                    object_end = index;
                    break;
                }
            }
        }
        if (object_end == std::string::npos || object_end <= object_start) {
            break;
        }

        const auto object_json = body.substr(object_start, object_end - object_start + 1);
        const auto start = ParseLongField(object_json, "start");
        const auto end = ParseLongField(object_json, "end");
        if (start.has_value() && end.has_value() && start.value() >= 0 && end.value() > start.value()) {
            ranges.push_back(Range{start.value(), end.value()});
        }

        search_start = object_end + 1;
    }

    std::sort(ranges.begin(), ranges.end(), [](const Range& left, const Range& right) {
        if (left.start != right.start) {
            return left.start < right.start;
        }
        return left.end < right.end;
    });

    std::vector<Range> merged;
    for (const auto& range : ranges) {
        if (merged.empty() || merged.back().end < range.start) {
            merged.push_back(range);
            continue;
        }
        merged.back().end = std::max(merged.back().end, range.end);
    }
    return merged;
}

std::string CacheRuntime::EscapeJson(const std::string& raw) {
    std::string out;
    out.reserve(raw.size() + 16);
    for (char c : raw) {
        switch (c) {
            case '\\':
                out += "\\\\";
                break;
            case '"':
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

std::string CacheRuntime::BuildRangesJson(const std::vector<Range>& ranges) {
    std::ostringstream builder;
    for (std::size_t index = 0; index < ranges.size(); ++index) {
        if (index > 0) {
            builder << ", ";
        }
        builder << "{\"start\":" << ranges[index].start << ",\"end\":" << ranges[index].end << "}";
    }
    return builder.str();
}

std::string CacheRuntime::BuildConfigJson(
        const std::string& resource_key,
        int32_t block_size_bytes,
        int64_t content_length,
        int64_t duration_ms,
        const std::unordered_set<int64_t>& block_indexes,
        const std::vector<Range>& completed_ranges,
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

    std::vector<Range> sorted_ranges = completed_ranges;
    std::sort(sorted_ranges.begin(), sorted_ranges.end(), [](const Range& left, const Range& right) {
        if (left.start != right.start) {
            return left.start < right.start;
        }
        return left.end < right.end;
    });

    std::ostringstream json;
    json << "{\n";
    json << "  \"version\": 1,\n";
    json << "  \"resourceKey\": \"" << EscapeJson(resource_key) << "\",\n";
    json << "  \"contentLength\": " << content_length << ",\n";
    json << "  \"durationMs\": " << duration_ms << ",\n";
    json << "  \"blockSizeBytes\": " << block_size_bytes << ",\n";
    json << "  \"blocks\": [" << blocks_builder.str() << "],\n";
    json << "  \"completedRanges\": [" << BuildRangesJson(sorted_ranges) << "],\n";
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
