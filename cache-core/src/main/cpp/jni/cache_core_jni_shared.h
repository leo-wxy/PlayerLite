#pragma once

#include <jni.h>

#include "core/cache_provider_bridge.h"
#include "core/cache_runtime.h"

namespace cachecore {
namespace jni {

CacheRuntime& Runtime();
JniProviderBridge& ProviderBridge();
void EnsureJniSetup(JavaVM* vm);

jbyteArray ToByteArray(JNIEnv* env, const std::vector<uint8_t>& bytes);
jstring ToJString(JNIEnv* env, const std::string& value);

}  // namespace jni
}  // namespace cachecore

