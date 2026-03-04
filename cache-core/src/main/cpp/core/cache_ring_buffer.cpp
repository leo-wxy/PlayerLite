#include "cache_ring_buffer.h"

#include <algorithm>

namespace cachecore {

RingBufferWindow::RingBufferWindow(std::size_t capacity) {
    Reset(capacity);
}

void RingBufferWindow::Reset(std::size_t capacity) {
    capacity_ = capacity;
    data_.assign(capacity_, 0);
    has_window_ = false;
    window_start_ = 0;
    window_end_ = 0;
}

void RingBufferWindow::Clear() {
    has_window_ = false;
    window_start_ = 0;
    window_end_ = 0;
}

void RingBufferWindow::Release() {
    data_.clear();
    data_.shrink_to_fit();
    capacity_ = 0;
    has_window_ = false;
    window_start_ = 0;
    window_end_ = 0;
}

std::size_t RingBufferWindow::Capacity() const {
    return capacity_;
}

std::size_t RingBufferWindow::Size() const {
    if (!has_window_ || window_end_ <= window_start_) {
        return 0;
    }
    return static_cast<std::size_t>(window_end_ - window_start_);
}

int64_t RingBufferWindow::WindowStart() const {
    return has_window_ ? window_start_ : 0;
}

int64_t RingBufferWindow::WindowEnd() const {
    return has_window_ ? window_end_ : 0;
}

bool RingBufferWindow::Read(int64_t offset, int32_t size, std::vector<uint8_t>* out) const {
    if (out == nullptr || size < 0 || offset < 0) {
        return false;
    }
    if (size == 0) {
        out->clear();
        return true;
    }
    if (capacity_ == 0 || !has_window_) {
        return false;
    }
    const int64_t end = offset + static_cast<int64_t>(size);
    if (offset < window_start_ || end > window_end_) {
        return false;
    }

    out->resize(static_cast<std::size_t>(size));
    for (int32_t index = 0; index < size; ++index) {
        const int64_t absolute = offset + index;
        (*out)[static_cast<std::size_t>(index)] = data_[NormalizeIndex(absolute)];
    }
    return true;
}

void RingBufferWindow::Write(int64_t offset, const std::vector<uint8_t>& bytes) {
    if (capacity_ == 0 || bytes.empty() || offset < 0) {
        return;
    }

    std::size_t source_index = 0;
    int64_t write_start = offset;
    if (bytes.size() > capacity_) {
        source_index = bytes.size() - capacity_;
        write_start = offset + static_cast<int64_t>(source_index);
    }
    const int64_t write_end = offset + static_cast<int64_t>(bytes.size());

    if (!has_window_) {
        has_window_ = true;
        window_start_ = write_start;
        window_end_ = write_start;
    }

    if (write_end - window_start_ > static_cast<int64_t>(capacity_)) {
        window_start_ = write_end - static_cast<int64_t>(capacity_);
    }

    if (write_start < window_start_) {
        const int64_t drop = window_start_ - write_start;
        source_index += static_cast<std::size_t>(drop);
        write_start = window_start_;
    }

    if (write_end > window_end_) {
        window_end_ = write_end;
    }
    if (window_end_ - window_start_ > static_cast<int64_t>(capacity_)) {
        window_start_ = window_end_ - static_cast<int64_t>(capacity_);
    }

    for (std::size_t i = source_index; i < bytes.size(); ++i) {
        const int64_t absolute = offset + static_cast<int64_t>(i);
        if (absolute < window_start_ || absolute >= window_end_) {
            continue;
        }
        data_[NormalizeIndex(absolute)] = bytes[i];
    }
}

std::size_t RingBufferWindow::NormalizeIndex(int64_t absolute_offset) const {
    if (capacity_ == 0) {
        return 0;
    }
    const auto mod = absolute_offset % static_cast<int64_t>(capacity_);
    return static_cast<std::size_t>(mod >= 0 ? mod : mod + static_cast<int64_t>(capacity_));
}

}  // namespace cachecore
