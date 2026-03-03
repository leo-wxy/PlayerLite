#pragma once

#include <jni.h>

#include <mutex>

#include "cache_runtime.h"

namespace cachecore {

class JniProviderBridge final : public ProviderBridge {
public:
    void SetJavaVm(JavaVM* vm);

    std::vector<uint8_t> ReadAt(int64_t provider_handle, int64_t offset, int32_t size) override;
    void CancelInFlightRead(int64_t provider_handle) override;
    int64_t QueryContentLength(int64_t provider_handle) override;
    void Close(int64_t provider_handle) override;

private:
    bool EnsureResolved(JNIEnv* env);
    JNIEnv* AttachThread(bool* attached);
    void DetachThread(bool attached);

    std::mutex mutex_;
    JavaVM* jvm_ = nullptr;
    jclass bridge_class_ = nullptr;
    jmethodID read_at_mid_ = nullptr;
    jmethodID cancel_mid_ = nullptr;
    jmethodID query_mid_ = nullptr;
    jmethodID close_mid_ = nullptr;
};

}  // namespace cachecore

