#include "ffmpeg_player.h"

#include <cerrno>
#include <cstdint>
#include <string>

#ifdef HAVE_FFMPEG
extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/error.h>
#include <libavutil/mem.h>
}

namespace {
constexpr int kAvioBufferSize = 64 * 1024;

struct SourceIoContext {
    // 传给 FFmpeg 自定义 AVIO 回调的上下文桥接对象。
    IPlaySource* source = nullptr;
    std::string* error_message = nullptr;
};

std::string FfErrorToString(int ff_error) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(ff_error, buffer, sizeof(buffer));
    return std::string(buffer);
}

int ReadPacket(void* opaque, uint8_t* buf, int buf_size) {
    auto* source_context = static_cast<SourceIoContext*>(opaque);
    if (source_context == nullptr || source_context->source == nullptr || buf == nullptr || buf_size <= 0) {
        return AVERROR(EINVAL);
    }

    const int read_size = source_context->source->Read(buf, buf_size, source_context->error_message);
    if (read_size < 0) {
        if (source_context->error_message != nullptr && source_context->error_message->empty()) {
            *source_context->error_message = "source read failed";
        }
        return AVERROR(EIO);
    }
    // 自定义 IO 到达结尾时，需要返回 AVERROR_EOF 而不是 0。
    if (read_size == 0) {
        return AVERROR_EOF;
    }
    return read_size;
}

int64_t SeekPacket(void* opaque, int64_t offset, int whence) {
    auto* source_context = static_cast<SourceIoContext*>(opaque);
    if (source_context == nullptr || source_context->source == nullptr) {
        return AVERROR(EINVAL);
    }

    const int64_t position = source_context->source->Seek(offset, whence, source_context->error_message);
    if (position < 0) {
        if (source_context->error_message != nullptr && source_context->error_message->empty()) {
            *source_context->error_message = "source seek failed";
        }
        return AVERROR(EIO);
    }
    return position;
}

void CloseInputContext(AVFormatContext** format_context, AVIOContext** avio_context) {
    if (format_context != nullptr && *format_context != nullptr) {
        avformat_close_input(format_context);
    }

    if (avio_context != nullptr && *avio_context != nullptr) {
        auto* source_context = static_cast<SourceIoContext*>((*avio_context)->opaque);
        delete source_context;
        av_freep(&(*avio_context)->buffer);
        avio_context_free(avio_context);
    }
}

bool OpenInputFromSource(
        IPlaySource* source,
        std::string* error_message,
        AVFormatContext** out_format_context,
        AVIOContext** out_avio_context) {
    if (out_format_context == nullptr || out_avio_context == nullptr) {
        if (error_message != nullptr) {
            *error_message = "output context holder is null";
        }
        return false;
    }

    *out_format_context = nullptr;
    *out_avio_context = nullptr;

    if (source == nullptr) {
        if (error_message != nullptr) {
            *error_message = "play source is null";
        }
        return false;
    }

    AVFormatContext* format_context = avformat_alloc_context();
    if (format_context == nullptr) {
        if (error_message != nullptr) {
            *error_message = "avformat_alloc_context failed";
        }
        return false;
    }

    uint8_t* avio_buffer = static_cast<uint8_t*>(av_malloc(kAvioBufferSize));
    if (avio_buffer == nullptr) {
        avformat_free_context(format_context);
        if (error_message != nullptr) {
            *error_message = "av_malloc for avio buffer failed";
        }
        return false;
    }

    auto* source_context = new SourceIoContext();
    source_context->source = source;
    source_context->error_message = error_message;

    AVIOContext* avio_context = avio_alloc_context(
            avio_buffer,
            kAvioBufferSize,
            0,
            source_context,
            &ReadPacket,
            nullptr,
            &SeekPacket);
    if (avio_context == nullptr) {
        delete source_context;
        av_free(avio_buffer);
        avformat_free_context(format_context);
        if (error_message != nullptr) {
            *error_message = "avio_alloc_context failed";
        }
        return false;
    }

    // 显式声明可 seek，允许 FFmpeg 使用 seek 相关能力。
    avio_context->seekable = AVIO_SEEKABLE_NORMAL;

    format_context->pb = avio_context;
    // 输入来源于回调，不是 URL/文件路径。
    format_context->flags |= AVFMT_FLAG_CUSTOM_IO;

    const int open_result = avformat_open_input(&format_context, nullptr, nullptr, nullptr);
    if (open_result < 0) {
        if (error_message == nullptr || error_message->empty()) {
            if (error_message != nullptr) {
                *error_message = "avformat_open_input(source io) failed: " + FfErrorToString(open_result);
            }
        }
        CloseInputContext(&format_context, &avio_context);
        return false;
    }

    *out_format_context = format_context;
    *out_avio_context = avio_context;
    return true;
}
}  // namespace
#endif

int FfmpegPlayer::Play(
        IPlaySource* source,
        PcmConsumer* consumer,
        std::string* error_message) {
#ifndef HAVE_FFMPEG
    if (error_message != nullptr) {
        *error_message = "FFmpeg not found in third_party/FFmpeg-n6.1.4/out/<abi>/include or third_party/FFmpeg-n6.1.4/out-jniLibs/<abi>/libffmpeg.so.";
    }
    return -1000;
#else
    AVFormatContext* format_context = nullptr;
    AVIOContext* avio_context = nullptr;
    if (!OpenInputFromSource(source, error_message, &format_context, &avio_context)) {
        return -3;
    }

    const int result = decoder_.DecodeAndConsume(format_context, consumer, error_message);
    CloseInputContext(&format_context, &avio_context);
    return result;
#endif
}

int64_t FfmpegPlayer::GetDurationMs(
        IPlaySource* source,
        std::string* error_message) {
#ifndef HAVE_FFMPEG
    if (error_message != nullptr) {
        *error_message = "FFmpeg not found in third_party/FFmpeg-n6.1.4/out/<abi>/include or third_party/FFmpeg-n6.1.4/out-jniLibs/<abi>/libffmpeg.so.";
    }
    return -1000;
#else
    AVFormatContext* format_context = nullptr;
    AVIOContext* avio_context = nullptr;
    if (!OpenInputFromSource(source, error_message, &format_context, &avio_context)) {
        return -3;
    }

    const int64_t result = decoder_.GetDurationMs(format_context, error_message);
    CloseInputContext(&format_context, &avio_context);
    return result;
#endif
}

int FfmpegPlayer::GetAudioMetadata(
        IPlaySource* source,
        AudioMetadata* metadata,
        std::string* error_message) {
#ifndef HAVE_FFMPEG
    if (error_message != nullptr) {
        *error_message = "FFmpeg not found in third_party/FFmpeg-n6.1.4/out/<abi>/include or third_party/FFmpeg-n6.1.4/out-jniLibs/<abi>/libffmpeg.so.";
    }
    return -1000;
#else
    AVFormatContext* format_context = nullptr;
    AVIOContext* avio_context = nullptr;
    if (!OpenInputFromSource(source, error_message, &format_context, &avio_context)) {
        return -3;
    }

    const int result = decoder_.GetAudioMetadata(format_context, metadata, error_message);
    CloseInputContext(&format_context, &avio_context);
    return result;
#endif
}
