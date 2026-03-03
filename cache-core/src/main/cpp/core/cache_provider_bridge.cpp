#include "cache_provider_bridge.h"

namespace cachecore {

void JniProviderBridge::SetJavaVm(JavaVM* vm) {
    std::lock_guard<std::mutex> lock(mutex_);
    jvm_ = vm;
}

std::vector<uint8_t> JniProviderBridge::ReadAt(int64_t provider_handle, int64_t offset, int32_t size) {
    if (provider_handle <= 0 || size <= 0) {
        return {};
    }
    bool attached = false;
    JNIEnv* env = AttachThread(&attached);
    if (env == nullptr) {
        return {};
    }
    if (!EnsureResolved(env)) {
        DetachThread(attached);
        return {};
    }

    auto* byte_array = static_cast<jbyteArray>(
            env->CallStaticObjectMethod(
                    bridge_class_,
                    read_at_mid_,
                    static_cast<jlong>(provider_handle),
                    static_cast<jlong>(offset),
                    static_cast<jint>(size)));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        DetachThread(attached);
        return {};
    }
    if (byte_array == nullptr) {
        DetachThread(attached);
        return {};
    }

    const auto length = env->GetArrayLength(byte_array);
    if (length <= 0) {
        env->DeleteLocalRef(byte_array);
        DetachThread(attached);
        return {};
    }

    std::vector<uint8_t> output(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(byte_array, 0, length, reinterpret_cast<jbyte*>(output.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        output.clear();
    }
    env->DeleteLocalRef(byte_array);
    DetachThread(attached);
    return output;
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

bool JniProviderBridge::EnsureResolved(JNIEnv* env) {
    if (env == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (bridge_class_ != nullptr &&
        read_at_mid_ != nullptr &&
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
    jmethodID cancel_mid = env->GetStaticMethodID(global_class, "providerCancelInFlightRead", "(J)V");
    jmethodID query_mid = env->GetStaticMethodID(global_class, "providerQueryContentLength", "(J)J");
    jmethodID close_mid = env->GetStaticMethodID(global_class, "providerClose", "(J)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteGlobalRef(global_class);
        return false;
    }
    if (read_mid == nullptr || cancel_mid == nullptr || query_mid == nullptr || close_mid == nullptr) {
        env->DeleteGlobalRef(global_class);
        return false;
    }

    if (bridge_class_ != nullptr) {
        env->DeleteGlobalRef(bridge_class_);
    }
    bridge_class_ = global_class;
    read_at_mid_ = read_mid;
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

