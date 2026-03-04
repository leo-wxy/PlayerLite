#include <jni.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "ffmpeg_player.h"
#include "jni_play_source.h"

namespace {
constexpr jint kFallbackSampleRate = 44100;
constexpr jint kDeviceNativeCommonSampleRate = 48000;
constexpr jint kChannelConfigMono = 4;      // AudioFormat.CHANNEL_OUT_MONO
constexpr jint kChannelConfigStereo = 12;  // AudioFormat.CHANNEL_OUT_STEREO
constexpr jint kChannelConfigQuad = 204;    // AudioFormat.CHANNEL_OUT_QUAD
constexpr jint kChannelConfig5Point1 = 252; // AudioFormat.CHANNEL_OUT_5POINT1
constexpr jint kChannelConfig7Point1 = 6396; // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
constexpr jint kEncodingPcm16Bit = 2;      // AudioFormat.ENCODING_PCM_16BIT
constexpr jint kEncodingPcmFloat = 4;      // AudioFormat.ENCODING_PCM_FLOAT
constexpr jint kOutputEncodingCodePcm16 = 0;
constexpr jint kOutputEncodingCodePcmFloat = 1;
constexpr jint kStreamMusic = 3;           // AudioManager.STREAM_MUSIC
constexpr jint kModeStream = 1;            // AudioTrack.MODE_STREAM
constexpr jint kWriteBlocking = 0;         // AudioTrack.WRITE_BLOCKING
constexpr jint kStoppedCode = -2001;
constexpr jint kAudioTrackInitCode = -3001;
constexpr jint kAlreadyPlayingCode = -2005;
constexpr jint kSeekUnavailableCode = -2006;
constexpr jint kContextUnavailableCode = -6;
constexpr int64_t kProgressNotifyIntervalMs = 500;
constexpr int kAudioWriteChunkBytes = 16 * 1024;
constexpr int kAudioWriteRecoverMaxRetries = 2;
constexpr int kPlaybackHeadStallWriteCycles = 20;

jint ResolveChannelConfig(int channels) {
    switch (channels) {
        case 1:
            return kChannelConfigMono;
        case 2:
            return kChannelConfigStereo;
        case 4:
            return kChannelConfigQuad;
        case 6:
            return kChannelConfig5Point1;
        case 8:
            return kChannelConfig7Point1;
        default:
            return 0;
    }
}

// NativePlayer 的共享上下文：
// - 由 Kotlin 层的 nativeContextHandle 持有指针。
// - 多个 JNI 接口共用该状态（播放控制、查询、错误信息）。
// - 通过 active_calls/active_playbacks + release_requested 做延迟释放。
struct PlayerContext {
    // 每个 NativePlayer 实例在 JNI 调用间共享的运行状态。
    std::mutex error_mutex;
    std::string last_error = "ok";

    std::atomic<bool> stop_requested{false};
    std::atomic<bool> pause_requested{false};
    std::atomic<int64_t> seek_position_ms{-1};

    std::mutex pause_mutex;
    std::condition_variable pause_cv;

    std::mutex audio_track_mutex;
    jobject active_audio_track = nullptr;

    std::atomic<int> active_calls{0};
    std::atomic<int> active_playbacks{0};
    std::atomic<bool> release_requested{false};
};

std::mutex g_context_init_mutex;
std::mutex g_native_field_init_mutex;
std::atomic<jfieldID> g_native_context_field{nullptr};

// 获取并缓存 Kotlin 字段 nativeContextHandle 的 field id。
// 该字段用于保存 native PlayerContext* 指针。
jfieldID ResolveNativeContextField(JNIEnv* env, jobject thiz) {
    if (env == nullptr || thiz == nullptr) {
        return nullptr;
    }

    jfieldID cached = g_native_context_field.load(std::memory_order_acquire);
    if (cached != nullptr) {
        return cached;
    }

    // 缓存 field id，避免在高频路径重复反射查找。
    std::lock_guard<std::mutex> lock(g_native_field_init_mutex);
    cached = g_native_context_field.load(std::memory_order_relaxed);
    if (cached != nullptr) {
        return cached;
    }

    jclass native_player_class = env->GetObjectClass(thiz);
    if (native_player_class == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return nullptr;
    }

    jfieldID context_field = env->GetFieldID(native_player_class, "nativeContextHandle", "J");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        context_field = nullptr;
    }

    env->DeleteLocalRef(native_player_class);
    if (context_field != nullptr) {
        g_native_context_field.store(context_field, std::memory_order_release);
    }
    return context_field;
}

PlayerContext* GetContextNoCreate(JNIEnv* env, jobject thiz) {
    const jfieldID context_field = ResolveNativeContextField(env, thiz);
    if (context_field == nullptr) {
        return nullptr;
    }

    const jlong handle = env->GetLongField(thiz, context_field);
    return reinterpret_cast<PlayerContext*>(handle);
}

PlayerContext* GetOrCreateContext(JNIEnv* env, jobject thiz) {
    const jfieldID context_field = ResolveNativeContextField(env, thiz);
    if (context_field == nullptr) {
        return nullptr;
    }

    jlong handle = env->GetLongField(thiz, context_field);
    if (handle != 0) {
        return reinterpret_cast<PlayerContext*>(handle);
    }

    std::lock_guard<std::mutex> lock(g_context_init_mutex);
    handle = env->GetLongField(thiz, context_field);
    if (handle != 0) {
        return reinterpret_cast<PlayerContext*>(handle);
    }

    // 首次访问时懒初始化 context。
    auto* context = new PlayerContext();
    env->SetLongField(thiz, context_field, reinterpret_cast<jlong>(context));
    return context;
}

void SetLastError(PlayerContext* context, const std::string& message) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->error_mutex);
    context->last_error = message;
}

std::string GetLastError(PlayerContext* context) {
    if (context == nullptr) {
        return "ok";
    }
    std::lock_guard<std::mutex> lock(context->error_mutex);
    return context->last_error;
}

void TryReleaseContext(JNIEnv* env, jobject thiz, PlayerContext* context) {
    if (env == nullptr || thiz == nullptr || context == nullptr) {
        return;
    }

    // 仅在没有活动 JNI 调用时尝试释放。
    if (context->active_calls.load(std::memory_order_relaxed) != 0) {
        return;
    }

    // 仅在没有活跃播放流程时尝试释放。
    if (context->active_playbacks.load(std::memory_order_relaxed) != 0) {
        return;
    }

    const jfieldID context_field = ResolveNativeContextField(env, thiz);
    if (context_field == nullptr) {
        return;
    }

    const jlong handle = env->GetLongField(thiz, context_field);
    if (reinterpret_cast<PlayerContext*>(handle) != context) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(context->audio_track_mutex);
        if (context->active_audio_track != nullptr) {
            env->DeleteGlobalRef(context->active_audio_track);
            context->active_audio_track = nullptr;
        }
    }

    env->SetLongField(thiz, context_field, 0);
    delete context;
}

class ScopedContextUse {
public:
    ScopedContextUse(JNIEnv* env, jobject thiz, PlayerContext* context)
        : env_(env), thiz_(thiz), context_(context) {
        if (context_ != nullptr) {
            context_->active_calls.fetch_add(1, std::memory_order_relaxed);
        }
    }

    ~ScopedContextUse() {
        if (context_ == nullptr) {
            return;
        }

        // 延迟释放，确保没有 JNI 调用仍在使用该 context。
        const int active = context_->active_calls.fetch_sub(1, std::memory_order_relaxed) - 1;
        if (active == 0 && context_->release_requested.load(std::memory_order_relaxed)) {
            TryReleaseContext(env_, thiz_, context_);
        }
    }

    ScopedContextUse(const ScopedContextUse&) = delete;
    ScopedContextUse& operator=(const ScopedContextUse&) = delete;

private:
    JNIEnv* env_ = nullptr;
    jobject thiz_ = nullptr;
    PlayerContext* context_ = nullptr;
};

jobject BuildAudioMetaObject(JNIEnv* env, const AudioMetadata& metadata) {
    if (env == nullptr) {
        return nullptr;
    }

    jclass meta_class = env->FindClass("com/wxy/playerlite/player/AudioMeta");
    if (meta_class == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return nullptr;
    }

    jmethodID ctor_mid = env->GetMethodID(meta_class, "<init>", "(Ljava/lang/String;IIJJ)V");
    if (ctor_mid == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(meta_class);
        return nullptr;
    }

    const std::string codec = metadata.codec.empty() ? "-" : metadata.codec;
    jstring codec_j = env->NewStringUTF(codec.c_str());
    if (codec_j == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(meta_class);
        return nullptr;
    }

    jobject meta_object = env->NewObject(
            meta_class,
            ctor_mid,
            codec_j,
            static_cast<jint>(metadata.sample_rate),
            static_cast<jint>(metadata.channels),
            static_cast<jlong>(metadata.bit_rate),
            static_cast<jlong>(metadata.duration_ms));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        meta_object = nullptr;
    }

    env->DeleteLocalRef(codec_j);
    env->DeleteLocalRef(meta_class);
    return meta_object;
}

class AudioTrackConsumer final : public PcmConsumer {
public:
    AudioTrackConsumer(
            JNIEnv* env,
            jobject callback_owner,
            PlayerContext* context)
        : env_(env),
          callback_owner_local_(callback_owner),
          context_(context) {}

    ~AudioTrackConsumer() override {
        Shutdown();
    }

    bool Init(std::string* error_message) {
        // 解析并缓存 AudioTrack 所需 JNI 类/方法，后续播放循环直接调用。
        jclass local_audio_track_class = env_->FindClass("android/media/AudioTrack");
        if (local_audio_track_class == nullptr) {
            return Fail(error_message, "failed to find AudioTrack class");
        }
        audio_track_class_ = reinterpret_cast<jclass>(env_->NewGlobalRef(local_audio_track_class));
        env_->DeleteLocalRef(local_audio_track_class);
        if (audio_track_class_ == nullptr) {
            return Fail(error_message, "failed to create global ref for AudioTrack class");
        }

        get_min_buffer_size_mid_ = env_->GetStaticMethodID(
                audio_track_class_,
                "getMinBufferSize",
                "(III)I");
        ctor_mid_ = env_->GetMethodID(audio_track_class_, "<init>", "(IIIIII)V");
        play_mid_ = env_->GetMethodID(audio_track_class_, "play", "()V");
        pause_mid_ = env_->GetMethodID(audio_track_class_, "pause", "()V");
        get_playback_head_position_mid_ = env_->GetMethodID(audio_track_class_, "getPlaybackHeadPosition", "()I");
        write_array_mid_ = env_->GetMethodID(audio_track_class_, "write", "([BII)I");
        write_direct_mid_ = env_->GetMethodID(audio_track_class_, "write", "(Ljava/nio/ByteBuffer;II)I");
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            write_direct_mid_ = nullptr;
        }
        // Keep conservative path on emulator/device combinations where direct
        // ByteBuffer writes can introduce instability after frequent seek.
        write_direct_mid_ = nullptr;
        stop_mid_ = env_->GetMethodID(audio_track_class_, "stop", "()V");
        flush_mid_ = env_->GetMethodID(audio_track_class_, "flush", "()V");
        release_mid_ = env_->GetMethodID(audio_track_class_, "release", "()V");

        if (callback_owner_local_ == nullptr) {
            return Fail(error_message, "callback owner is null");
        }

        callback_owner_ = env_->NewGlobalRef(callback_owner_local_);
        if (callback_owner_ == nullptr) {
            return Fail(error_message, "failed to create callback owner global ref");
        }

        jclass callback_owner_class = env_->GetObjectClass(callback_owner_);
        if (callback_owner_class == nullptr) {
            return Fail(error_message, "failed to resolve callback owner class");
        }
        on_native_progress_mid_ = env_->GetMethodID(callback_owner_class, "onNativeProgress", "(J)V");
        on_native_output_config_mid_ = env_->GetMethodID(
                callback_owner_class,
                "onNativeOutputConfig",
                "(IIIIIIZ)V");
        env_->DeleteLocalRef(callback_owner_class);

        if (get_min_buffer_size_mid_ == nullptr || ctor_mid_ == nullptr || play_mid_ == nullptr ||
            pause_mid_ == nullptr ||
            get_playback_head_position_mid_ == nullptr ||
            write_array_mid_ == nullptr || stop_mid_ == nullptr || flush_mid_ == nullptr ||
            release_mid_ == nullptr || on_native_progress_mid_ == nullptr ||
            on_native_output_config_mid_ == nullptr) {
            return Fail(error_message, "failed to resolve AudioTrack methods");
        }

        return true;
    }

    bool ConfigureOutput(
            const PcmOutputConfig& preferred,
            PcmOutputConfig* applied,
            std::string* error_message) override {
        const jint preferred_channel_config = ResolveChannelConfig(preferred.channels);
        const jint preferred_encoding =
                preferred.encoding == PcmOutputEncoding::kPcmFloat ? kEncodingPcmFloat : kEncodingPcm16Bit;

        std::vector<OutputCandidate> candidates;

        auto append_candidate = [&candidates](const OutputCandidate& candidate) {
            for (const OutputCandidate& existing : candidates) {
                if (existing.sample_rate == candidate.sample_rate &&
                    existing.channels == candidate.channels &&
                    existing.channel_config == candidate.channel_config &&
                    existing.encoding == candidate.encoding) {
                    return;
                }
            }
            candidates.push_back(candidate);
        };

        auto make_candidate = [](jint sample_rate, int channels, jint channel_config, jint encoding) {
            OutputCandidate candidate;
            candidate.sample_rate = sample_rate;
            candidate.channels = channels;
            candidate.channel_config = channel_config;
            candidate.encoding = encoding;
            return candidate;
        };

        std::vector<jint> sample_rate_candidates;
        auto append_sample_rate = [&sample_rate_candidates](jint sample_rate) {
            if (sample_rate <= 0) {
                return;
            }
            for (jint existing_rate : sample_rate_candidates) {
                if (existing_rate == sample_rate) {
                    return;
                }
            }
            sample_rate_candidates.push_back(sample_rate);
        };
        append_sample_rate(preferred.sample_rate > 0 ? preferred.sample_rate : kFallbackSampleRate);
        append_sample_rate(kDeviceNativeCommonSampleRate);
        append_sample_rate(kFallbackSampleRate);

        std::vector<jint> encoding_candidates;
        encoding_candidates.push_back(preferred_encoding);
        if (preferred_encoding == kEncodingPcmFloat) {
            encoding_candidates.push_back(kEncodingPcm16Bit);
        }

        std::vector<std::pair<int, jint>> channel_candidates;
        if (preferred.channels > 0 && preferred_channel_config != 0) {
            channel_candidates.emplace_back(preferred.channels, preferred_channel_config);
        }
        if (preferred.channels != 2 || preferred_channel_config == 0) {
            channel_candidates.emplace_back(2, kChannelConfigStereo);
        }
        if (channel_candidates.empty()) {
            channel_candidates.emplace_back(2, kChannelConfigStereo);
        }

        // 降级顺序：先格式，再采样率，最后才降声道。
        for (const auto& channel_candidate : channel_candidates) {
            for (jint sample_rate_candidate : sample_rate_candidates) {
                for (jint encoding_candidate : encoding_candidates) {
                    append_candidate(make_candidate(
                            sample_rate_candidate,
                            channel_candidate.first,
                            channel_candidate.second,
                            encoding_candidate));
                }
            }
        }

        append_candidate(make_candidate(
                kFallbackSampleRate,
                2,
                kChannelConfigStereo,
                kEncodingPcm16Bit));

        std::string last_error = "AudioTrack output configuration unsupported";
        for (const OutputCandidate& candidate : candidates) {
            std::string candidate_error;
            if (TryStartAudioTrack(candidate, &candidate_error)) {
                output_sample_rate_ = candidate.sample_rate;
                output_channel_count_ = candidate.channels;
                output_encoding_ = candidate.encoding;

                if (applied != nullptr) {
                    applied->sample_rate = candidate.sample_rate;
                    applied->channels = candidate.channels;
                    applied->encoding =
                            candidate.encoding == kEncodingPcmFloat
                                    ? PcmOutputEncoding::kPcmFloat
                                    : PcmOutputEncoding::kPcm16;
                }

                const jint input_encoding_code =
                        preferred.encoding == PcmOutputEncoding::kPcmFloat
                                ? kOutputEncodingCodePcmFloat
                                : kOutputEncodingCodePcm16;
                const jint output_encoding_code =
                        candidate.encoding == kEncodingPcmFloat
                                ? kOutputEncodingCodePcmFloat
                                : kOutputEncodingCodePcm16;
                const jboolean uses_resampler =
                        preferred.sample_rate != candidate.sample_rate ||
                        preferred.channels != candidate.channels ||
                        input_encoding_code != output_encoding_code;
                env_->CallVoidMethod(
                        callback_owner_,
                        on_native_output_config_mid_,
                        static_cast<jint>(preferred.sample_rate),
                        static_cast<jint>(preferred.channels),
                        input_encoding_code,
                        static_cast<jint>(candidate.sample_rate),
                        static_cast<jint>(candidate.channels),
                        output_encoding_code,
                        uses_resampler);
                if (env_->ExceptionCheck()) {
                    env_->ExceptionClear();
                }

                NotifyProgressIfNeeded(true, nullptr);
                return true;
            }

            if (!candidate_error.empty()) {
                last_error = candidate_error;
            }
        }

        return Fail(error_message, last_error.c_str());
    }

    bool Consume(const uint8_t* data, int size, std::string* error_message) override {
        if (data == nullptr || size <= 0) {
            return true;
        }

        if (!HandlePauseState(error_message)) {
            return false;
        }

        if (!ApplyPendingTrackFlush(error_message)) {
            return false;
        }

        if (ShouldStop()) {
            return Fail(error_message, "stopped");
        }

        if (audio_track_ == nullptr) {
            return Fail(error_message, "AudioTrack is not initialized");
        }

        // 可能出现分段写入，循环直到整块 PCM 写完。
        int offset = 0;
        int recover_retries = 0;
        while (offset < size) {
            if (!HandlePauseState(error_message)) {
                return false;
            }

            if (ShouldStop()) {
                return Fail(error_message, "stopped");
            }

            const int chunk_size = std::min(size - offset, kAudioWriteChunkBytes);

            jint written = 0;
            if (write_direct_mid_ != nullptr) {
                // 快路径：尽量直接把 native buffer 写入 AudioTrack，减少拷贝。
                jobject chunk_buffer = env_->NewDirectByteBuffer(
                        const_cast<uint8_t*>(data) + offset,
                        static_cast<jlong>(chunk_size));
                if (chunk_buffer != nullptr) {
                    written = env_->CallIntMethod(
                            audio_track_,
                            write_direct_mid_,
                            chunk_buffer,
                            static_cast<jint>(chunk_size),
                            kWriteBlocking);
                    env_->DeleteLocalRef(chunk_buffer);

                    if (!env_->ExceptionCheck()) {
                        if (written <= 0) {
                            if (recover_retries < kAudioWriteRecoverMaxRetries &&
                                TryRecoverAudioTrackAfterWriteFailure("AudioTrack.write(ByteBuffer)", error_message)) {
                                recover_retries += 1;
                                continue;
                            }
                            return Fail(error_message, "AudioTrack.write(ByteBuffer) failed");
                        }
                        recover_retries = 0;
                        offset += written;
                        continue;
                    }

                    env_->ExceptionClear();
                    if (recover_retries < kAudioWriteRecoverMaxRetries &&
                        TryRecoverAudioTrackAfterWriteFailure(
                                "AudioTrack.write(ByteBuffer) threw exception",
                                error_message)) {
                        recover_retries += 1;
                        continue;
                    }
                } else if (env_->ExceptionCheck()) {
                    env_->ExceptionClear();
                }
            }

            // 回退路径：不支持 direct ByteBuffer 写入时走 byte[]。
            if (!EnsureWriteArrayBuffer(chunk_size, error_message)) {
                return false;
            }

            env_->SetByteArrayRegion(
                    write_array_buffer_,
                    0,
                    chunk_size,
                    reinterpret_cast<const jbyte*>(data + offset));
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack write buffer conversion failed");
            }

            written = env_->CallIntMethod(audio_track_, write_array_mid_, write_array_buffer_, 0, chunk_size);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                if (recover_retries < kAudioWriteRecoverMaxRetries &&
                    TryRecoverAudioTrackAfterWriteFailure(
                            "AudioTrack.write(byte[]) threw exception",
                            error_message)) {
                    recover_retries += 1;
                    continue;
                }
                return Fail(error_message, "AudioTrack.write(byte[]) threw exception");
            }
            if (written <= 0) {
                if (recover_retries < kAudioWriteRecoverMaxRetries &&
                    TryRecoverAudioTrackAfterWriteFailure("AudioTrack.write(byte[]) failed", error_message)) {
                    recover_retries += 1;
                    continue;
                }
                return Fail(error_message, "AudioTrack.write(byte[]) failed");
            }

            recover_retries = 0;
            offset += written;

            if (TryRecoverForPlaybackHeadStall(error_message)) {
                recover_retries += 1;
                continue;
            }
        }

        NotifyProgressIfNeeded(false, nullptr);

        return true;
    }

    bool ShouldStop() const override {
        return context_ != nullptr && context_->stop_requested.load(std::memory_order_relaxed);
    }

    int64_t TakeSeekPositionMs() override {
        if (context_ == nullptr) {
            return -1;
        }
        const int64_t seek_ms = context_->seek_position_ms.exchange(-1, std::memory_order_relaxed);
        if (seek_ms >= 0) {
            // 通知后续输出侧在 seek 后执行 AudioTrack flush，避免旧缓冲残留。
            pending_seek_base_ms_ = seek_ms;
            has_pending_seek_base_ = true;
            pending_audio_track_flush_ = true;
        }
        return seek_ms;
    }

private:
    struct OutputCandidate {
        jint sample_rate = 0;
        int channels = 0;
        jint channel_config = 0;
        jint encoding = 0;
    };

    void DetachContextAudioTrackRef() {
        if (context_ == nullptr) {
            return;
        }
        std::lock_guard<std::mutex> lock(context_->audio_track_mutex);
        if (context_->active_audio_track == audio_track_) {
            context_->active_audio_track = nullptr;
        }
    }

    void ReleaseAudioTrackInstance() {
        if (audio_track_ == nullptr) {
            return;
        }

        DetachContextAudioTrackRef();

        env_->CallVoidMethod(audio_track_, stop_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }
        env_->CallVoidMethod(audio_track_, flush_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }
        env_->CallVoidMethod(audio_track_, release_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }

        env_->DeleteGlobalRef(audio_track_);
        audio_track_ = nullptr;
    }

    bool TryStartAudioTrack(const OutputCandidate& candidate, std::string* error_message) {
        ReleaseAudioTrackInstance();

        const jint min_buffer_size = env_->CallStaticIntMethod(
                audio_track_class_,
                get_min_buffer_size_mid_,
                candidate.sample_rate,
                candidate.channel_config,
                candidate.encoding);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return Fail(error_message, "AudioTrack.getMinBufferSize threw exception");
        }
        if (min_buffer_size <= 0) {
            return Fail(error_message, "AudioTrack.getMinBufferSize unsupported for target format");
        }

        // Keep output latency low for seek responsiveness.
        int64_t buffer_size_64 = static_cast<int64_t>(min_buffer_size) * 2;
        if (buffer_size_64 > 256 * 1024) {
            buffer_size_64 = 256 * 1024;
        }
        const jint buffer_size = static_cast<jint>(buffer_size_64);
        jobject local_audio_track = env_->NewObject(
                audio_track_class_,
                ctor_mid_,
                kStreamMusic,
                candidate.sample_rate,
                candidate.channel_config,
                candidate.encoding,
                buffer_size,
                kModeStream);
        if (local_audio_track == nullptr || env_->ExceptionCheck()) {
            env_->ExceptionClear();
            if (local_audio_track != nullptr) {
                env_->DeleteLocalRef(local_audio_track);
            }
            return Fail(error_message, "failed to create AudioTrack instance");
        }

        audio_track_ = env_->NewGlobalRef(local_audio_track);
        env_->DeleteLocalRef(local_audio_track);
        if (audio_track_ == nullptr) {
            return Fail(error_message, "failed to create global ref for AudioTrack object");
        }

        env_->CallVoidMethod(audio_track_, play_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            ReleaseAudioTrackInstance();
            return Fail(error_message, "AudioTrack.play threw exception");
        }

        if (context_ != nullptr) {
            std::lock_guard<std::mutex> lock(context_->audio_track_mutex);
            context_->active_audio_track = audio_track_;
        }

        has_playback_head_ = false;
        last_playback_head_u32_ = 0;
        playback_head_wrap_count_ = 0;
        has_write_head_probe_ = false;
        stagnant_write_cycles_ = 0;
        seek_anchor_head_frames_ = 0;
        last_notified_progress_ms_ = -1;
        pending_audio_track_flush_ = false;
        is_paused_ = false;

        return true;
    }

    bool HandlePauseState(std::string* error_message) {
        if (context_ == nullptr || audio_track_ == nullptr) {
            return true;
        }

        const bool should_pause = context_->pause_requested.load(std::memory_order_relaxed);
        if (should_pause && !is_paused_) {
            env_->CallVoidMethod(audio_track_, pause_mid_);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack.pause threw exception");
            }
            is_paused_ = true;
            NotifyProgressIfNeeded(true, nullptr);
        }

        while (context_->pause_requested.load(std::memory_order_relaxed) && !ShouldStop()) {
            std::unique_lock<std::mutex> pause_lock(context_->pause_mutex);
            context_->pause_cv.wait(pause_lock, [this]() {
                return !context_->pause_requested.load(std::memory_order_relaxed) || ShouldStop();
            });
        }

        if (!context_->pause_requested.load(std::memory_order_relaxed) && is_paused_) {
            env_->CallVoidMethod(audio_track_, play_mid_);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack.play threw exception");
            }
            is_paused_ = false;
            NotifyProgressIfNeeded(true, nullptr);
        }

        return true;
    }

    bool ApplyPendingTrackFlush(std::string* error_message) {
        if (!pending_audio_track_flush_) {
            return true;
        }
        if (audio_track_ == nullptr) {
            pending_audio_track_flush_ = false;
            return true;
        }

        env_->CallVoidMethod(audio_track_, pause_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return Fail(error_message, "AudioTrack.pause for seek flush threw exception");
        }

        env_->CallVoidMethod(audio_track_, flush_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return Fail(error_message, "AudioTrack.flush for seek threw exception");
        }

        const bool should_pause =
                context_ != nullptr && context_->pause_requested.load(std::memory_order_relaxed);
        if (!should_pause && !ShouldStop()) {
            env_->CallVoidMethod(audio_track_, play_mid_);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack.play after seek flush threw exception");
            }
            is_paused_ = false;
        } else {
            is_paused_ = true;
        }

        has_playback_head_ = false;
        last_playback_head_u32_ = 0;
        playback_head_wrap_count_ = 0;
        has_write_head_probe_ = false;
        stagnant_write_cycles_ = 0;
        seek_anchor_head_frames_ = 0;
        last_notified_progress_ms_ = -1;
        pending_audio_track_flush_ = false;

        NotifyProgressIfNeeded(true, nullptr);
        return true;
    }

    bool ReadPlaybackHeadFrames(uint64_t* out_frames, std::string* error_message) {
        if (out_frames == nullptr || audio_track_ == nullptr || get_playback_head_position_mid_ == nullptr) {
            return Fail(error_message, "AudioTrack.getPlaybackHeadPosition unavailable");
        }

        const jint head_position = env_->CallIntMethod(audio_track_, get_playback_head_position_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return Fail(error_message, "AudioTrack.getPlaybackHeadPosition threw exception");
        }

        const uint32_t head_u32 = static_cast<uint32_t>(head_position);
        if (has_playback_head_) {
            // 播放头是 32 位计数器，需处理回绕以保持进度单调递增。
            if (head_u32 < last_playback_head_u32_) {
                playback_head_wrap_count_ += 1;
            }
        } else {
            has_playback_head_ = true;
        }

        last_playback_head_u32_ = head_u32;
        *out_frames = (playback_head_wrap_count_ << 32) + static_cast<uint64_t>(head_u32);
        return true;
    }

    bool NotifyProgressIfNeeded(bool force, std::string* error_message) {
        if (callback_owner_ == nullptr || on_native_progress_mid_ == nullptr) {
            return true;
        }

        uint64_t playback_head_frames = 0;
        if (!ReadPlaybackHeadFrames(&playback_head_frames, error_message)) {
            if (error_message != nullptr) {
                error_message->clear();
            }
            return true;
        }

        if (has_pending_seek_base_) {
            seek_base_ms_ = pending_seek_base_ms_;
            seek_anchor_head_frames_ = playback_head_frames;
            has_pending_seek_base_ = false;
            last_notified_progress_ms_ = -1;
        }

        int64_t delta_frames = static_cast<int64_t>(playback_head_frames - seek_anchor_head_frames_);
        if (delta_frames < 0) {
            delta_frames = 0;
        }

        const int64_t progress_sample_rate =
                output_sample_rate_ > 0 ? output_sample_rate_ : kFallbackSampleRate;
        const int64_t progress_ms = seek_base_ms_ + (delta_frames * 1000) / progress_sample_rate;
        if (!force &&
            last_notified_progress_ms_ >= 0 &&
            progress_ms >= last_notified_progress_ms_ &&
            (progress_ms - last_notified_progress_ms_) < kProgressNotifyIntervalMs) {
            return true;
        }

        env_->CallVoidMethod(callback_owner_, on_native_progress_mid_, static_cast<jlong>(progress_ms));
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            if (error_message != nullptr) {
                error_message->clear();
            }
            return true;
        }

        last_notified_progress_ms_ = progress_ms;
        return true;
    }

    bool EnsureWriteArrayBuffer(int size, std::string* error_message) {
        if (size <= 0) {
            return true;
        }

        if (write_array_buffer_ != nullptr) {
            const jsize current_capacity = env_->GetArrayLength(write_array_buffer_);
            if (current_capacity >= size) {
                return true;
            }
            env_->DeleteLocalRef(write_array_buffer_);
            write_array_buffer_ = nullptr;
        }

        write_array_buffer_ = env_->NewByteArray(size);
        if (write_array_buffer_ == nullptr) {
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
            }
            return Fail(error_message, "failed to allocate AudioTrack write buffer");
        }

        return true;
    }

    bool TryRecoverAudioTrackAfterWriteFailure(const char* stage, std::string* error_message) {
        if (audio_track_class_ == nullptr) {
            return false;
        }

        OutputCandidate candidate;
        candidate.sample_rate = output_sample_rate_ > 0 ? output_sample_rate_ : kFallbackSampleRate;
        candidate.channels = output_channel_count_ > 0 ? output_channel_count_ : 2;
        candidate.channel_config = ResolveChannelConfig(candidate.channels);
        if (candidate.channel_config == 0) {
            candidate.channels = 2;
            candidate.channel_config = kChannelConfigStereo;
        }
        candidate.encoding = output_encoding_;
        if (candidate.encoding != kEncodingPcm16Bit && candidate.encoding != kEncodingPcmFloat) {
            candidate.encoding = kEncodingPcm16Bit;
        }

        std::string recover_error;
        if (TryStartAudioTrack(candidate, &recover_error)) {
            has_write_head_probe_ = false;
            stagnant_write_cycles_ = 0;
            return true;
        }
        if (error_message != nullptr) {
            *error_message =
                    std::string(stage) + " and recover failed: " +
                    (recover_error.empty() ? "unknown" : recover_error);
        }
        return false;
    }

    bool TryRecoverForPlaybackHeadStall(std::string* error_message) {
        if (audio_track_ == nullptr || is_paused_ || ShouldStop()) {
            has_write_head_probe_ = false;
            stagnant_write_cycles_ = 0;
            return false;
        }

        uint64_t head_frames = 0;
        std::string ignored_error;
        if (!ReadPlaybackHeadFrames(&head_frames, &ignored_error)) {
            return false;
        }

        if (!has_write_head_probe_) {
            has_write_head_probe_ = true;
            last_write_head_frames_ = head_frames;
            stagnant_write_cycles_ = 0;
            return false;
        }

        if (head_frames > last_write_head_frames_) {
            last_write_head_frames_ = head_frames;
            stagnant_write_cycles_ = 0;
            return false;
        }

        stagnant_write_cycles_ += 1;
        if (stagnant_write_cycles_ < kPlaybackHeadStallWriteCycles) {
            return false;
        }

        stagnant_write_cycles_ = 0;
        has_write_head_probe_ = false;
        return TryRecoverAudioTrackAfterWriteFailure("AudioTrack playback head stalled", error_message);
    }

    bool Fail(std::string* error_message, const char* message) {
        if (error_message != nullptr) {
            *error_message = message;
        }
        return false;
    }

    void Shutdown() {
        ReleaseAudioTrackInstance();

        if (audio_track_class_ != nullptr) {
            env_->DeleteGlobalRef(audio_track_class_);
            audio_track_class_ = nullptr;
        }

        if (write_array_buffer_ != nullptr) {
            env_->DeleteLocalRef(write_array_buffer_);
            write_array_buffer_ = nullptr;
        }

        if (callback_owner_ != nullptr) {
            env_->DeleteGlobalRef(callback_owner_);
            callback_owner_ = nullptr;
        }
    }

    JNIEnv* env_ = nullptr;
    jobject callback_owner_local_ = nullptr;
    jobject callback_owner_ = nullptr;
    PlayerContext* context_ = nullptr;

    jclass audio_track_class_ = nullptr;
    jobject audio_track_ = nullptr;

    jmethodID get_min_buffer_size_mid_ = nullptr;
    jmethodID ctor_mid_ = nullptr;
    jmethodID play_mid_ = nullptr;
    jmethodID pause_mid_ = nullptr;
    jmethodID get_playback_head_position_mid_ = nullptr;
    jmethodID write_array_mid_ = nullptr;
    jmethodID write_direct_mid_ = nullptr;
    jmethodID stop_mid_ = nullptr;
    jmethodID flush_mid_ = nullptr;
    jmethodID release_mid_ = nullptr;
    jmethodID on_native_progress_mid_ = nullptr;
    jmethodID on_native_output_config_mid_ = nullptr;
    jbyteArray write_array_buffer_ = nullptr;

    bool has_playback_head_ = false;
    uint32_t last_playback_head_u32_ = 0;
    uint64_t playback_head_wrap_count_ = 0;

    bool has_pending_seek_base_ = false;
    int64_t pending_seek_base_ms_ = 0;
    int64_t seek_base_ms_ = 0;
    uint64_t seek_anchor_head_frames_ = 0;
    int64_t last_notified_progress_ms_ = -1;
    bool pending_audio_track_flush_ = false;
    bool has_write_head_probe_ = false;
    uint64_t last_write_head_frames_ = 0;
    int stagnant_write_cycles_ = 0;

    bool is_paused_ = false;
    int output_sample_rate_ = kFallbackSampleRate;
    int output_channel_count_ = 2;
    jint output_encoding_ = kEncodingPcm16Bit;
};

int RunPlaybackInternal(JNIEnv* env, jobject thiz, PlayerContext* context, IPlaySource* source) {
    if (context == nullptr) {
        return kContextUnavailableCode;
    }

    int expected = 0;
    // 单实例同一时刻仅允许一个活跃播放流程。
    if (!context->active_playbacks.compare_exchange_strong(
                expected,
                1,
                std::memory_order_relaxed,
                std::memory_order_relaxed)) {
        SetLastError(context, "playback already running on this player instance");
        return kAlreadyPlayingCode;
    }

    context->stop_requested.store(false, std::memory_order_relaxed);
    context->pause_requested.store(false, std::memory_order_relaxed);
    context->seek_position_ms.store(-1, std::memory_order_relaxed);

    struct PlaybackGuard {
        JNIEnv* env = nullptr;
        jobject thiz = nullptr;
        PlayerContext* context = nullptr;

        ~PlaybackGuard() {
            if (context == nullptr) {
                return;
            }
            const int active = context->active_playbacks.fetch_sub(1, std::memory_order_relaxed) - 1;
            if (active == 0 && context->release_requested.load(std::memory_order_relaxed)) {
                TryReleaseContext(env, thiz, context);
            }
        }
    } guard{env, thiz, context};

    // 组装播放核心：FFmpeg 播放器 + PCM 消费器（AudioTrack）。
    std::unique_ptr<INativePlayer> player_core = std::make_unique<FfmpegPlayer>();
    AudioTrackConsumer consumer(env, thiz, context);
    std::string decoder_error;

    if (!consumer.Init(&decoder_error)) {
        SetLastError(context, decoder_error.empty() ? "failed to init AudioTrack" : decoder_error);
        return kAudioTrackInitCode;
    }

    const int result = player_core->Play(source, &consumer, &decoder_error);

    if (decoder_error.empty()) {
        SetLastError(context, result == 0 ? "ok" : "playback failed");
    } else {
        SetLastError(context, decoder_error);
    }
    return result;
}

void FlushActiveAudioTrackForSeek(JNIEnv* env, PlayerContext* context) {
    if (env == nullptr || context == nullptr) {
        return;
    }

    jobject local_audio_track = nullptr;
    {
        std::lock_guard<std::mutex> lock(context->audio_track_mutex);
        if (context->active_audio_track != nullptr) {
            local_audio_track = env->NewLocalRef(context->active_audio_track);
        }
    }
    if (local_audio_track == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return;
    }

    jclass audio_track_class = env->GetObjectClass(local_audio_track);
    if (audio_track_class == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(local_audio_track);
        return;
    }

    jmethodID pause_mid = env->GetMethodID(audio_track_class, "pause", "()V");
    jmethodID flush_mid = env->GetMethodID(audio_track_class, "flush", "()V");
    jmethodID play_mid = env->GetMethodID(audio_track_class, "play", "()V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    if (pause_mid != nullptr) {
        env->CallVoidMethod(local_audio_track, pause_mid);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    if (flush_mid != nullptr) {
        env->CallVoidMethod(local_audio_track, flush_mid);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    const bool should_pause = context->pause_requested.load(std::memory_order_relaxed);
    const bool should_stop = context->stop_requested.load(std::memory_order_relaxed);
    if (!should_pause && !should_stop && play_mid != nullptr) {
        env->CallVoidMethod(local_audio_track, play_mid);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    env->DeleteLocalRef(audio_track_class);
    env->DeleteLocalRef(local_audio_track);
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativePlayFromSource(
        JNIEnv* env,
        jobject thiz,
        jobject source_object) {
    // JNI 播放入口：构建 Source 桥接，进入统一播放流程。
    PlayerContext* context = GetOrCreateContext(env, thiz);
    if (context == nullptr) {
        return kContextUnavailableCode;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    std::string source_error;
    JniPlaySource source(env, source_object);
    if (!source.Init(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to init play source" : source_error);
        return -4;
    }
    if (!source.Open(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to open play source" : source_error);
        return -5;
    }

    const int result = RunPlaybackInternal(env, thiz, context, &source);
    source.Close();
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeStop(JNIEnv* env, jobject thiz) {
    // 控制接口：请求停止，并唤醒可能处于 pause wait 的线程。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    context->stop_requested.store(true, std::memory_order_relaxed);
    context->pause_requested.store(false, std::memory_order_relaxed);
    context->pause_cv.notify_all();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativePause(JNIEnv* env, jobject thiz) {
    // 控制接口：仅设置 pause 标记，实际暂停由消费循环处理。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return kContextUnavailableCode;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    context->pause_requested.store(true, std::memory_order_relaxed);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeResume(JNIEnv* env, jobject thiz) {
    // 控制接口：清除 pause 标记并通知等待线程继续。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return kContextUnavailableCode;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    context->pause_requested.store(false, std::memory_order_relaxed);
    context->pause_cv.notify_all();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeSeek(
        JNIEnv* env,
        jobject thiz,
        jlong position_ms) {
    // 控制接口：写入一次性 seek 请求，由解码循环轮询消费。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return kContextUnavailableCode;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    if (position_ms < 0) {
        SetLastError(context, "seek position must be >= 0");
        return -1;
    }

    if (context->active_playbacks.load(std::memory_order_relaxed) == 0) {
        // 仅在播放循环活跃时允许 seek。
        SetLastError(context, "seek is available only while playback is active");
        return kSeekUnavailableCode;
    }

    context->seek_position_ms.store(static_cast<int64_t>(position_ms), std::memory_order_relaxed);
    FlushActiveAudioTrackForSeek(env, context);
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeGetDurationFromSource(
        JNIEnv* env,
        jobject thiz,
        jobject source_object) {
    // 查询接口：仅打开 source 读取时长，不进入播放循环。
    PlayerContext* context = GetOrCreateContext(env, thiz);
    if (context == nullptr) {
        return kContextUnavailableCode;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    std::string source_error;
    JniPlaySource source(env, source_object);
    if (!source.Init(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to init play source" : source_error);
        return -4;
    }
    if (!source.Open(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to open play source" : source_error);
        return -5;
    }

    std::string decoder_error;
    std::unique_ptr<INativePlayer> player_core = std::make_unique<FfmpegPlayer>();
    const int64_t duration_ms = player_core->GetDurationMs(&source, &decoder_error);
    source.Close();

    if (decoder_error.empty()) {
        SetLastError(context, duration_ms >= 0 ? "ok" : "duration query failed");
    } else {
        SetLastError(context, decoder_error);
    }

    return static_cast<jlong>(duration_ms);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeGetAudioMetadataFromSource(
        JNIEnv* env,
        jobject thiz,
        jobject source_object) {
    // 查询接口：读取音频元信息并映射为 Kotlin AudioMeta 对象。
    PlayerContext* context = GetOrCreateContext(env, thiz);
    if (context == nullptr) {
        return nullptr;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    std::string source_error;
    JniPlaySource source(env, source_object);
    if (!source.Init(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to init play source" : source_error);
        return nullptr;
    }
    if (!source.Open(&source_error)) {
        SetLastError(context, source_error.empty() ? "failed to open play source" : source_error);
        return nullptr;
    }

    std::unique_ptr<INativePlayer> player_core = std::make_unique<FfmpegPlayer>();
    AudioMetadata metadata;
    std::string decoder_error;
    const int result = player_core->GetAudioMetadata(&source, &metadata, &decoder_error);
    source.Close();
    if (result != 0) {
        SetLastError(context, decoder_error.empty() ? "audio metadata query failed" : decoder_error);
        return nullptr;
    }

    jobject metadata_object = BuildAudioMetaObject(env, metadata);
    if (metadata_object == nullptr) {
        SetLastError(context, "failed to create AudioMeta object");
        return nullptr;
    }

    SetLastError(context, "ok");
    return metadata_object;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeGetPlaybackState(
        JNIEnv* env,
        jobject thiz) {
    // 查询接口：从当前活跃 AudioTrack 读取 playState。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return -1;
    }
    ScopedContextUse scoped_context(env, thiz, context);

    jobject local_audio_track = nullptr;
    {
        std::lock_guard<std::mutex> lock(context->audio_track_mutex);
        if (context->active_audio_track != nullptr) {
            local_audio_track = env->NewLocalRef(context->active_audio_track);
        }
    }

    if (local_audio_track == nullptr) {
        return -1;
    }

    jclass audio_track_class = env->GetObjectClass(local_audio_track);
    if (audio_track_class == nullptr) {
        env->DeleteLocalRef(local_audio_track);
        SetLastError(context, "failed to resolve AudioTrack class");
        return -2;
    }

    jmethodID get_play_state_mid = env->GetMethodID(audio_track_class, "getPlayState", "()I");
    if (get_play_state_mid == nullptr) {
        env->DeleteLocalRef(audio_track_class);
        env->DeleteLocalRef(local_audio_track);
        SetLastError(context, "failed to resolve AudioTrack.getPlayState");
        return -3;
    }

    const jint play_state = env->CallIntMethod(local_audio_track, get_play_state_mid);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(audio_track_class);
        env->DeleteLocalRef(local_audio_track);
        SetLastError(context, "AudioTrack.getPlayState threw exception");
        return -4;
    }

    env->DeleteLocalRef(audio_track_class);
    env->DeleteLocalRef(local_audio_track);
    return play_state;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeRelease(JNIEnv* env, jobject thiz) {
    // 释放接口：打停止标记 + release 标记，触发延迟释放流程。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    if (context == nullptr) {
        return;
    }

    context->stop_requested.store(true, std::memory_order_relaxed);
    context->pause_requested.store(false, std::memory_order_relaxed);
    context->pause_cv.notify_all();
    context->release_requested.store(true, std::memory_order_relaxed);
    TryReleaseContext(env, thiz, context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeLastError(JNIEnv* env, jobject thiz) {
    // 查询接口：返回最近一次 native 错误文本。
    PlayerContext* context = GetContextNoCreate(env, thiz);
    ScopedContextUse scoped_context(env, thiz, context);
    const std::string message = GetLastError(context);
    return env->NewStringUTF(message.c_str());
}
