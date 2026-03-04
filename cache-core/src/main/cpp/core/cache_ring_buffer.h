#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace cachecore {

class RingBufferWindow {
public:
    explicit RingBufferWindow(std::size_t capacity = 0);

    void Reset(std::size_t capacity);
    void Clear();
    void Release();

    std::size_t Capacity() const;
    std::size_t Size() const;
    int64_t WindowStart() const;
    int64_t WindowEnd() const;

    bool Read(int64_t offset, int32_t size, std::vector<uint8_t>* out) const;
    void Write(int64_t offset, const std::vector<uint8_t>& bytes);

private:
    std::size_t NormalizeIndex(int64_t absolute_offset) const;

    std::vector<uint8_t> data_;
    std::size_t capacity_ = 0;
    bool has_window_ = false;
    int64_t window_start_ = 0;
    int64_t window_end_ = 0;
};

}  // namespace cachecore
