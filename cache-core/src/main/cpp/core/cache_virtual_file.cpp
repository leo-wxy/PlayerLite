#include "cache_virtual_file.h"

#include <algorithm>
#include <fstream>

namespace cachecore {

VirtualFile::VirtualFile(std::filesystem::path data_file, int32_t block_size_bytes) {
    Reset(std::move(data_file), block_size_bytes);
}

void VirtualFile::Reset(std::filesystem::path data_file, int32_t block_size_bytes) {
    data_file_ = std::move(data_file);
    block_size_bytes_ = block_size_bytes > 0 ? block_size_bytes : 64 * 1024;
    trunk_index_.Clear();
}

bool VirtualFile::EnsureCreated() const {
    if (data_file_.empty()) {
        return false;
    }
    std::error_code ec;
    if (std::filesystem::exists(data_file_, ec)) {
        return !ec;
    }
    std::ofstream output(data_file_, std::ios::binary | std::ios::out);
    output.close();
    return output.good() || std::filesystem::exists(data_file_);
}

std::vector<uint8_t> VirtualFile::Read(int64_t offset, int32_t size) const {
    if (offset < 0 || size <= 0 || !trunk_index_.Contains(offset, size)) {
        return {};
    }
    if (!std::filesystem::exists(data_file_)) {
        return {};
    }

    std::ifstream input(data_file_, std::ios::binary);
    if (!input.good()) {
        return {};
    }

    input.seekg(0, std::ios::end);
    const auto file_size = static_cast<int64_t>(input.tellg());
    if (offset >= file_size) {
        return {};
    }

    const auto readable = static_cast<int32_t>(std::min<int64_t>(size, file_size - offset));
    if (readable <= 0) {
        return {};
    }

    std::vector<uint8_t> bytes(static_cast<std::size_t>(readable));
    input.seekg(offset, std::ios::beg);
    input.read(reinterpret_cast<char*>(bytes.data()), readable);
    if (!input.good() && !input.eof()) {
        return {};
    }
    return bytes;
}

bool VirtualFile::Write(int64_t offset, const std::vector<uint8_t>& bytes) {
    if (offset < 0 || bytes.empty()) {
        return false;
    }
    if (!EnsureCreated()) {
        return false;
    }

    std::fstream io(data_file_, std::ios::binary | std::ios::in | std::ios::out);
    if (!io.good()) {
        io.clear();
        io.open(data_file_, std::ios::binary | std::ios::out);
        io.close();
        io.open(data_file_, std::ios::binary | std::ios::in | std::ios::out);
        if (!io.good()) {
            return false;
        }
    }

    io.seekp(offset, std::ios::beg);
    io.write(reinterpret_cast<const char*>(bytes.data()), static_cast<std::streamsize>(bytes.size()));
    io.flush();
    if (!io.good()) {
        return false;
    }

    trunk_index_.AddTrunk(offset, static_cast<int64_t>(bytes.size()));
    return true;
}

int64_t VirtualFile::SizeBytes() const {
    std::error_code ec;
    if (!std::filesystem::exists(data_file_, ec) || ec) {
        return 0;
    }
    return static_cast<int64_t>(std::filesystem::file_size(data_file_, ec));
}

int32_t VirtualFile::BlockSizeBytes() const {
    return block_size_bytes_;
}

const std::filesystem::path& VirtualFile::DataFile() const {
    return data_file_;
}

TrunkIndex& VirtualFile::MutableTrunkIndex() {
    return trunk_index_;
}

const TrunkIndex& VirtualFile::GetTrunkIndex() const {
    return trunk_index_;
}

}  // namespace cachecore
