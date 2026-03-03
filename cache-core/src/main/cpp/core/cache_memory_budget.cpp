#include "cache_runtime.h"

namespace cachecore {

void CacheRuntime::TouchMemoryBlockLocked(int64_t session_id, int64_t block_index) {
    const MemoryKey key{session_id, block_index};
    auto found = memory_lru_index_.find(key);
    if (found == memory_lru_index_.end()) {
        return;
    }
    memory_lru_order_.erase(found->second);
    memory_lru_order_.push_front(key);
    found->second = memory_lru_order_.begin();
}

void CacheRuntime::InsertMemoryBlockLocked(int64_t session_id, int64_t block_index, const std::vector<uint8_t>& bytes) {
    auto session_it = sessions_.find(session_id);
    if (session_it == sessions_.end() || session_it->second == nullptr) {
        return;
    }
    auto& session = *session_it->second;

    auto existing = session.memory_blocks.find(block_index);
    if (existing != session.memory_blocks.end()) {
        current_memory_bytes_ -= static_cast<int64_t>(existing->second.size());
    }
    session.memory_blocks[block_index] = bytes;
    current_memory_bytes_ += static_cast<int64_t>(bytes.size());

    const MemoryKey key{session_id, block_index};
    auto index_it = memory_lru_index_.find(key);
    if (index_it != memory_lru_index_.end()) {
        memory_lru_order_.erase(index_it->second);
    }
    memory_lru_order_.push_front(key);
    memory_lru_index_[key] = memory_lru_order_.begin();

    EvictMemoryIfNeededLocked();
}

void CacheRuntime::RemoveMemoryBlockLocked(int64_t session_id, int64_t block_index) {
    auto session_it = sessions_.find(session_id);
    if (session_it == sessions_.end() || session_it->second == nullptr) {
        return;
    }
    auto& session = *session_it->second;
    auto memory_it = session.memory_blocks.find(block_index);
    if (memory_it != session.memory_blocks.end()) {
        current_memory_bytes_ -= static_cast<int64_t>(memory_it->second.size());
        session.memory_blocks.erase(memory_it);
    }

    const MemoryKey key{session_id, block_index};
    auto index_it = memory_lru_index_.find(key);
    if (index_it != memory_lru_index_.end()) {
        memory_lru_order_.erase(index_it->second);
        memory_lru_index_.erase(index_it);
    }
}

void CacheRuntime::EvictMemoryIfNeededLocked() {
    if (memory_cache_cap_bytes_ <= 0) {
        return;
    }
    while (current_memory_bytes_ > memory_cache_cap_bytes_ && !memory_lru_order_.empty()) {
        const MemoryKey key = memory_lru_order_.back();
        memory_lru_order_.pop_back();
        memory_lru_index_.erase(key);

        auto session_it = sessions_.find(key.session_id);
        if (session_it == sessions_.end() || session_it->second == nullptr) {
            continue;
        }
        auto& session = *session_it->second;
        auto memory_it = session.memory_blocks.find(key.block_index);
        if (memory_it == session.memory_blocks.end()) {
            continue;
        }

        current_memory_bytes_ -= static_cast<int64_t>(memory_it->second.size());
        session.memory_blocks.erase(memory_it);
    }
    if (current_memory_bytes_ < 0) {
        current_memory_bytes_ = 0;
    }
}

void CacheRuntime::RemoveSessionMemoryLocked(const SessionState& session) {
    for (const auto& block : session.memory_blocks) {
        const MemoryKey key{session.session_id, block.first};
        auto index_it = memory_lru_index_.find(key);
        if (index_it != memory_lru_index_.end()) {
            memory_lru_order_.erase(index_it->second);
            memory_lru_index_.erase(index_it);
        }
        current_memory_bytes_ -= static_cast<int64_t>(block.second.size());
    }
    if (current_memory_bytes_ < 0) {
        current_memory_bytes_ = 0;
    }
}

}  // namespace cachecore

