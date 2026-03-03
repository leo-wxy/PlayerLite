#include "cache_core_jni_shared.h"

namespace {
cachecore::CacheRuntime g_runtime;
cachecore::JniProviderBridge g_provider_bridge;
}  // namespace

namespace cachecore {
namespace jni {

CacheRuntime& Runtime() {
    return g_runtime;
}

JniProviderBridge& ProviderBridge() {
    return g_provider_bridge;
}

void EnsureJniSetup(JavaVM* vm) {
    g_provider_bridge.SetJavaVm(vm);
    g_runtime.SetProviderBridge(&g_provider_bridge);
}

jbyteArray ToByteArray(JNIEnv* env, const std::vector<uint8_t>& bytes) {
    if (env == nullptr) {
        return nullptr;
    }
    auto* output = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (output == nullptr) {
        return nullptr;
    }
    if (!bytes.empty()) {
        env->SetByteArrayRegion(
                output,
                0,
                static_cast<jsize>(bytes.size()),
                reinterpret_cast<const jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            env->DeleteLocalRef(output);
            return nullptr;
        }
    }
    return output;
}

jstring ToJString(JNIEnv* env, const std::string& value) {
    if (env == nullptr) {
        return nullptr;
    }
    return env->NewStringUTF(value.c_str());
}

}  // namespace jni
}  // namespace cachecore

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    cachecore::jni::EnsureJniSetup(vm);
    return JNI_VERSION_1_6;
}

