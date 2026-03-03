#include "cache_core_jni_shared.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeLookup(
        JNIEnv* env,
        jclass,
        jstring resource_key) {
    if (env == nullptr || resource_key == nullptr) {
        return nullptr;
    }
    const char* raw_key = env->GetStringUTFChars(resource_key, nullptr);
    if (raw_key == nullptr) {
        return nullptr;
    }
    const std::string key(raw_key);
    env->ReleaseStringUTFChars(resource_key, raw_key);

    const auto snapshot = cachecore::jni::Runtime().Lookup(key);
    if (!snapshot.has_value()) {
        return nullptr;
    }
    const auto json = cachecore::CacheRuntime::BuildLookupJson(snapshot.value());
    return cachecore::jni::ToJString(env, json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wxy_playerlite_cache_core_CacheCoreNativeBridge_nativeLookupByPrefix(
        JNIEnv* env,
        jclass,
        jstring prefix,
        jint limit) {
    if (env == nullptr || prefix == nullptr || limit <= 0) {
        return cachecore::jni::ToJString(env, "[]");
    }
    const char* raw_prefix = env->GetStringUTFChars(prefix, nullptr);
    if (raw_prefix == nullptr) {
        return cachecore::jni::ToJString(env, "[]");
    }
    const std::string prefix_value(raw_prefix);
    env->ReleaseStringUTFChars(prefix, raw_prefix);

    const auto snapshots = cachecore::jni::Runtime().LookupByPrefix(prefix_value, static_cast<int32_t>(limit));
    const auto json = cachecore::CacheRuntime::BuildLookupArrayJson(snapshots);
    return cachecore::jni::ToJString(env, json);
}

