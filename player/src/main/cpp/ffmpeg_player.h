#pragma once

#include "i_native_player.h"

class FfmpegPlayer final : public INativePlayer {
public:
    int Play(
            IPlaySource* source,
            PcmConsumer* consumer,
            std::string* error_message) override;

    int64_t GetDurationMs(
            IPlaySource* source,
            std::string* error_message) override;

    int GetAudioMetadata(
            IPlaySource* source,
            AudioMetadata* metadata,
            std::string* error_message) override;

private:
    FfmpegDecoder decoder_;
};
