#pragma once

#include <cstdint>
#include <string>

class IPlaySource {
public:
    virtual ~IPlaySource() = default;
    virtual int Read(uint8_t* buffer, int size, std::string* error_message) = 0;
    virtual int64_t Seek(int64_t offset, int whence, std::string* error_message) = 0;
};
