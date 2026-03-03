#include "cache_core_jni_shared.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeClearAll(
        JNIEnv*,
        jclass) {
    return cachecore::jni::Runtime().ClearAll() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeReleaseProviderHandle(
        JNIEnv*,
        jclass,
        jlong provider_handle) {
    if (provider_handle <= 0) {
        return;
    }
    cachecore::jni::Runtime().ReleaseProviderHandle(static_cast<int64_t>(provider_handle));
}

