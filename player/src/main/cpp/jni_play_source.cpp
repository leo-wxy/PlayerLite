#include "jni_play_source.h"

JniPlaySource::JniPlaySource(JNIEnv* env, jobject source_object)
    : env_(env), source_local_(source_object) {}

JniPlaySource::~JniPlaySource() {
    if (read_array_buffer_ != nullptr) {
        env_->DeleteGlobalRef(read_array_buffer_);
        read_array_buffer_ = nullptr;
    }
    if (source_global_ != nullptr) {
        env_->DeleteGlobalRef(source_global_);
        source_global_ = nullptr;
    }
}

bool JniPlaySource::Init(std::string* error_message) {
    if (source_local_ == nullptr) {
        return Fail(error_message, "source object is null");
    }

    source_global_ = env_->NewGlobalRef(source_local_);
    if (source_global_ == nullptr) {
        return Fail(error_message, "failed to create source global ref");
    }

    jclass source_class = env_->GetObjectClass(source_global_);
    if (source_class == nullptr) {
        return Fail(error_message, "failed to resolve source class");
    }

    open_mid_ = env_->GetMethodID(
            source_class,
            "open",
            "()Lcom/wxy/playerlite/player/source/IPlaysource$AudioSourceCode;");

    read_direct_mid_ = env_->GetMethodID(source_class, "readDirect", "(Ljava/nio/ByteBuffer;I)I");
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        read_direct_mid_ = nullptr;
    }

    read_array_mid_ = env_->GetMethodID(source_class, "read", "([BI)I");
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        read_array_mid_ = nullptr;
    }

    seek_mid_ = env_->GetMethodID(source_class, "seek", "(JI)J");
    close_mid_ = env_->GetMethodID(source_class, "close", "()V");
    env_->DeleteLocalRef(source_class);

    if (open_mid_ == nullptr || seek_mid_ == nullptr || close_mid_ == nullptr ||
        (read_direct_mid_ == nullptr && read_array_mid_ == nullptr)) {
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }
        return Fail(error_message, "failed to resolve source methods");
    }

    return true;
}

bool JniPlaySource::Open(std::string* error_message) {
    if (source_global_ == nullptr || open_mid_ == nullptr) {
        return Fail(error_message, "invalid source open state");
    }

    jobject open_code = env_->CallObjectMethod(source_global_, open_mid_);
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        return Fail(error_message, "source.open threw exception");
    }
    if (open_code == nullptr) {
        return Fail(error_message, "source.open returned null");
    }

    jclass open_code_class = env_->GetObjectClass(open_code);
    if (open_code_class == nullptr) {
        env_->DeleteLocalRef(open_code);
        return Fail(error_message, "failed to resolve open code class");
    }
    jmethodID get_code_mid = env_->GetMethodID(open_code_class, "getCode", "()I");
    if (get_code_mid == nullptr) {
        env_->DeleteLocalRef(open_code_class);
        env_->DeleteLocalRef(open_code);
        return Fail(error_message, "failed to resolve open code getter");
    }

    const jint code = env_->CallIntMethod(open_code, get_code_mid);
    env_->DeleteLocalRef(open_code_class);
    env_->DeleteLocalRef(open_code);
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        return Fail(error_message, "open code getter threw exception");
    }

    if (code != 0) {
        if (error_message != nullptr) {
            *error_message = "source open failed with code " + std::to_string(code);
        }
        return false;
    }

    is_opened_ = true;
    return true;
}

void JniPlaySource::Close() {
    if (!is_opened_) {
        return;
    }
    if (source_global_ != nullptr && close_mid_ != nullptr) {
        env_->CallVoidMethod(source_global_, close_mid_);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }
    }
    is_opened_ = false;
}

int JniPlaySource::Read(uint8_t* buffer, int size, std::string* error_message) {
    if (source_global_ == nullptr || buffer == nullptr || size <= 0) {
        return FailWithCode(error_message, "invalid source read state", -1);
    }

    if (read_direct_mid_ != nullptr) {
        // 首选路径：让 Source 直接写入 native buffer，减少一次拷贝。
        jobject direct_buffer = env_->NewDirectByteBuffer(buffer, static_cast<jlong>(size));
        if (direct_buffer != nullptr) {
            const jint read_size = env_->CallIntMethod(
                    source_global_,
                    read_direct_mid_,
                    direct_buffer,
                    static_cast<jint>(size));
            env_->DeleteLocalRef(direct_buffer);

            if (!env_->ExceptionCheck()) {
                if (read_size < 0) {
                    return FailWithCode(error_message, "source.readDirect returned error", -1);
                }
                if (read_size > size) {
                    return size;
                }
                return static_cast<int>(read_size);
            }

            env_->ExceptionClear();
            if (read_array_mid_ == nullptr) {
                return FailWithCode(error_message, "source.readDirect threw exception", -1);
            }
        } else {
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
            }
            if (read_array_mid_ == nullptr) {
                return FailWithCode(error_message, "failed to create direct byte buffer", -1);
            }
        }
    }

    // 兼容回退：走 byte[] 读取，再拷贝到 native buffer。
    if (read_array_mid_ == nullptr) {
        return FailWithCode(error_message, "source.read is unavailable", -1);
    }

    if (!EnsureReadArrayBuffer(size, error_message)) {
        return -1;
    }

    const jint read_size = env_->CallIntMethod(
            source_global_,
            read_array_mid_,
            read_array_buffer_,
            static_cast<jint>(size));
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        return FailWithCode(error_message, "source.read threw exception", -1);
    }

    if (read_size < 0) {
        return FailWithCode(error_message, "source.read returned error", -1);
    }
    if (read_size == 0) {
        return 0;
    }

    const int copy_size = read_size > size ? size : static_cast<int>(read_size);
    env_->GetByteArrayRegion(read_array_buffer_, 0, copy_size, reinterpret_cast<jbyte*>(buffer));
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        return FailWithCode(error_message, "failed to copy source bytes", -1);
    }

    return copy_size;
}

int64_t JniPlaySource::Seek(int64_t offset, int whence, std::string* error_message) {
    if (source_global_ == nullptr || seek_mid_ == nullptr) {
        return FailWithCode(error_message, "invalid source seek state", -1);
    }

    const jlong result = env_->CallLongMethod(
            source_global_,
            seek_mid_,
            static_cast<jlong>(offset),
            static_cast<jint>(whence));
    if (env_->ExceptionCheck()) {
        env_->ExceptionClear();
        return FailWithCode(error_message, "source.seek threw exception", -1);
    }

    return static_cast<int64_t>(result);
}

bool JniPlaySource::EnsureReadArrayBuffer(int size, std::string* error_message) {
    if (size <= 0) {
        return true;
    }

    if (read_array_buffer_ != nullptr) {
        const jsize current_capacity = env_->GetArrayLength(read_array_buffer_);
        if (current_capacity >= size) {
            return true;
        }
        // 仅在容量不足时重建缓冲区，降低 JNI 分配抖动。
        env_->DeleteGlobalRef(read_array_buffer_);
        read_array_buffer_ = nullptr;
    }

    jbyteArray local_buffer = env_->NewByteArray(size);
    if (local_buffer == nullptr) {
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
        }
        return Fail(error_message, "failed to allocate source read fallback buffer");
    }

    read_array_buffer_ = reinterpret_cast<jbyteArray>(env_->NewGlobalRef(local_buffer));
    env_->DeleteLocalRef(local_buffer);
    if (read_array_buffer_ == nullptr) {
        return Fail(error_message, "failed to cache source read fallback buffer");
    }

    return true;
}

bool JniPlaySource::Fail(std::string* error_message, const char* message) {
    if (error_message != nullptr) {
        *error_message = message;
    }
    return false;
}

int JniPlaySource::FailWithCode(std::string* error_message, const char* message, int code) {
    if (error_message != nullptr) {
        *error_message = message;
    }
    return code;
}

int64_t JniPlaySource::FailWithCode(std::string* error_message, const char* message, int64_t code) {
    if (error_message != nullptr) {
        *error_message = message;
    }
    return code;
}
