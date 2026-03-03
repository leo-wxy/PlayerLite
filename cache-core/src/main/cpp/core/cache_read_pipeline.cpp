#include "cache_runtime.h"

#include <algorithm>
#include <fstream>

namespace cachecore {

std::vector<uint8_t> CacheRuntime::ReadAtLocked(SessionState* session, int64_t offset, int32_t size) {
    if (session == nullptr || session->closed || size <= 0 || offset < 0) {
        return {};
    }

    std::vector<uint8_t> output(static_cast<std::size_t>(size));
    int32_t written = 0;
    int64_t current = offset;
    int32_t remaining = size;

    while (remaining > 0) {
        if (session->storage.content_length > 0 && current >= session->storage.content_length) {
            break;
        }

        const int64_t block_index = current / session->storage.block_size_bytes;
        const int32_t in_block_offset = static_cast<int32_t>(current % session->storage.block_size_bytes);
        std::vector<uint8_t> block;
        if (!LoadOrFetchBlockLocked(session, block_index, &block) || block.empty()) {
            break;
        }
        if (in_block_offset >= static_cast<int32_t>(block.size())) {
            break;
        }

        const int32_t can_copy = std::min<int32_t>(
                remaining,
                static_cast<int32_t>(block.size()) - in_block_offset);
        if (can_copy <= 0) {
            break;
        }
        std::copy(
                block.begin() + in_block_offset,
                block.begin() + in_block_offset + can_copy,
                output.begin() + written);

        written += can_copy;
        remaining -= can_copy;
        current += can_copy;
    }

    output.resize(static_cast<std::size_t>(written));
    return output;
}

bool CacheRuntime::LoadOrFetchBlockLocked(
        SessionState* session,
        int64_t block_index,
        std::vector<uint8_t>* out_block) {
    if (session == nullptr || out_block == nullptr || block_index < 0) {
        return false;
    }

    auto memory_it = session->memory_blocks.find(block_index);
    if (memory_it != session->memory_blocks.end()) {
        TouchMemoryBlockLocked(session->session_id, block_index);
        *out_block = memory_it->second;
        return true;
    }

    if (session->storage.cached_blocks.find(block_index) != session->storage.cached_blocks.end()) {
        auto disk_block = ReadBlockFromDiskLocked(*session, block_index);
        if (!disk_block.empty()) {
            InsertMemoryBlockLocked(session->session_id, block_index, disk_block);
            *out_block = std::move(disk_block);
            return true;
        }
        session->storage.cached_blocks.erase(block_index);
        PersistConfigLocked(*session);
    }

    if (provider_bridge_ == nullptr || session->provider_handle <= 0) {
        return false;
    }
    auto fetched = provider_bridge_->ReadAt(
            session->provider_handle,
            block_index * static_cast<int64_t>(session->storage.block_size_bytes),
            session->storage.block_size_bytes);
    if (fetched.empty()) {
        return false;
    }

    if (!WriteBlockToDiskLocked(*session, block_index, fetched)) {
        return false;
    }
    session->storage.cached_blocks.insert(block_index);
    InsertMemoryBlockLocked(session->session_id, block_index, fetched);

    if (session->storage.content_length < 0) {
        const auto queried = provider_bridge_->QueryContentLength(session->provider_handle);
        if (queried >= 0) {
            session->storage.content_length = queried;
        }
    }
    PersistConfigLocked(*session);

    *out_block = std::move(fetched);
    return true;
}

std::vector<uint8_t> CacheRuntime::ReadBlockFromDiskLocked(const SessionState& session, int64_t block_index) {
    if (!std::filesystem::exists(session.storage.data_file) || block_index < 0) {
        return {};
    }
    std::ifstream input(session.storage.data_file, std::ios::binary);
    if (!input.good()) {
        return {};
    }

    input.seekg(0, std::ios::end);
    const auto file_size = static_cast<int64_t>(input.tellg());
    const int64_t block_start = block_index * static_cast<int64_t>(session.storage.block_size_bytes);
    if (block_start >= file_size) {
        return {};
    }

    const int64_t remaining = file_size - block_start;
    const int32_t readable = static_cast<int32_t>(std::min<int64_t>(remaining, session.storage.block_size_bytes));
    if (readable <= 0) {
        return {};
    }

    std::vector<uint8_t> block(static_cast<std::size_t>(readable));
    input.seekg(block_start, std::ios::beg);
    input.read(reinterpret_cast<char*>(block.data()), readable);
    if (!input.good() && !input.eof()) {
        return {};
    }
    return block;
}

bool CacheRuntime::WriteBlockToDiskLocked(
        const SessionState& session,
        int64_t block_index,
        const std::vector<uint8_t>& bytes) {
    if (bytes.empty() || block_index < 0) {
        return false;
    }
    std::fstream io(
            session.storage.data_file,
            std::ios::binary | std::ios::in | std::ios::out);
    if (!io.good()) {
        io.clear();
        io.open(session.storage.data_file, std::ios::binary | std::ios::out);
        io.close();
        io.open(session.storage.data_file, std::ios::binary | std::ios::in | std::ios::out);
        if (!io.good()) {
            return false;
        }
    }

    const int64_t block_start = block_index * static_cast<int64_t>(session.storage.block_size_bytes);
    io.seekp(block_start, std::ios::beg);
    io.write(reinterpret_cast<const char*>(bytes.data()), static_cast<std::streamsize>(bytes.size()));
    io.flush();
    return io.good();
}

}  // namespace cachecore

