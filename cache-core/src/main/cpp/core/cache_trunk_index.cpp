#include "cache_trunk_index.h"

#include <algorithm>

namespace cachecore {

void TrunkIndex::Clear() {
    ranges_.clear();
}

void TrunkIndex::SetRanges(const std::vector<Range>& ranges) {
    ranges_.clear();
    for (const auto& range : ranges) {
        if (!range.IsValid()) {
            continue;
        }
        AddTrunk(range.start, range.Length());
    }
}

bool TrunkIndex::AddTrunk(int64_t offset, int64_t length) {
    if (offset < 0 || length <= 0) {
        return false;
    }

    Range incoming{offset, offset + length};
    std::vector<Range> merged;
    merged.reserve(ranges_.size() + 1);

    bool inserted = false;
    for (const auto& current : ranges_) {
        if (current.end < incoming.start) {
            merged.push_back(current);
            continue;
        }
        if (incoming.end < current.start) {
            if (!inserted) {
                merged.push_back(incoming);
                inserted = true;
            }
            merged.push_back(current);
            continue;
        }

        incoming.start = std::min(incoming.start, current.start);
        incoming.end = std::max(incoming.end, current.end);
    }

    if (!inserted) {
        merged.push_back(incoming);
    }

    const bool changed = merged != ranges_;
    ranges_ = std::move(merged);
    return changed;
}

bool TrunkIndex::Contains(int64_t offset, int32_t length) const {
    if (offset < 0 || length < 0) {
        return false;
    }
    if (length == 0) {
        return true;
    }
    const int64_t end = offset + static_cast<int64_t>(length);
    for (const auto& range : ranges_) {
        if (range.start <= offset && range.end >= end) {
            return true;
        }
        if (range.start > offset) {
            break;
        }
    }
    return false;
}

int64_t TrunkIndex::AvailableSize(int64_t offset) const {
    if (offset < 0) {
        return 0;
    }
    for (const auto& range : ranges_) {
        if (range.start <= offset && range.end > offset) {
            return range.end - offset;
        }
        if (range.start > offset) {
            return 0;
        }
    }
    return 0;
}

bool TrunkIndex::NextHole(int64_t offset, int64_t end, int64_t* hole_start, int64_t* hole_end) const {
    if (hole_start == nullptr || hole_end == nullptr || offset < 0 || end <= offset) {
        return false;
    }

    int64_t cursor = offset;
    for (const auto& range : ranges_) {
        if (range.end <= cursor) {
            continue;
        }
        if (range.start > cursor) {
            *hole_start = cursor;
            *hole_end = std::min(end, range.start);
            return *hole_end > *hole_start;
        }
        cursor = std::max(cursor, range.end);
        if (cursor >= end) {
            return false;
        }
    }

    *hole_start = cursor;
    *hole_end = end;
    return *hole_end > *hole_start;
}

std::vector<Range> TrunkIndex::Ranges() const {
    return ranges_;
}

std::unordered_set<int64_t> TrunkIndex::ToBlockSet(int32_t block_size_bytes) const {
    std::unordered_set<int64_t> blocks;
    if (block_size_bytes <= 0) {
        return blocks;
    }
    for (const auto& range : ranges_) {
        if (!range.IsValid()) {
            continue;
        }
        const int64_t first = range.start / block_size_bytes;
        const int64_t last = (range.end - 1) / block_size_bytes;
        for (int64_t block = first; block <= last; ++block) {
            blocks.insert(block);
        }
    }
    return blocks;
}

std::vector<Range> TrunkIndex::BuildRangesFromBlocks(
        const std::unordered_set<int64_t>& blocks,
        int32_t block_size_bytes) {
    std::vector<int64_t> sorted_blocks(blocks.begin(), blocks.end());
    sorted_blocks.erase(
            std::remove_if(sorted_blocks.begin(), sorted_blocks.end(), [](int64_t value) {
                return value < 0;
            }),
            sorted_blocks.end());
    std::sort(sorted_blocks.begin(), sorted_blocks.end());

    std::vector<Range> ranges;
    if (sorted_blocks.empty() || block_size_bytes <= 0) {
        return ranges;
    }

    int64_t start_block = sorted_blocks.front();
    int64_t prev_block = start_block;
    for (std::size_t i = 1; i < sorted_blocks.size(); ++i) {
        const int64_t value = sorted_blocks[i];
        if (value == prev_block + 1) {
            prev_block = value;
            continue;
        }
        ranges.push_back(Range{
                start_block * block_size_bytes,
                (prev_block + 1) * block_size_bytes});
        start_block = value;
        prev_block = value;
    }

    ranges.push_back(Range{
            start_block * block_size_bytes,
            (prev_block + 1) * block_size_bytes});
    return ranges;
}

}  // namespace cachecore
