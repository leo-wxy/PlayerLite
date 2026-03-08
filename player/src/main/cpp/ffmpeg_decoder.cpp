#include "ffmpeg_decoder.h"

#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <limits>
#include <string>

namespace {
constexpr int kDurationUnavailableCode = -2003;
}

#ifdef HAVE_FFMPEG
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libswresample/swresample.h>
}

namespace {
constexpr int kFallbackOutputSampleRate = 44100;
constexpr AVSampleFormat kFallbackOutputSampleFormat = AV_SAMPLE_FMT_S16;
constexpr int kFallbackOutputChannels = 2;

// 本文件约定的业务返回码（其余负值通常为 FFmpeg 原生错误码）：
//   0      : 成功
//  -2001   : 外部请求停止（stop）
//  -2002   : PCM 消费失败（consumer->Consume 返回 false）
//  -2003   : 时长不可用（GetDurationMs）
//  -2004   : 音频流信息缺失（如 stream/codecpar 为空）
//   1      : 内部标记，表示 seek 已处理（不对外暴露）
constexpr int kStoppedCode = -2001;
constexpr int kConsumeFailedCode = -2002;
constexpr int kOutputInitFailedCode = -3001;
constexpr int kSeekHandledCode = 1;

// 解码主链路：
// av_read_frame(解复用) -> avcodec_send/receive(解码) -> swr_convert(重采样)
// -> PcmConsumer::Consume(输出到 AudioTrack 或其他消费端)。

std::string FfErrorToString(int ff_error) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(ff_error, buffer, sizeof(buffer));
    return std::string(buffer);
}

AVSampleFormat ResolveOutputSampleFormat(const PcmOutputConfig& output_config) {
    return output_config.encoding == PcmOutputEncoding::kPcmFloat
            ? AV_SAMPLE_FMT_FLT
            : AV_SAMPLE_FMT_S16;
}

PcmOutputConfig BuildPreferredOutputConfig(const AVCodecContext* codec_context) {
    PcmOutputConfig config;
    config.sample_rate = codec_context->sample_rate > 0
            ? codec_context->sample_rate
            : kFallbackOutputSampleRate;
    config.channels = codec_context->ch_layout.nb_channels > 0
            ? codec_context->ch_layout.nb_channels
            : kFallbackOutputChannels;
    // Prefer PCM16 by default for broader device/emulator compatibility.
    config.encoding = PcmOutputEncoding::kPcm16;

    return config;
}

bool NeedsResample(
        const AVChannelLayout* input_layout,
        int input_sample_rate,
        AVSampleFormat input_sample_format,
        const AVChannelLayout& output_layout,
        int output_sample_rate,
        AVSampleFormat output_sample_format) {
    const bool same_sample_rate = input_sample_rate == output_sample_rate;
    const bool same_layout =
            input_layout != nullptr &&
            av_channel_layout_compare(input_layout, &output_layout) == 0;
    const bool same_sample_format = input_sample_format == output_sample_format;
    return !(same_sample_rate && same_layout && same_sample_format);
}

int NormalizePlaybackSpeedTenths(int speed_tenths) {
    return std::max(5, std::min(20, speed_tenths));
}

class AudioTempoProcessor {
public:
    ~AudioTempoProcessor() {
        Reset();
    }

    int ProcessInterleaved(
            const uint8_t* data,
            int nb_samples,
            const AVChannelLayout& channel_layout,
            AVSampleFormat sample_format,
            int sample_rate,
            int channels,
            PcmConsumer* consumer,
            std::string* error_message) {
        if (data == nullptr || nb_samples <= 0 || consumer == nullptr) {
            return 0;
        }

        const int speed_tenths = NormalizePlaybackSpeedTenths(
                consumer->CurrentPlaybackSpeedTenths());
        int result = EnsureGraph(
                channel_layout,
                sample_format,
                sample_rate,
                channels,
                speed_tenths,
                error_message);
        if (result < 0) {
            return result;
        }

        if (current_speed_tenths_ != speed_tenths) {
            result = UpdateTempo(speed_tenths, error_message);
            if (result < 0) {
                return result;
            }
        }

        AVFrame* frame = av_frame_alloc();
        if (frame == nullptr) {
            return AVERROR(ENOMEM);
        }

        frame->format = sample_format;
        frame->sample_rate = sample_rate;
        frame->nb_samples = nb_samples;
        result = av_channel_layout_copy(&frame->ch_layout, &channel_layout);
        if (result < 0) {
            av_frame_free(&frame);
            return result;
        }

        result = av_frame_get_buffer(frame, 0);
        if (result < 0) {
            av_frame_free(&frame);
            return result;
        }

        const int buffer_size = av_samples_get_buffer_size(
                nullptr,
                channels,
                nb_samples,
                sample_format,
                1);
        if (buffer_size < 0) {
            av_frame_free(&frame);
            return buffer_size;
        }
        memcpy(frame->data[0], data, buffer_size);

        result = av_buffersrc_add_frame_flags(buffer_src_ctx_, frame, 0);
        av_frame_free(&frame);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "buffersrc add frame failed: " + FfErrorToString(result);
            }
            return result;
        }

        return Drain(consumer, error_message);
    }

    int Flush(PcmConsumer* consumer, std::string* error_message) {
        if (buffer_src_ctx_ == nullptr || buffer_sink_ctx_ == nullptr || consumer == nullptr) {
            return 0;
        }

        int result = av_buffersrc_add_frame_flags(buffer_src_ctx_, nullptr, 0);
        if (result < 0 && result != AVERROR_EOF) {
            if (error_message != nullptr) {
                *error_message = "tempo flush failed: " + FfErrorToString(result);
            }
            return result;
        }
        return Drain(consumer, error_message);
    }

    void Reset() {
        if (graph_ != nullptr) {
            avfilter_graph_free(&graph_);
            graph_ = nullptr;
        }
        buffer_src_ctx_ = nullptr;
        atempo_ctx_ = nullptr;
        buffer_sink_ctx_ = nullptr;
        current_sample_format_ = AV_SAMPLE_FMT_NONE;
        current_sample_rate_ = 0;
        current_channels_ = 0;
        current_speed_tenths_ = 10;
        av_channel_layout_uninit(&current_channel_layout_);
    }

private:
    int EnsureGraph(
            const AVChannelLayout& channel_layout,
            AVSampleFormat sample_format,
            int sample_rate,
            int channels,
            int speed_tenths,
            std::string* error_message) {
        const bool same_layout =
                graph_ != nullptr &&
                av_channel_layout_compare(&current_channel_layout_, &channel_layout) == 0;
        const bool same_config =
                same_layout &&
                current_sample_format_ == sample_format &&
                current_sample_rate_ == sample_rate &&
                current_channels_ == channels;
        if (same_config) {
            return 0;
        }

        Reset();

        graph_ = avfilter_graph_alloc();
        if (graph_ == nullptr) {
            return AVERROR(ENOMEM);
        }

        const AVFilter* abuffer = avfilter_get_by_name("abuffer");
        const AVFilter* atempo = avfilter_get_by_name("atempo");
        const AVFilter* abuffersink = avfilter_get_by_name("abuffersink");
        if (abuffer == nullptr || atempo == nullptr || abuffersink == nullptr) {
            if (error_message != nullptr) {
                *error_message = "required audio tempo filters unavailable";
            }
            return AVERROR_FILTER_NOT_FOUND;
        }

        buffer_src_ctx_ = avfilter_graph_alloc_filter(graph_, abuffer, "src");
        atempo_ctx_ = avfilter_graph_alloc_filter(graph_, atempo, "tempo");
        buffer_sink_ctx_ = avfilter_graph_alloc_filter(graph_, abuffersink, "sink");
        if (buffer_src_ctx_ == nullptr || atempo_ctx_ == nullptr || buffer_sink_ctx_ == nullptr) {
            if (error_message != nullptr) {
                *error_message = "failed to allocate tempo filter contexts";
            }
            return AVERROR(ENOMEM);
        }

        char layout_desc[64] = {0};
        av_channel_layout_describe(&channel_layout, layout_desc, sizeof(layout_desc));
        av_opt_set(buffer_src_ctx_, "channel_layout", layout_desc, AV_OPT_SEARCH_CHILDREN);
        av_opt_set(buffer_src_ctx_, "sample_fmt", av_get_sample_fmt_name(sample_format), AV_OPT_SEARCH_CHILDREN);
        av_opt_set_q(buffer_src_ctx_, "time_base", AVRational{1, sample_rate}, AV_OPT_SEARCH_CHILDREN);
        av_opt_set_int(buffer_src_ctx_, "sample_rate", sample_rate, AV_OPT_SEARCH_CHILDREN);

        int result = avfilter_init_str(buffer_src_ctx_, nullptr);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "init abuffer failed: " + FfErrorToString(result);
            }
            return result;
        }

        char tempo_value[16] = {0};
        snprintf(tempo_value, sizeof(tempo_value), "%.1f", speed_tenths / 10.0);
        av_opt_set(atempo_ctx_, "tempo", tempo_value, AV_OPT_SEARCH_CHILDREN);
        result = avfilter_init_str(atempo_ctx_, nullptr);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "init atempo failed: " + FfErrorToString(result);
            }
            return result;
        }

        result = avfilter_init_str(buffer_sink_ctx_, nullptr);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "init abuffersink failed: " + FfErrorToString(result);
            }
            return result;
        }

        result = avfilter_link(buffer_src_ctx_, 0, atempo_ctx_, 0);
        if (result >= 0) {
            result = avfilter_link(atempo_ctx_, 0, buffer_sink_ctx_, 0);
        }
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "link tempo graph failed: " + FfErrorToString(result);
            }
            return result;
        }

        result = avfilter_graph_config(graph_, nullptr);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "configure tempo graph failed: " + FfErrorToString(result);
            }
            return result;
        }

        result = av_channel_layout_copy(&current_channel_layout_, &channel_layout);
        if (result < 0) {
            return result;
        }
        current_sample_format_ = sample_format;
        current_sample_rate_ = sample_rate;
        current_channels_ = channels;
        current_speed_tenths_ = speed_tenths;
        return 0;
    }

    int UpdateTempo(int speed_tenths, std::string* error_message) {
        if (atempo_ctx_ == nullptr) {
            return AVERROR(EINVAL);
        }

        char tempo_value[16] = {0};
        snprintf(tempo_value, sizeof(tempo_value), "%.1f", speed_tenths / 10.0);
        const int result = avfilter_process_command(
                atempo_ctx_,
                "tempo",
                tempo_value,
                nullptr,
                0,
                0);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "update atempo failed: " + FfErrorToString(result);
            }
            return result;
        }
        current_speed_tenths_ = speed_tenths;
        return 0;
    }

    int Drain(PcmConsumer* consumer, std::string* error_message) {
        AVFrame* filtered_frame = av_frame_alloc();
        if (filtered_frame == nullptr) {
            return AVERROR(ENOMEM);
        }

        int result = 0;
        while ((result = av_buffersink_get_frame(buffer_sink_ctx_, filtered_frame)) >= 0) {
            const int output_size = av_samples_get_buffer_size(
                    nullptr,
                    filtered_frame->ch_layout.nb_channels,
                    filtered_frame->nb_samples,
                    static_cast<AVSampleFormat>(filtered_frame->format),
                    1);
            if (output_size < 0) {
                av_frame_unref(filtered_frame);
                av_frame_free(&filtered_frame);
                return output_size;
            }

            const bool consumed_ok = consumer->Consume(
                    filtered_frame->data[0],
                    output_size,
                    error_message);
            av_frame_unref(filtered_frame);
            if (!consumed_ok) {
                av_frame_free(&filtered_frame);
                return kConsumeFailedCode;
            }
        }

        av_frame_free(&filtered_frame);
        if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) {
            return 0;
        }
        if (result < 0 && error_message != nullptr) {
            *error_message = "drain tempo graph failed: " + FfErrorToString(result);
        }
        return result;
    }

    AVFilterGraph* graph_ = nullptr;
    AVFilterContext* buffer_src_ctx_ = nullptr;
    AVFilterContext* atempo_ctx_ = nullptr;
    AVFilterContext* buffer_sink_ctx_ = nullptr;
    AVChannelLayout current_channel_layout_{};
    AVSampleFormat current_sample_format_ = AV_SAMPLE_FMT_NONE;
    int current_sample_rate_ = 0;
    int current_channels_ = 0;
    int current_speed_tenths_ = 10;
};

int WriteResampledFrame(
        SwrContext* swr,
        AVFrame* decoded_frame,
        int input_sample_rate,
        const AVChannelLayout& output_layout,
        int output_sample_rate,
        AVSampleFormat output_sample_format,
        int output_channels,
        AudioTempoProcessor* tempo_processor,
        PcmConsumer* consumer,
        std::string* error_message) {
    // swr 可能仍有历史延迟样本，目标采样点数需要把 delay 一起计算进来。
    const int64_t delayed = swr_get_delay(swr, input_sample_rate);
    const int dst_nb_samples = static_cast<int>(av_rescale_rnd(
            delayed + decoded_frame->nb_samples,
            output_sample_rate,
            input_sample_rate,
            AV_ROUND_UP));

    // 每帧按需申请输出缓冲，写完立刻释放，避免跨帧状态污染。
    uint8_t** output_data = nullptr;
    int output_line_size = 0;
    int result = av_samples_alloc_array_and_samples(
            &output_data,
            &output_line_size,
            output_channels,
            dst_nb_samples,
            output_sample_format,
            0);
    if (result < 0) {
        return result;
    }

    const int converted = swr_convert(
            swr,
            output_data,
            dst_nb_samples,
            const_cast<const uint8_t**>(decoded_frame->extended_data),
            decoded_frame->nb_samples);
    if (converted < 0) {
        av_freep(&output_data[0]);
        av_freep(&output_data);
        return converted;
    }

    int result_to_return = 0;
    if (tempo_processor != nullptr && consumer != nullptr) {
        result_to_return = tempo_processor->ProcessInterleaved(
                output_data[0],
                converted,
                output_layout,
                output_sample_format,
                output_sample_rate,
                output_channels,
                consumer,
                error_message);
    }

    av_freep(&output_data[0]);
    av_freep(&output_data);
    return result_to_return;
}

int WriteFrameWithoutResample(
        AVFrame* decoded_frame,
        const AVChannelLayout& output_layout,
        int output_sample_rate,
        AVSampleFormat output_sample_format,
        int output_channels,
        AudioTempoProcessor* tempo_processor,
        PcmConsumer* consumer,
        std::string* error_message) {
    if (decoded_frame == nullptr || consumer == nullptr) {
        return AVERROR(EINVAL);
    }

    if (av_sample_fmt_is_planar(static_cast<AVSampleFormat>(decoded_frame->format))) {
        return AVERROR(EINVAL);
    }

    const uint8_t* output_buffer = decoded_frame->data[0];
    if (output_buffer == nullptr) {
        return AVERROR(EINVAL);
    }

    if (tempo_processor == nullptr) {
        return 0;
    }

    return tempo_processor->ProcessInterleaved(
            output_buffer,
            decoded_frame->nb_samples,
            output_layout,
            output_sample_format,
            output_sample_rate,
            output_channels,
            consumer,
            error_message);
}

int ApplyPendingSeek(
        AVFormatContext* format_context,
        int audio_stream_index,
        AVRational audio_time_base,
        AVCodecContext* codec_context,
        SwrContext* swr_context,
        AudioTempoProcessor* tempo_processor,
        PcmConsumer* consumer,
        std::string* error_message) {
    if (consumer == nullptr) {
        return 0;
    }

    // 通过 consumer 轮询一次性 seek 请求；<0 表示当前没有 seek。
    const int64_t seek_ms = consumer->TakeSeekPositionMs();
    if (seek_ms < 0) {
        return 0;
    }

    // 将 UI 的毫秒时间换算到流时间轴，并做边界收敛。
    int64_t seek_target = av_rescale_q(seek_ms, AVRational{1, 1000}, audio_time_base);
    if (seek_target < 0) {
        seek_target = 0;
    }

    if (audio_stream_index >= 0 && audio_stream_index < static_cast<int>(format_context->nb_streams)) {
        AVStream* audio_stream = format_context->streams[audio_stream_index];
        if (audio_stream != nullptr &&
            audio_stream->duration != AV_NOPTS_VALUE &&
            audio_stream->duration > 0) {
            seek_target = std::min(seek_target, audio_stream->duration - 1);
        }
    }

    // 优先按音频流维度 seek，定位更精确。
    int result = av_seek_frame(
            format_context,
            audio_stream_index,
            seek_target,
            AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);

    if (result < 0) {
        // 回退到全局时间轴 seek，兼容不支持流级 seek 的容器。
        const int64_t global_seek_target = av_rescale_q(seek_ms, AVRational{1, 1000}, AV_TIME_BASE_Q);
        result = av_seek_frame(
                format_context,
                -1,
                global_seek_target,
                AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY);
    }

    if (result < 0) {
        // 最后再尝试 avformat_seek_file，兼容偏好该语义的 demuxer。
        result = avformat_seek_file(
                format_context,
                audio_stream_index,
                std::numeric_limits<int64_t>::min(),
                seek_target,
                std::numeric_limits<int64_t>::max(),
                AVSEEK_FLAG_ANY);
    }

    if (result < 0) {
        if (error_message != nullptr) {
            *error_message = "seek failed: " + FfErrorToString(result);
        }
        return result;
    }

    // 清空缓存包/帧并重置重采样器，避免 seek 后继续输出旧数据。
    avformat_flush(format_context);
    avcodec_flush_buffers(codec_context);

    if (swr_context != nullptr) {
        swr_close(swr_context);
        result = swr_init(swr_context);
        if (result < 0) {
            if (error_message != nullptr) {
                *error_message = "swr_init after seek failed: " + FfErrorToString(result);
            }
            return result;
        }
    }

    if (tempo_processor != nullptr) {
        tempo_processor->Reset();
    }

    return kSeekHandledCode;
}
}  // namespace
#endif

int FfmpegDecoder::GetAudioMetadata(
        AVFormatContext* format_context,
        AudioMetadata* metadata,
        std::string* error_message) const {
    auto set_error = [error_message](const std::string& message) {
        if (error_message != nullptr) {
            *error_message = message;
        }
    };

    if (metadata != nullptr) {
        *metadata = AudioMetadata{};
    }

#ifndef HAVE_FFMPEG
    set_error("FFmpeg not found in third_party/FFmpeg-n6.1.4/out/<abi>/include or third_party/FFmpeg-n6.1.4/out-jniLibs/<abi>/libffmpeg.so.");
    return -1000;
#else
    if (format_context == nullptr) {
        set_error("format context is null");
        return -1;
    }

    int result = avformat_find_stream_info(format_context, nullptr);
    if (result < 0) {
        set_error("avformat_find_stream_info failed: " + FfErrorToString(result));
        return result;
    }

    // 使用 FFmpeg 的 best stream 选择策略，避免手动遍历的分支复杂度。
    const int audio_stream_index = av_find_best_stream(
            format_context,
            AVMEDIA_TYPE_AUDIO,
            -1,
            -1,
            nullptr,
            0);
    if (audio_stream_index < 0) {
        set_error("av_find_best_stream failed: " + FfErrorToString(audio_stream_index));
        return audio_stream_index;
    }

    AVStream* audio_stream = format_context->streams[audio_stream_index];
    if (audio_stream == nullptr || audio_stream->codecpar == nullptr) {
        set_error("audio stream info unavailable");
        return -2004;
    }

    AVCodecParameters* codec_parameters = audio_stream->codecpar;
    const int sample_rate = codec_parameters->sample_rate > 0 ? codec_parameters->sample_rate : 0;
    const int channels = codec_parameters->ch_layout.nb_channels > 0
            ? codec_parameters->ch_layout.nb_channels
            : 0;
    const int64_t bit_rate = codec_parameters->bit_rate > 0
            ? codec_parameters->bit_rate
            : (format_context->bit_rate > 0 ? format_context->bit_rate : 0);

    // 时长优先取音频流时长，缺失时回退到容器总时长。
    int64_t duration_ms = 0;
    if (audio_stream->duration != AV_NOPTS_VALUE && audio_stream->duration >= 0) {
        duration_ms = av_rescale_q(audio_stream->duration, audio_stream->time_base, AVRational{1, 1000});
    } else if (format_context->duration != AV_NOPTS_VALUE && format_context->duration >= 0) {
        duration_ms = av_rescale_q(format_context->duration, AV_TIME_BASE_Q, AVRational{1, 1000});
    }

    const char* codec_name_raw = avcodec_get_name(codec_parameters->codec_id);

    if (metadata != nullptr) {
        metadata->codec = codec_name_raw != nullptr ? codec_name_raw : "unknown";
        metadata->sample_rate = sample_rate;
        metadata->channels = channels;
        metadata->bit_rate = bit_rate;
        metadata->duration_ms = duration_ms;
    }

    set_error("ok");
    return 0;
#endif
}

int64_t FfmpegDecoder::GetDurationMs(
        AVFormatContext* format_context,
        std::string* error_message) const {
    AudioMetadata metadata;
    std::string metadata_error;
    const int meta_result = GetAudioMetadata(format_context, &metadata, &metadata_error);

    if (error_message != nullptr) {
        *error_message = metadata_error;
    }

    if (meta_result != 0) {
        return meta_result;
    }

    if (metadata.duration_ms <= 0) {
        if (error_message != nullptr) {
            *error_message = "duration unavailable";
        }
        return kDurationUnavailableCode;
    }

    return metadata.duration_ms;
}

int FfmpegDecoder::DecodeAndConsume(
        AVFormatContext* format_context,
        PcmConsumer* consumer,
        std::string* error_message) const {
    auto set_error = [error_message](const std::string& message) {
        if (error_message != nullptr) {
            *error_message = message;
        }
    };

#ifndef HAVE_FFMPEG
    set_error("FFmpeg not found in third_party/FFmpeg-n6.1.4/out/<abi>/include or third_party/FFmpeg-n6.1.4/out-jniLibs/<abi>/libffmpeg.so.");
    return -1000;
#else
    if (format_context == nullptr) {
        set_error("format context is null");
        return -1;
    }
    if (consumer == nullptr) {
        set_error("pcm consumer is null");
        return -2;
    }

    // 阶段 1：读取流信息并选定音频流。
    int result = avformat_find_stream_info(format_context, nullptr);
    if (result < 0) {
        set_error("avformat_find_stream_info failed: " + FfErrorToString(result));
        return result;
    }

    AVCodecContext* codec_context = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    SwrContext* swr_context = nullptr;
    const AVCodec* decoder = nullptr;
    const int audio_stream_index = av_find_best_stream(
            format_context,
            AVMEDIA_TYPE_AUDIO,
            -1,
            -1,
            &decoder,
            0);
    if (audio_stream_index < 0) {
        set_error("av_find_best_stream failed: " + FfErrorToString(audio_stream_index));
        return audio_stream_index;
    }

    AVStream* audio_stream = format_context->streams[audio_stream_index];
    if (audio_stream == nullptr) {
        set_error("audio stream is null");
        return -2004;
    }

    AVChannelLayout output_layout{};
    AVChannelLayout swr_input_layout{};
    bool has_swr_input_layout = false;
    int swr_input_sample_rate = 0;
    AVSampleFormat swr_input_sample_format = AV_SAMPLE_FMT_NONE;
    PcmOutputConfig output_config;
    AVSampleFormat output_sample_format = kFallbackOutputSampleFormat;
    AudioTempoProcessor tempo_processor;
    bool use_resampler = false;

    auto ConsumeDecodedFrame = [&](AVFrame* frame_to_consume) -> int {
        const AVChannelLayout* input_layout =
                frame_to_consume->ch_layout.nb_channels > 0
                        ? &frame_to_consume->ch_layout
                        : &codec_context->ch_layout;
        const int input_sample_rate =
                frame_to_consume->sample_rate > 0
                        ? frame_to_consume->sample_rate
                        : codec_context->sample_rate;
        const AVSampleFormat input_sample_format =
                static_cast<AVSampleFormat>(frame_to_consume->format);

        if (input_layout == nullptr || input_layout->nb_channels <= 0 ||
            input_sample_rate <= 0 ||
            input_sample_format == AV_SAMPLE_FMT_NONE) {
            return AVERROR(EINVAL);
        }

        use_resampler = NeedsResample(
                input_layout,
                input_sample_rate,
                input_sample_format,
                output_layout,
                output_config.sample_rate,
                output_sample_format);

        if (!use_resampler) {
            if (swr_context != nullptr) {
                swr_free(&swr_context);
            }
            if (has_swr_input_layout) {
                av_channel_layout_uninit(&swr_input_layout);
                has_swr_input_layout = false;
            }

            return WriteFrameWithoutResample(
                    frame_to_consume,
                    output_layout,
                    output_config.sample_rate,
                    output_sample_format,
                    output_config.channels,
                    &tempo_processor,
                    consumer,
                    error_message);
        }

        const bool same_input_layout =
                has_swr_input_layout &&
                av_channel_layout_compare(&swr_input_layout, input_layout) == 0;
        const bool same_resample_input =
                swr_context != nullptr &&
                same_input_layout &&
                swr_input_sample_rate == input_sample_rate &&
                swr_input_sample_format == input_sample_format;

        if (!same_resample_input) {
            if (swr_context != nullptr) {
                swr_free(&swr_context);
            }

            swr_context = swr_alloc();
            if (swr_context == nullptr) {
                return AVERROR(ENOMEM);
            }

            av_opt_set_chlayout(swr_context, "in_chlayout", input_layout, 0);
            av_opt_set_int(swr_context, "in_sample_rate", input_sample_rate, 0);
            av_opt_set_sample_fmt(swr_context, "in_sample_fmt", input_sample_format, 0);

            av_opt_set_chlayout(swr_context, "out_chlayout", &output_layout, 0);
            av_opt_set_int(swr_context, "out_sample_rate", output_config.sample_rate, 0);
            av_opt_set_sample_fmt(swr_context, "out_sample_fmt", output_sample_format, 0);

            int local_result = swr_init(swr_context);
            if (local_result < 0) {
                set_error("swr_init failed: " + FfErrorToString(local_result));
                return local_result;
            }

            if (has_swr_input_layout) {
                av_channel_layout_uninit(&swr_input_layout);
                has_swr_input_layout = false;
            }

            local_result = av_channel_layout_copy(&swr_input_layout, input_layout);
            if (local_result < 0) {
                set_error("cache swr input layout failed: " + FfErrorToString(local_result));
                return local_result;
            }

            has_swr_input_layout = true;
            swr_input_sample_rate = input_sample_rate;
            swr_input_sample_format = input_sample_format;
        }

        return WriteResampledFrame(
                swr_context,
                frame_to_consume,
                input_sample_rate,
                output_layout,
                output_config.sample_rate,
                output_sample_format,
                output_config.channels,
                &tempo_processor,
                consumer,
                error_message);
    };

    // 阶段 2：初始化解码器上下文。
    codec_context = avcodec_alloc_context3(decoder);
    if (codec_context == nullptr) {
        result = AVERROR(ENOMEM);
        set_error("avcodec_alloc_context3 failed");
        goto cleanup;
    }

    result = avcodec_parameters_to_context(
            codec_context,
            format_context->streams[audio_stream_index]->codecpar);
    if (result < 0) {
        set_error("avcodec_parameters_to_context failed: " + FfErrorToString(result));
        goto cleanup;
    }

    result = avcodec_open2(codec_context, decoder, nullptr);
    if (result < 0) {
        set_error("avcodec_open2 failed: " + FfErrorToString(result));
        goto cleanup;
    }

    // 根据音频真实格式构建首选输出配置，并交给消费端决定最终生效配置。
    {
        const PcmOutputConfig preferred_output_config = BuildPreferredOutputConfig(codec_context);
        if (!consumer->ConfigureOutput(preferred_output_config, &output_config, error_message)) {
            if (error_message == nullptr || error_message->empty()) {
                set_error("pcm output configuration failed");
            }
            result = kOutputInitFailedCode;
            goto cleanup;
        }
    }

    if (output_config.sample_rate <= 0 || output_config.channels <= 0) {
        set_error("invalid pcm output configuration");
        result = AVERROR(EINVAL);
        goto cleanup;
    }

    output_sample_format = ResolveOutputSampleFormat(output_config);
    av_channel_layout_default(&output_layout, output_config.channels);

    packet = av_packet_alloc();
    frame = av_frame_alloc();
    if (packet == nullptr || frame == nullptr) {
        result = AVERROR(ENOMEM);
        set_error("failed to allocate packet/frame");
        goto cleanup;
    }

    // 阶段 4：主解码循环。
    // 4.1 外层读包；4.2 送入解码器；4.3 拉取帧并重采样输出。
    while (true) {
        if (consumer->ShouldStop()) {
            result = kStoppedCode;
            set_error("stopped");
            goto cleanup;
        }

        // 在读取下一包前处理 seek，提升 UI seek 响应速度。
        result = ApplyPendingSeek(
                format_context,
                audio_stream_index,
                audio_stream->time_base,
                codec_context,
                swr_context,
                &tempo_processor,
                consumer,
                error_message);
        if (result < 0) {
            if (error_message == nullptr || error_message->empty()) {
                set_error("seek failed");
            }
            goto cleanup;
        }
        if (result == kSeekHandledCode) {
            continue;
        }

        result = av_read_frame(format_context, packet);
        if (result < 0) {
            if (result == AVERROR_INVALIDDATA) {
                continue;
            }
            break;
        }

        if (packet->stream_index != audio_stream_index) {
            // 非目标音频流直接丢弃（如视频/字幕流）。
            av_packet_unref(packet);
            continue;
        }

        result = avcodec_send_packet(codec_context, packet);
        av_packet_unref(packet);
        if (result == AVERROR_INVALIDDATA) {
            continue;
        }
        if (result < 0) {
            set_error("avcodec_send_packet failed: " + FfErrorToString(result));
            goto cleanup;
        }

        while ((result = avcodec_receive_frame(codec_context, frame)) >= 0) {
            if (consumer->ShouldStop()) {
                result = kStoppedCode;
                set_error("stopped");
                goto cleanup;
            }

            // 在帧间再次检查 seek，进一步减少 seek 后旧音频残留。
            result = ApplyPendingSeek(
                    format_context,
                    audio_stream_index,
                    audio_stream->time_base,
                    codec_context,
                    swr_context,
                    &tempo_processor,
                    consumer,
                    error_message);
            if (result < 0) {
                if (error_message == nullptr || error_message->empty()) {
                    set_error("seek failed");
                }
                goto cleanup;
            }
            if (result == kSeekHandledCode) {
                av_frame_unref(frame);
                break;
            }

            result = ConsumeDecodedFrame(frame);
            av_frame_unref(frame);
            if (result == kConsumeFailedCode) {
                if (consumer->ShouldStop()) {
                    result = kStoppedCode;
                }
                if (error_message == nullptr || error_message->empty()) {
                    set_error(result == kStoppedCode ? "stopped" : "pcm consumer failed");
                }
                goto cleanup;
            }
            if (result < 0) {
                set_error("resample/consume failed: " + FfErrorToString(result));
                goto cleanup;
            }
        }

        if (result == AVERROR(EAGAIN)) {
            continue;
        }
        if (result == AVERROR_INVALIDDATA) {
            continue;
        }
        if (result < 0 && result != AVERROR_EOF) {
            set_error("avcodec_receive_frame failed: " + FfErrorToString(result));
            goto cleanup;
        }
    }

    if (result != AVERROR_EOF && result != AVERROR_INVALIDDATA) {
        set_error("av_read_frame failed: " + FfErrorToString(result));
        goto cleanup;
    }

    // 到达 EOF 后继续 flush 解码器，取出内部仍缓存的帧。
    result = avcodec_send_packet(codec_context, nullptr);
    if (result == AVERROR_EOF) {
        result = 0;
    }
    if (result < 0) {
        set_error("flush send_packet failed: " + FfErrorToString(result));
        goto cleanup;
    }

    if (result == 0) {
        while ((result = avcodec_receive_frame(codec_context, frame)) >= 0) {
            if (consumer->ShouldStop()) {
                result = kStoppedCode;
                set_error("stopped");
                goto cleanup;
            }

            result = ConsumeDecodedFrame(frame);
            av_frame_unref(frame);
            if (result == kConsumeFailedCode) {
                if (consumer->ShouldStop()) {
                    result = kStoppedCode;
                }
                if (error_message == nullptr || error_message->empty()) {
                    set_error(result == kStoppedCode ? "stopped" : "pcm consumer failed");
                }
                goto cleanup;
            }
            if (result < 0) {
                set_error("flush resample/consume failed: " + FfErrorToString(result));
                goto cleanup;
            }
        }

        if (result == AVERROR_EOF || result == AVERROR(EAGAIN) || result == AVERROR_INVALIDDATA) {
            result = 0;
        } else if (result < 0) {
            set_error("flush receive_frame failed: " + FfErrorToString(result));
            goto cleanup;
        }
    }

    if (result == 0) {
        result = tempo_processor.Flush(consumer, error_message);
        if (result == kConsumeFailedCode) {
            if (consumer->ShouldStop()) {
                result = kStoppedCode;
            }
            if (error_message == nullptr || error_message->empty()) {
                set_error(result == kStoppedCode ? "stopped" : "pcm consumer failed");
            }
            goto cleanup;
        }
        if (result < 0) {
            set_error("tempo flush failed: " + FfErrorToString(result));
            goto cleanup;
        }
        set_error("ok");
    }

cleanup:
    // 阶段 6：统一清理资源，所有错误路径都汇聚到这里。
    if (packet != nullptr) {
        av_packet_free(&packet);
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
    }
    if (swr_context != nullptr) {
        swr_free(&swr_context);
    }
    if (codec_context != nullptr) {
        avcodec_free_context(&codec_context);
    }

    if (has_swr_input_layout) {
        av_channel_layout_uninit(&swr_input_layout);
    }

    av_channel_layout_uninit(&output_layout);
    return result;
#endif
}
