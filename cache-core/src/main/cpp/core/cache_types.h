#pragma once

#include <cstdint>
#include <vector>

namespace cachecore {

struct Range {
    int64_t start = 0;
    int64_t end = 0;

    bool IsValid() const {
        return start >= 0 && end > start;
    }

    int64_t Length() const {
        return end - start;
    }

    bool operator==(const Range& other) const {
        return start == other.start && end == other.end;
    }

    bool operator!=(const Range& other) const {
        return !(*this == other);
    }
};

struct Trunk {
    int64_t offset = 0;
    int64_t length = 0;
};

struct SessionCursor {
    int64_t offset = 0;
};

struct ReadRequest {
    int64_t offset = 0;
    int32_t size = 0;
    int32_t max_retries = 0;
};

struct ReadResult {
    std::vector<uint8_t> bytes;
    bool from_memory = false;
    bool from_disk = false;
    bool from_provider = false;
    bool cancelled = false;
};

}  // namespace cachecore
