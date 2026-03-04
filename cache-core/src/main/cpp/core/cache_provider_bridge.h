#pragma once

#include <jni.h>

#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "cache_runtime.h"

namespace cachecore {

class JniProviderBridge final : public ProviderBridge {
public:
    void SetJavaVm(JavaVM* vm);

    std::vector<uint8_t> ReadAt(int64_t provider_handle, int64_t offset, int32_t size) override;
    bool ReadAtStream(
            int64_t provider_handle,
            int64_t offset,
            int32_t size,
            const StreamCallbacks& callbacks) override;
    void CancelInFlightRead(int64_t provider_handle) override;
    int64_t QueryContentLength(int64_t provider_handle) override;
    void Close(int64_t provider_handle) override;

    void OnStreamBegin(int64_t request_id, int64_t offset, int32_t requested_size);
    bool OnStreamData(int64_t request_id, const std::vector<uint8_t>& bytes);
    void OnStreamEnd(int64_t request_id, bool success);

private:
    struct StreamState {
        std::mutex mutex;
        std::condition_variable cv;
        bool completed = false;
        bool success = false;
        bool end_notified = false;
        int64_t offset = 0;
        int32_t requested_size = 0;
        int64_t next_chunk_offset = 0;
        StreamCallbacks callbacks;
        bool collect_bytes = true;
        std::vector<uint8_t> bytes;
    };

    int64_t CreateStreamState(
            int64_t offset,
            int32_t requested_size,
            const StreamCallbacks* callbacks,
            bool collect_bytes);
    std::shared_ptr<StreamState> GetStreamState(int64_t request_id);
    bool WaitAndConsumeStream(int64_t request_id, bool call_ok, std::vector<uint8_t>* out_bytes);

    bool EnsureResolved(JNIEnv* env);
    JNIEnv* AttachThread(bool* attached);
    void DetachThread(bool attached);

    std::mutex mutex_;
    JavaVM* jvm_ = nullptr;
    jclass bridge_class_ = nullptr;
    jmethodID read_at_mid_ = nullptr;
    jmethodID read_at_stream_mid_ = nullptr;
    jmethodID cancel_mid_ = nullptr;
    jmethodID query_mid_ = nullptr;
    jmethodID close_mid_ = nullptr;

    std::mutex stream_mutex_;
    int64_t next_stream_request_id_ = 1;
    std::unordered_map<int64_t, std::shared_ptr<StreamState>> stream_states_;
};

}  // namespace cachecore
