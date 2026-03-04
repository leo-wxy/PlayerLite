#pragma once

#include <cstdint>
#include <filesystem>
#include <vector>

#include "cache_trunk_index.h"

namespace cachecore {

class VirtualFile {
public:
    VirtualFile() = default;
    VirtualFile(std::filesystem::path data_file, int32_t block_size_bytes);

    void Reset(std::filesystem::path data_file, int32_t block_size_bytes);

    bool EnsureCreated() const;
    std::vector<uint8_t> Read(int64_t offset, int32_t size) const;
    bool Write(int64_t offset, const std::vector<uint8_t>& bytes);

    int64_t SizeBytes() const;

    int32_t BlockSizeBytes() const;
    const std::filesystem::path& DataFile() const;

    TrunkIndex& MutableTrunkIndex();
    const TrunkIndex& GetTrunkIndex() const;

private:
    std::filesystem::path data_file_;
    int32_t block_size_bytes_ = 64 * 1024;
    TrunkIndex trunk_index_;
};

}  // namespace cachecore
