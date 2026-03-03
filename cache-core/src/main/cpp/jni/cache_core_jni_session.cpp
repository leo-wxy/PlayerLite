#include "cache_core_jni_shared.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeOpenSession(
        JNIEnv* env,
        jclass,
        jstring resource_key,
        jint block_size_bytes,
        jlong content_length_hint,
        jlong duration_ms_hint,
        jlong provider_handle) {
    if (env == nullptr || resource_key == nullptr || provider_handle <= 0 || block_size_bytes <= 0) {
        return static_cast<jlong>(-1);
    }
    const char* raw_key = env->GetStringUTFChars(resource_key, nullptr);
    if (raw_key == nullptr) {
        return static_cast<jlong>(-1);
    }
    const std::string key(raw_key);
    env->ReleaseStringUTFChars(resource_key, raw_key);

    return static_cast<jlong>(cachecore::jni::Runtime().OpenSession(
            key,
            static_cast<int32_t>(block_size_bytes),
            static_cast<int64_t>(content_length_hint),
            static_cast<int64_t>(duration_ms_hint),
            static_cast<int64_t>(provider_handle)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeRead(
        JNIEnv* env,
        jclass,
        jlong session_id,
        jint size) {
    if (env == nullptr || session_id <= 0 || size <= 0) {
        return nullptr;
    }
    const auto bytes = cachecore::jni::Runtime().Read(
            static_cast<int64_t>(session_id),
            static_cast<int32_t>(size));
    return cachecore::jni::ToByteArray(env, bytes);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeReadAt(
        JNIEnv* env,
        jclass,
        jlong session_id,
        jlong offset,
        jint size) {
    if (env == nullptr || session_id <= 0 || offset < 0 || size <= 0) {
        return nullptr;
    }
    const auto bytes = cachecore::jni::Runtime().ReadAt(
            static_cast<int64_t>(session_id),
            static_cast<int64_t>(offset),
            static_cast<int32_t>(size));
    return cachecore::jni::ToByteArray(env, bytes);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeSeek(
        JNIEnv*,
        jclass,
        jlong session_id,
        jlong offset,
        jint whence) {
    if (session_id <= 0) {
        return static_cast<jlong>(-1);
    }
    return static_cast<jlong>(cachecore::jni::Runtime().Seek(
            static_cast<int64_t>(session_id),
            static_cast<int64_t>(offset),
            static_cast<int32_t>(whence)));
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeCancelPendingRead(
        JNIEnv*,
        jclass,
        jlong session_id) {
    if (session_id <= 0) {
        return;
    }
    cachecore::jni::Runtime().CancelPendingRead(static_cast<int64_t>(session_id));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeCloseSession(
        JNIEnv*,
        jclass,
        jlong session_id) {
    if (session_id <= 0) {
        return JNI_FALSE;
    }
    return cachecore::jni::Runtime().CloseSession(static_cast<int64_t>(session_id)) ? JNI_TRUE : JNI_FALSE;
}
