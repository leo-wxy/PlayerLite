#include "cache_provider_bridge.h"

#include <algorithm>
#include <chrono>

namespace cachecore {

void JniProviderBridge::SetJavaVm(JavaVM* vm) {
    std::lock_guard<std::mutex> lock(mutex_);
    jvm_ = vm;
}

std::vector<uint8_t> JniProviderBridge::ReadAt(int64_t provider_handle, int64_t offset, int32_t size) {
    std::vector<uint8_t> output;
    std::mutex output_mutex;
    StreamCallbacks callbacks;
    callbacks.on_data = [&output, &output_mutex](int64_t, const std::vector<uint8_t>& bytes) {
        if (bytes.empty()) {
            return true;
        }
        std::lock_guard<std::mutex> lock(output_mutex);
        output.insert(output.end(), bytes.begin(), bytes.end());
        return true;
    };
    if (!ReadAtStream(provider_handle, offset, size, callbacks)) {
        return {};
    }
    return output;
}

bool JniProviderBridge::ReadAtStream(
        int64_t provider_handle,
        int64_t offset,
        int32_t size,
        const StreamCallbacks& callbacks) {
    if (provider_handle <= 0 || offset < 0 || size <= 0) {
        return false;
    }

    const int64_t request_id = CreateStreamState(offset, size, &callbacks, false);

    bool attached = false;
    JNIEnv* env = AttachThread(&attached);
    if (env == nullptr) {
        return WaitAndConsumeStream(request_id, false, nullptr);
    }
    if (!EnsureResolved(env)) {
        DetachThread(attached);
        return WaitAndConsumeStream(request_id, false, nullptr);
    }

    const auto call_ok = static_cast<bool>(env->CallStaticBooleanMethod(
            bridge_class_,
            read_at_stream_mid_,
            static_cast<jlong>(provider_handle),
            static_cast<jlong>(offset),
            static_cast<jint>(size),
            static_cast<jlong>(request_id)));
    bool no_exception = true;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        no_exception = false;
    }
    DetachThread(attached);

    return WaitAndConsumeStream(request_id, call_ok && no_exception, nullptr);
}

void JniProviderBridge::CancelInFlightRead(int64_t provider_handle) {
    if (provider_handle <= 0) {
        return;
    }
    bool attached = false;
    JNIEnv* env = AttachThread(&attached);
    if (env == nullptr) {
        return;
    }
    if (!EnsureResolved(env)) {
        DetachThread(attached);
        return;
    }
    env->CallStaticVoidMethod(
            bridge_class_,
            cancel_mid_,
            static_cast<jlong>(provider_handle));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    DetachThread(attached);
}

int64_t JniProviderBridge::QueryContentLength(int64_t provider_handle) {
    if (provider_handle <= 0) {
        return -1;
    }
    bool attached = false;
    JNIEnv* env = AttachThread(&attached);
    if (env == nullptr) {
        return -1;
    }
    if (!EnsureResolved(env)) {
        DetachThread(attached);
        return -1;
    }
    const auto result = static_cast<int64_t>(env->CallStaticLongMethod(
            bridge_class_,
            query_mid_,
            static_cast<jlong>(provider_handle)));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        DetachThread(attached);
        return -1;
    }
    DetachThread(attached);
    return result;
}

void JniProviderBridge::Close(int64_t provider_handle) {
    if (provider_handle <= 0) {
        return;
    }
    bool attached = false;
    JNIEnv* env = AttachThread(&attached);
    if (env == nullptr) {
        return;
    }
    if (!EnsureResolved(env)) {
        DetachThread(attached);
        return;
    }
    env->CallStaticVoidMethod(
            bridge_class_,
            close_mid_,
            static_cast<jlong>(provider_handle));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    DetachThread(attached);
}

void JniProviderBridge::OnStreamBegin(int64_t request_id, int64_t offset, int32_t requested_size) {
    auto state = GetStreamState(request_id);
    if (state == nullptr) {
        return;
    }
    std::function<void(int64_t, int32_t)> on_begin;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->completed) {
            return;
        }
        state->offset = offset;
        state->requested_size = requested_size;
        state->next_chunk_offset = offset;
        on_begin = state->callbacks.on_begin;
        if (state->collect_bytes && requested_size > 0) {
            state->bytes.reserve(static_cast<std::size_t>(requested_size));
        }
    }
    if (on_begin) {
        on_begin(offset, requested_size);
    }
}

bool JniProviderBridge::OnStreamData(int64_t request_id, const std::vector<uint8_t>& bytes) {
    if (bytes.empty()) {
        return true;
    }
    auto state = GetStreamState(request_id);
    if (state == nullptr) {
        return false;
    }
    int64_t chunk_offset = 0;
    bool collect_bytes = false;
    std::function<bool(int64_t, const std::vector<uint8_t>&)> on_data;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->completed) {
            return false;
        }
        chunk_offset = state->next_chunk_offset;
        state->next_chunk_offset += static_cast<int64_t>(bytes.size());
        collect_bytes = state->collect_bytes;
        on_data = state->callbacks.on_data;
        if (collect_bytes) {
            state->bytes.insert(state->bytes.end(), bytes.begin(), bytes.end());
        }
    }

    bool accepted = true;
    if (on_data) {
        accepted = on_data(chunk_offset, bytes);
    }
    if (accepted) {
        return true;
    }

    std::function<void(bool)> on_end;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->completed) {
            return false;
        }
        state->success = false;
        state->completed = true;
        state->end_notified = true;
        on_end = state->callbacks.on_end;
    }
    if (on_end) {
        on_end(false);
    }
    state->cv.notify_all();
    return false;
}

void JniProviderBridge::OnStreamEnd(int64_t request_id, bool success) {
    auto state = GetStreamState(request_id);
    if (state == nullptr) {
        return;
    }
    std::function<void(bool)> on_end;
    bool final_success = false;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        if (state->completed) {
            return;
        }
        state->completed = true;
        state->success = success;
        state->end_notified = true;
        final_success = state->success;
        on_end = state->callbacks.on_end;
    }
    if (on_end) {
        on_end(final_success);
    }
    state->cv.notify_all();
}

int64_t JniProviderBridge::CreateStreamState(
        int64_t offset,
        int32_t requested_size,
        const StreamCallbacks* callbacks,
        bool collect_bytes) {
    std::lock_guard<std::mutex> lock(stream_mutex_);
    const int64_t request_id = next_stream_request_id_++;
    auto state = std::make_shared<StreamState>();
    state->success = true;
    state->offset = offset;
    state->requested_size = requested_size;
    state->next_chunk_offset = offset;
    state->collect_bytes = collect_bytes;
    if (callbacks != nullptr) {
        state->callbacks = *callbacks;
    }
    if (collect_bytes && requested_size > 0) {
        state->bytes.reserve(static_cast<std::size_t>(requested_size));
    }
    stream_states_[request_id] = std::move(state);
    return request_id;
}

std::shared_ptr<JniProviderBridge::StreamState> JniProviderBridge::GetStreamState(int64_t request_id) {
    std::lock_guard<std::mutex> lock(stream_mutex_);
    const auto found = stream_states_.find(request_id);
    if (found == stream_states_.end()) {
        return nullptr;
    }
    return found->second;
}

bool JniProviderBridge::WaitAndConsumeStream(int64_t request_id, bool call_ok, std::vector<uint8_t>* out_bytes) {
    std::shared_ptr<StreamState> state;
    {
        std::lock_guard<std::mutex> lock(stream_mutex_);
        const auto found = stream_states_.find(request_id);
        if (found == stream_states_.end()) {
            return false;
        }
        state = found->second;
    }

    std::function<void(bool)> on_end;
    bool should_call_on_end = false;
    bool success = false;
    {
        std::unique_lock<std::mutex> lock(state->mutex);
        if (!state->completed && call_ok) {
            state->cv.wait_for(lock, std::chrono::seconds(30), [state]() { return state->completed; });
        }
        if (!state->completed) {
            state->completed = true;
            state->success = false;
        }
        success = call_ok && state->success;
        if (!state->end_notified) {
            state->end_notified = true;
            on_end = state->callbacks.on_end;
            should_call_on_end = static_cast<bool>(on_end);
        }
        if (out_bytes != nullptr && success && state->collect_bytes) {
            *out_bytes = state->bytes;
        }
    }
    if (should_call_on_end) {
        on_end(success);
    }

    std::lock_guard<std::mutex> cleanup_lock(stream_mutex_);
    stream_states_.erase(request_id);
    return success;
}

bool JniProviderBridge::EnsureResolved(JNIEnv* env) {
    if (env == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (bridge_class_ != nullptr &&
        read_at_mid_ != nullptr &&
        read_at_stream_mid_ != nullptr &&
        cancel_mid_ != nullptr &&
        query_mid_ != nullptr &&
        close_mid_ != nullptr) {
        return true;
    }

    auto* local_class = env->FindClass("com/wxy/playerlite/cache/core/CacheCoreNativeBridge");
    if (local_class == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return false;
    }
    auto* global_class = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
    env->DeleteLocalRef(local_class);
    if (global_class == nullptr) {
        return false;
    }

    jmethodID read_mid = env->GetStaticMethodID(global_class, "providerReadAt", "(JJI)[B");
    jmethodID read_stream_mid = env->GetStaticMethodID(global_class, "providerReadAtStream", "(JJIJ)Z");
    jmethodID cancel_mid = env->GetStaticMethodID(global_class, "providerCancelInFlightRead", "(J)V");
    jmethodID query_mid = env->GetStaticMethodID(global_class, "providerQueryContentLength", "(J)J");
    jmethodID close_mid = env->GetStaticMethodID(global_class, "providerClose", "(J)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteGlobalRef(global_class);
        return false;
    }
    if (read_mid == nullptr || read_stream_mid == nullptr || cancel_mid == nullptr ||
        query_mid == nullptr || close_mid == nullptr) {
        env->DeleteGlobalRef(global_class);
        return false;
    }

    if (bridge_class_ != nullptr) {
        env->DeleteGlobalRef(bridge_class_);
    }
    bridge_class_ = global_class;
    read_at_mid_ = read_mid;
    read_at_stream_mid_ = read_stream_mid;
    cancel_mid_ = cancel_mid;
    query_mid_ = query_mid;
    close_mid_ = close_mid;
    return true;
}

JNIEnv* JniProviderBridge::AttachThread(bool* attached) {
    if (attached != nullptr) {
        *attached = false;
    }

    JavaVM* vm = nullptr;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        vm = jvm_;
    }
    if (vm == nullptr) {
        return nullptr;
    }

    JNIEnv* env = nullptr;
    const jint get_env = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (get_env == JNI_OK && env != nullptr) {
        return env;
    }
    if (get_env != JNI_EDETACHED) {
        return nullptr;
    }

    if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK || env == nullptr) {
        return nullptr;
    }
    if (attached != nullptr) {
        *attached = true;
    }
    return env;
}

void JniProviderBridge::DetachThread(bool attached) {
    if (!attached) {
        return;
    }
    JavaVM* vm = nullptr;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        vm = jvm_;
    }
    if (vm != nullptr) {
        vm->DetachCurrentThread();
    }
}

}  // namespace cachecore
