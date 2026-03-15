#pragma once

#include <cstdint>
#include <string>

struct AVFormatContext;

enum class PcmOutputEncoding {
    kPcm16,
    kPcmFloat,
};

struct PcmOutputConfig {
    int sample_rate = 0;
    int channels = 0;
    PcmOutputEncoding encoding = PcmOutputEncoding::kPcm16;
};

class PcmConsumer {
public:
    virtual ~PcmConsumer() = default;
    // 消费一段 PCM 数据，返回 false 表示消费端失败或主动停止。
    virtual bool Consume(const uint8_t* data, int size, std::string* error_message) = 0;
    // 配置输出 PCM 目标参数；实现可根据设备能力回退，并通过 applied 返回最终生效配置。
    virtual bool ConfigureOutput(
            const PcmOutputConfig& preferred,
            PcmOutputConfig* applied,
            std::string* error_message) = 0;
    // 解码循环轮询停止状态。
    virtual bool ShouldStop() const = 0;
    // 拉取一次性 seek 请求（毫秒），<0 表示当前无 seek 请求。
    virtual int64_t TakeSeekPositionMs() = 0;
    // 返回当前生效的播放倍速（以 0.1X 为单位的整数，例如 1.0X -> 10）。
    virtual int CurrentPlaybackSpeedTenths() const = 0;
    // 在解码成功结束后，让消费端有机会把已经写入但尚未播出的数据真正 drain 完。
    virtual bool FinalizePlayback(std::string* error_message) {
        return true;
    }
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
    // 返回码约定：
    // - 0 表示成功
    // - 本项目自定义业务错误码集中在 -200x 区间
    // - 其他负值通常透传 FFmpeg 原生错误码

    // 提取音频元信息（codec/sampleRate/channels/bitRate/duration）。
    int GetAudioMetadata(
            AVFormatContext* format_context,
            AudioMetadata* metadata,
            std::string* error_message) const;

    // 获取时长（毫秒）；时长不可用时返回负值错误码。
    int64_t GetDurationMs(AVFormatContext* format_context, std::string* error_message) const;

    // 主链路：解复用 -> 解码 -> 重采样 -> 交给 PcmConsumer。
    int DecodeAndConsume(
            AVFormatContext* format_context,
            PcmConsumer* consumer,
            std::string* error_message) const;
};
