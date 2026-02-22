#pragma once

#include <cstdint>
#include <string>

#include "i_play_source.h"
#include "ffmpeg_decoder.h"

class INativePlayer {
public:
    virtual ~INativePlayer() = default;

    virtual int Play(
            IPlaySource* source,
            PcmConsumer* consumer,
            std::string* error_message) = 0;

    virtual int64_t GetDurationMs(
            IPlaySource* source,
            std::string* error_message) = 0;

    virtual int GetAudioMetadata(
            IPlaySource* source,
            AudioMetadata* metadata,
            std::string* error_message) = 0;
};
