#pragma once

#include <cstdint>
#include <string>

struct AVFormatContext;

class PcmConsumer {
public:
    virtual ~PcmConsumer() = default;
    virtual bool Consume(const uint8_t* data, int size, std::string* error_message) = 0;
    virtual bool ShouldStop() const = 0;
    virtual int64_t TakeSeekPositionMs() = 0;
};

struct AudioMetadata {
    std::string codec;
    int sample_rate = 0;
    int channels = 0;
    int64_t bit_rate = 0;
    int64_t duration_ms = 0;
};

class FfmpegDecoder {
public:
    int GetAudioMetadata(
            AVFormatContext* format_context,
            AudioMetadata* metadata,
            std::string* error_message) const;

    int64_t GetDurationMs(AVFormatContext* format_context, std::string* error_message) const;

    int DecodeAndConsume(
            AVFormatContext* format_context,
            PcmConsumer* consumer,
            std::string* error_message) const;
};
