#include <jni.h>

#include <string>

#include "core/cache_runtime.h"

namespace {
cachecore::CacheRuntime g_runtime;
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeIsAvailable(
        JNIEnv* env,
        jclass clazz) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeInit(
        JNIEnv* env,
        jclass clazz,
        jstring cache_root_path) {
    if (cache_root_path == nullptr) {
        return -1;
    }
    const char* raw_path = env->GetStringUTFChars(cache_root_path, nullptr);
    if (raw_path == nullptr) {
        return -1;
    }
    const std::string path(raw_path);
    env->ReleaseStringUTFChars(cache_root_path, raw_path);

    return g_runtime.Init(path) ? 0 : -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeShutdown(
        JNIEnv* env,
        jclass clazz) {
    g_runtime.Shutdown();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeIsInitialized(
        JNIEnv* env,
        jclass clazz) {
    return g_runtime.IsInitialized() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeOpenSession(
        JNIEnv* env,
        jclass clazz,
        jstring resource_key) {
    if (resource_key == nullptr) {
        return static_cast<jlong>(-1);
    }
    const char* raw_key = env->GetStringUTFChars(resource_key, nullptr);
    if (raw_key == nullptr) {
        return static_cast<jlong>(-1);
    }
    const std::string key(raw_key);
    env->ReleaseStringUTFChars(resource_key, raw_key);

    return static_cast<jlong>(g_runtime.OpenSession(key));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeCloseSession(
        JNIEnv* env,
        jclass clazz,
        jlong session_id) {
    return g_runtime.CloseSession(static_cast<int64_t>(session_id)) ? JNI_TRUE : JNI_FALSE;
}
