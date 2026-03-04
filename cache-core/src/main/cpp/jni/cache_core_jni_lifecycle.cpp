#include "cache_core_jni_shared.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeIsAvailable(
        JNIEnv*,
        jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeInit(
        JNIEnv* env,
        jclass,
        jstring cache_root_path,
        jlong memory_cache_cap_bytes,
        jlong disk_cache_max_bytes,
        jdouble disk_cache_clean_range_min,
        jdouble disk_cache_clean_range_max,
        jint read_retry_count) {
    if (env == nullptr || cache_root_path == nullptr || memory_cache_cap_bytes <= 0 ||
        disk_cache_max_bytes <= 0 || read_retry_count < 0) {
        return -1;
    }
    const char* raw_path = env->GetStringUTFChars(cache_root_path, nullptr);
    if (raw_path == nullptr) {
        return -1;
    }
    const std::string path(raw_path);
    env->ReleaseStringUTFChars(cache_root_path, raw_path);

    const bool ok = cachecore::jni::Runtime().Init(
            path,
            static_cast<int64_t>(memory_cache_cap_bytes),
            static_cast<int64_t>(disk_cache_max_bytes),
            static_cast<double>(disk_cache_clean_range_min),
            static_cast<double>(disk_cache_clean_range_max),
            static_cast<int32_t>(read_retry_count));
    return ok ? 0 : -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeShutdown(
        JNIEnv*,
        jclass) {
    cachecore::jni::Runtime().Shutdown();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeIsInitialized(
        JNIEnv*,
        jclass) {
    return cachecore::jni::Runtime().IsInitialized() ? JNI_TRUE : JNI_FALSE;
}
