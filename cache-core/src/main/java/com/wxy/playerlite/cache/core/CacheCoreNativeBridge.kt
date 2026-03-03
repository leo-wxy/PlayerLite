package com.wxy.playerlite.cache.core

internal object CacheCoreNativeBridge {
    private const val LIB_NAME = "cachecore"

    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary(LIB_NAME)
        true
    }.getOrElse {
        false
    }

    fun isAvailable(): Boolean {
        if (!nativeLoaded) {
            return false
        }
        return runCatching { nativeIsAvailable() }.getOrDefault(false)
    }

    fun init(cacheRootDirPath: String, memoryCacheCapBytes: Long): Boolean {
        if (!nativeLoaded || cacheRootDirPath.isBlank()) {
            return false
        }
        return runCatching { nativeInit(cacheRootDirPath, memoryCacheCapBytes) == 0 }.getOrDefault(false)
    }

    fun shutdown() {
        if (!nativeLoaded) {
            return
        }
        runCatching { nativeShutdown() }
    }

    fun isInitialized(): Boolean {
        if (!nativeLoaded) {
            return false
        }
        return runCatching { nativeIsInitialized() }.getOrDefault(false)
    }

    fun openSession(
        resourceKey: String,
        blockSizeBytes: Int,
        contentLengthHint: Long,
        durationMsHint: Long,
        providerHandle: Long
    ): Long {
        if (!nativeLoaded || resourceKey.isBlank() || providerHandle <= 0L) {
            return -1L
        }
        return runCatching {
            nativeOpenSession(
                resourceKey = resourceKey,
                blockSizeBytes = blockSizeBytes,
                contentLengthHint = contentLengthHint,
                durationMsHint = durationMsHint,
                providerHandle = providerHandle
            )
        }.getOrDefault(-1L)
    }

    fun read(sessionId: Long, size: Int): ByteArray? {
        if (!nativeLoaded || sessionId <= 0L || size <= 0) {
            return null
        }
        return runCatching { nativeRead(sessionId, size) }.getOrNull()
    }

    fun readAt(sessionId: Long, offset: Long, size: Int): ByteArray? {
        if (!nativeLoaded || sessionId <= 0L || size <= 0 || offset < 0L) {
            return null
        }
        return runCatching { nativeReadAt(sessionId, offset, size) }.getOrNull()
    }

    fun seek(sessionId: Long, offset: Long, whence: Int): Long {
        if (!nativeLoaded || sessionId <= 0L) {
            return -1L
        }
        return runCatching { nativeSeek(sessionId, offset, whence) }.getOrDefault(-1L)
    }

    fun cancelPendingRead(sessionId: Long) {
        if (!nativeLoaded || sessionId <= 0L) {
            return
        }
        runCatching { nativeCancelPendingRead(sessionId) }
    }

    fun closeSession(sessionId: Long): Boolean {
        if (!nativeLoaded || sessionId <= 0L) {
            return false
        }
        return runCatching { nativeCloseSession(sessionId) }.getOrDefault(false)
    }

    fun lookup(resourceKey: String): String? {
        if (!nativeLoaded || resourceKey.isBlank()) {
            return null
        }
        return runCatching { nativeLookup(resourceKey) }.getOrNull()
    }

    fun lookupByPrefix(prefix: String, limit: Int): String {
        if (!nativeLoaded || limit <= 0) {
            return "[]"
        }
        return runCatching { nativeLookupByPrefix(prefix, limit) }.getOrDefault("[]")
    }

    fun clearAll(): Boolean {
        if (!nativeLoaded) {
            return false
        }
        return runCatching { nativeClearAll() }.getOrDefault(false)
    }

    fun releaseProviderHandle(providerHandle: Long) {
        if (!nativeLoaded || providerHandle <= 0L) {
            return
        }
        runCatching { nativeReleaseProviderHandle(providerHandle) }
    }

    @JvmStatic
    fun providerReadAt(handle: Long, offset: Long, size: Int): ByteArray {
        return NativeProviderRegistry.readAt(handle, offset, size)
    }

    @JvmStatic
    fun providerCancelInFlightRead(handle: Long) {
        NativeProviderRegistry.cancelInFlightRead(handle)
    }

    @JvmStatic
    fun providerQueryContentLength(handle: Long): Long {
        return NativeProviderRegistry.queryContentLength(handle)
    }

    @JvmStatic
    fun providerClose(handle: Long) {
        NativeProviderRegistry.close(handle)
    }

    private external fun nativeIsAvailable(): Boolean

    private external fun nativeInit(cacheRootDirPath: String, memoryCacheCapBytes: Long): Int

    private external fun nativeShutdown()

    private external fun nativeIsInitialized(): Boolean

    private external fun nativeOpenSession(
        resourceKey: String,
        blockSizeBytes: Int,
        contentLengthHint: Long,
        durationMsHint: Long,
        providerHandle: Long
    ): Long

    private external fun nativeRead(sessionId: Long, size: Int): ByteArray?

    private external fun nativeReadAt(sessionId: Long, offset: Long, size: Int): ByteArray?

    private external fun nativeSeek(sessionId: Long, offset: Long, whence: Int): Long

    private external fun nativeCancelPendingRead(sessionId: Long)

    private external fun nativeCloseSession(sessionId: Long): Boolean

    private external fun nativeLookup(resourceKey: String): String?

    private external fun nativeLookupByPrefix(prefix: String, limit: Int): String

    private external fun nativeClearAll(): Boolean

    private external fun nativeReleaseProviderHandle(providerHandle: Long)
}

