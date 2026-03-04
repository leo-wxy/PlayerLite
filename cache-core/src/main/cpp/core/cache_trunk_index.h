#pragma once

#include <cstdint>
#include <unordered_set>
#include <vector>

#include "cache_types.h"

namespace cachecore {

class TrunkIndex {
public:
    void Clear();
    void SetRanges(const std::vector<Range>& ranges);
    bool AddTrunk(int64_t offset, int64_t length);

    bool Contains(int64_t offset, int32_t length) const;
    int64_t AvailableSize(int64_t offset) const;
    bool NextHole(int64_t offset, int64_t end, int64_t* hole_start, int64_t* hole_end) const;

    std::vector<Range> Ranges() const;
    std::unordered_set<int64_t> ToBlockSet(int32_t block_size_bytes) const;

    static std::vector<Range> BuildRangesFromBlocks(
            const std::unordered_set<int64_t>& blocks,
            int32_t block_size_bytes);

private:
    std::vector<Range> ranges_;
};

}  // namespace cachecore
