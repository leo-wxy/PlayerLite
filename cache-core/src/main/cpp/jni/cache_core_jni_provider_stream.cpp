#include "cache_core_jni_shared.h"

#include <algorithm>
#include <vector>

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeProviderOnDataBegin(
        JNIEnv*,
        jclass,
        jlong request_id,
        jlong offset,
        jint requested_size) {
    if (request_id <= 0) {
        return;
    }
    cachecore::jni::ProviderBridge().OnStreamBegin(
            static_cast<int64_t>(request_id),
            static_cast<int64_t>(offset),
            static_cast<int32_t>(requested_size));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeProviderOnDataSend(
        JNIEnv* env,
        jclass,
        jlong request_id,
        jbyteArray data,
        jint length) {
    if (env == nullptr || request_id <= 0 || data == nullptr || length <= 0) {
        return JNI_FALSE;
    }

    const auto data_len = static_cast<int32_t>(env->GetArrayLength(data));
    if (data_len <= 0) {
        return JNI_FALSE;
    }
    const int32_t copy_len = std::min<int32_t>(data_len, static_cast<int32_t>(length));
    if (copy_len <= 0) {
        return JNI_FALSE;
    }

    std::vector<uint8_t> chunk(static_cast<std::size_t>(copy_len));
    env->GetByteArrayRegion(
            data,
            0,
            static_cast<jsize>(copy_len),
            reinterpret_cast<jbyte*>(chunk.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    return cachecore::jni::ProviderBridge().OnStreamData(
            static_cast<int64_t>(request_id),
            chunk) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeProviderOnDataEnd(
        JNIEnv*,
        jclass,
        jlong request_id,
        jboolean success) {
    if (request_id <= 0) {
        return;
    }
    cachecore::jni::ProviderBridge().OnStreamEnd(
            static_cast<int64_t>(request_id),
            success == JNI_TRUE);
}
