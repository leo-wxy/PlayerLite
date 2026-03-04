#pragma once

#include <jni.h>

#include <cstdint>
#include <string>

#include "i_play_source.h"

class JniPlaySource final : public IPlaySource {
public:
    JniPlaySource(JNIEnv* env, jobject source_object);
    ~JniPlaySource() override;

    bool Init(std::string* error_message);
    bool Open(std::string* error_message);
    void Close();

    int Read(uint8_t* buffer, int size, std::string* error_message) override;
    int64_t Seek(int64_t offset, int whence, std::string* error_message) override;
    bool SupportsFastSeek() const override;

private:
    bool Fail(std::string* error_message, const char* message);
    int FailWithCode(std::string* error_message, const char* message, int code);
    int64_t FailWithCode(std::string* error_message, const char* message, int64_t code);
    bool EnsureReadArrayBuffer(int size, std::string* error_message);

    JNIEnv* env_ = nullptr;
    jobject source_local_ = nullptr;
    jobject source_global_ = nullptr;
    jmethodID open_mid_ = nullptr;
    jmethodID read_direct_mid_ = nullptr;
    jmethodID read_array_mid_ = nullptr;
    jmethodID seek_mid_ = nullptr;
    jmethodID support_fast_seek_mid_ = nullptr;
    jmethodID close_mid_ = nullptr;
    jbyteArray read_array_buffer_ = nullptr;
    bool is_opened_ = false;
    bool supports_fast_seek_ = true;
};
