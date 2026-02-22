#include <jni.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>

#include "ffmpeg_player.h"
#include "jni_play_source.h"

namespace {
constexpr jint kSampleRate = 44100;
constexpr jint kChannelConfigStereo = 12;  // AudioFormat.CHANNEL_OUT_STEREO
constexpr jint kEncodingPcm16Bit = 2;      // AudioFormat.ENCODING_PCM_16BIT
constexpr jint kStreamMusic = 3;           // AudioManager.STREAM_MUSIC
constexpr jint kModeStream = 1;            // AudioTrack.MODE_STREAM
constexpr jint kWriteBlocking = 0;         // AudioTrack.WRITE_BLOCKING
constexpr jint kStoppedCode = -2001;
constexpr jint kAudioTrackInitCode = -3001;
constexpr jint kAlreadyPlayingCode = -2005;
constexpr jint kSeekUnavailableCode = -2006;
constexpr jint kContextUnavailableCode = -6;
constexpr int64_t kProgressNotifyIntervalMs = 500;

struct PlayerContext {
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

jfieldID ResolveNativeContextField(JNIEnv* env, jobject thiz) {
    if (env == nullptr || thiz == nullptr) {
        return nullptr;
    }

    jfieldID cached = g_native_context_field.load(std::memory_order_acquire);
    if (cached != nullptr) {
        return cached;
    }

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

    if (context->active_calls.load(std::memory_order_relaxed) != 0) {
        return;
    }

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
        env_->DeleteLocalRef(callback_owner_class);

        if (get_min_buffer_size_mid_ == nullptr || ctor_mid_ == nullptr || play_mid_ == nullptr ||
            pause_mid_ == nullptr ||
            get_playback_head_position_mid_ == nullptr ||
            write_array_mid_ == nullptr || stop_mid_ == nullptr || flush_mid_ == nullptr ||
            release_mid_ == nullptr || on_native_progress_mid_ == nullptr) {
            return Fail(error_message, "failed to resolve AudioTrack methods");
        }

        jint min_buffer_size = env_->CallStaticIntMethod(
                audio_track_class_,
                get_min_buffer_size_mid_,
                kSampleRate,
                kChannelConfigStereo,
                kEncodingPcm16Bit);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return Fail(error_message, "AudioTrack.getMinBufferSize threw exception");
        }

        const jint buffer_size = min_buffer_size > 0 ? min_buffer_size * 2 : 8192;
        jobject local_audio_track = env_->NewObject(
                audio_track_class_,
                ctor_mid_,
                kStreamMusic,
                kSampleRate,
                kChannelConfigStereo,
                kEncodingPcm16Bit,
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
            return Fail(error_message, "AudioTrack.play threw exception");
        }

        {
            if (context_ != nullptr) {
                std::lock_guard<std::mutex> lock(context_->audio_track_mutex);
                context_->active_audio_track = audio_track_;
            }
        }

        NotifyProgressIfNeeded(true, nullptr);

        return true;
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

        int offset = 0;
        while (offset < size) {
            if (!HandlePauseState(error_message)) {
                return false;
            }

            if (ShouldStop()) {
                return Fail(error_message, "stopped");
            }

            jint written = 0;
            if (write_direct_mid_ != nullptr) {
                jobject chunk_buffer = env_->NewDirectByteBuffer(
                        const_cast<uint8_t*>(data) + offset,
                        static_cast<jlong>(size - offset));
                if (chunk_buffer != nullptr) {
                    written = env_->CallIntMethod(
                            audio_track_,
                            write_direct_mid_,
                            chunk_buffer,
                            static_cast<jint>(size - offset),
                            kWriteBlocking);
                    env_->DeleteLocalRef(chunk_buffer);

                    if (!env_->ExceptionCheck()) {
                        if (written <= 0) {
                            return Fail(error_message, "AudioTrack.write(ByteBuffer) failed");
                        }
                        offset += written;
                        continue;
                    }

                    env_->ExceptionClear();
                } else if (env_->ExceptionCheck()) {
                    env_->ExceptionClear();
                }
            }

            if (!EnsureWriteArrayBuffer(size - offset, error_message)) {
                return false;
            }

            env_->SetByteArrayRegion(
                    write_array_buffer_,
                    0,
                    size - offset,
                    reinterpret_cast<const jbyte*>(data + offset));
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack write buffer conversion failed");
            }

            written = env_->CallIntMethod(audio_track_, write_array_mid_, write_array_buffer_, 0, size - offset);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                return Fail(error_message, "AudioTrack.write(byte[]) threw exception");
            }
            if (written <= 0) {
                return Fail(error_message, "AudioTrack.write(byte[]) failed");
            }

            offset += written;
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
            pending_seek_base_ms_ = seek_ms;
            has_pending_seek_base_ = true;
            pending_audio_track_flush_ = true;
        }
        return seek_ms;
    }

private:
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

        const int64_t progress_ms = seek_base_ms_ + (delta_frames * 1000) / kSampleRate;
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

    bool Fail(std::string* error_message, const char* message) {
        if (error_message != nullptr) {
            *error_message = message;
        }
        return false;
    }

    void Shutdown() {
        if (audio_track_ != nullptr) {
            {
                if (context_ != nullptr) {
                    std::lock_guard<std::mutex> lock(context_->audio_track_mutex);
                    if (context_->active_audio_track == audio_track_) {
                        context_->active_audio_track = nullptr;
                    }
                }
            }

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

    bool is_paused_ = false;
};

int RunPlaybackInternal(JNIEnv* env, jobject thiz, PlayerContext* context, IPlaySource* source) {
    if (context == nullptr) {
        return kContextUnavailableCode;
    }

    int expected = 0;
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
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativePlayFromSource(
        JNIEnv* env,
        jobject thiz,
        jobject source_object) {
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
        SetLastError(context, "seek is available only while playback is active");
        return kSeekUnavailableCode;
    }

    context->seek_position_ms.store(static_cast<int64_t>(position_ms), std::memory_order_relaxed);
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxy_playerlite_player_NativePlayer_nativeGetDurationFromSource(
        JNIEnv* env,
        jobject thiz,
        jobject source_object) {
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
    PlayerContext* context = GetContextNoCreate(env, thiz);
    ScopedContextUse scoped_context(env, thiz, context);
    const std::string message = GetLastError(context);
    return env->NewStringUTF(message.c_str());
}
