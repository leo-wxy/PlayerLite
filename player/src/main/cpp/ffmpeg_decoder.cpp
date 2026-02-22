#include "ffmpeg_decoder.h"

#include <cstdint>
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
#include <libswresample/swresample.h>
}

namespace {
constexpr int kOutputSampleRate = 44100;
constexpr AVSampleFormat kOutputSampleFormat = AV_SAMPLE_FMT_S16;
constexpr int kOutputChannels = 2;
constexpr int kStoppedCode = -2001;
constexpr int kConsumeFailedCode = -2002;
constexpr int kSeekHandledCode = 1;

std::string FfErrorToString(int ff_error) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(ff_error, buffer, sizeof(buffer));
    return std::string(buffer);
}

int WriteResampledFrame(
        SwrContext* swr,
        AVCodecContext* codec_context,
        AVFrame* decoded_frame,
        PcmConsumer* consumer,
        std::string* error_message) {
    const int64_t delayed = swr_get_delay(swr, codec_context->sample_rate);
    const int dst_nb_samples = static_cast<int>(av_rescale_rnd(
            delayed + decoded_frame->nb_samples,
            kOutputSampleRate,
            codec_context->sample_rate,
            AV_ROUND_UP));

    uint8_t** output_data = nullptr;
    int output_line_size = 0;
    int result = av_samples_alloc_array_and_samples(
            &output_data,
            &output_line_size,
            kOutputChannels,
            dst_nb_samples,
            kOutputSampleFormat,
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

    const int output_size = av_samples_get_buffer_size(
            &output_line_size,
            kOutputChannels,
            converted,
            kOutputSampleFormat,
            1);
    if (output_size < 0) {
        av_freep(&output_data[0]);
        av_freep(&output_data);
        return output_size;
    }

    bool consumed_ok = true;
    if (consumer != nullptr) {
        consumed_ok = consumer->Consume(output_data[0], output_size, error_message);
    }

    av_freep(&output_data[0]);
    av_freep(&output_data);

    if (!consumed_ok) {
        return kConsumeFailedCode;
    }

    return 0;
}

int ApplyPendingSeek(
        AVFormatContext* format_context,
        int audio_stream_index,
        AVRational audio_time_base,
        AVCodecContext* codec_context,
        SwrContext* swr_context,
        PcmConsumer* consumer,
        std::string* error_message) {
    if (consumer == nullptr) {
        return 0;
    }

    const int64_t seek_ms = consumer->TakeSeekPositionMs();
    if (seek_ms < 0) {
        return 0;
    }

    const int64_t seek_target = av_rescale_q(seek_ms, AVRational{1, 1000}, audio_time_base);
    int result = av_seek_frame(format_context, audio_stream_index, seek_target, AVSEEK_FLAG_BACKWARD);
    if (result < 0) {
        if (error_message != nullptr) {
            *error_message = "av_seek_frame failed: " + FfErrorToString(result);
        }
        return result;
    }

    avformat_flush(format_context);
    avcodec_flush_buffers(codec_context);

    swr_close(swr_context);
    result = swr_init(swr_context);
    if (result < 0) {
        if (error_message != nullptr) {
            *error_message = "swr_init after seek failed: " + FfErrorToString(result);
        }
        return result;
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

    AVChannelLayout output_layout;
    av_channel_layout_default(&output_layout, kOutputChannels);

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

    swr_context = swr_alloc();
    if (swr_context == nullptr) {
        result = AVERROR(ENOMEM);
        set_error("swr_alloc failed");
        goto cleanup;
    }

    av_opt_set_chlayout(swr_context, "in_chlayout", &codec_context->ch_layout, 0);
    av_opt_set_int(swr_context, "in_sample_rate", codec_context->sample_rate, 0);
    av_opt_set_sample_fmt(swr_context, "in_sample_fmt", codec_context->sample_fmt, 0);

    av_opt_set_chlayout(swr_context, "out_chlayout", &output_layout, 0);
    av_opt_set_int(swr_context, "out_sample_rate", kOutputSampleRate, 0);
    av_opt_set_sample_fmt(swr_context, "out_sample_fmt", kOutputSampleFormat, 0);

    result = swr_init(swr_context);
    if (result < 0) {
        set_error("swr_init failed: " + FfErrorToString(result));
        goto cleanup;
    }

    packet = av_packet_alloc();
    frame = av_frame_alloc();
    if (packet == nullptr || frame == nullptr) {
        result = AVERROR(ENOMEM);
        set_error("failed to allocate packet/frame");
        goto cleanup;
    }

    while (true) {
        if (consumer->ShouldStop()) {
            result = kStoppedCode;
            set_error("stopped");
            goto cleanup;
        }

        result = ApplyPendingSeek(
                format_context,
                audio_stream_index,
                audio_stream->time_base,
                codec_context,
                swr_context,
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

            result = ApplyPendingSeek(
                    format_context,
                    audio_stream_index,
                    audio_stream->time_base,
                    codec_context,
                    swr_context,
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

            result = WriteResampledFrame(swr_context, codec_context, frame, consumer, error_message);
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

            result = WriteResampledFrame(swr_context, codec_context, frame, consumer, error_message);
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
        set_error("ok");
    }

cleanup:
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

    av_channel_layout_uninit(&output_layout);
    return result;
#endif
}
