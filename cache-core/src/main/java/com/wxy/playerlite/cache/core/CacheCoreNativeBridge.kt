package com.wxy.playerlite.cache.core

object CacheCoreNativeBridge {
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

    fun init(cacheRootDirPath: String): Boolean {
        if (!nativeLoaded || cacheRootDirPath.isBlank()) {
            return false
        }
        return runCatching { nativeInit(cacheRootDirPath) == 0 }.getOrDefault(false)
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

    fun openSession(resourceKey: String): Long {
        if (!nativeLoaded || resourceKey.isBlank()) {
            return -1L
        }
        return runCatching { nativeOpenSession(resourceKey) }.getOrDefault(-1L)
    }

    fun closeSession(sessionId: Long): Boolean {
        if (!nativeLoaded || sessionId <= 0L) {
            return false
        }
        return runCatching { nativeCloseSession(sessionId) }.getOrDefault(false)
    }

    private external fun nativeIsAvailable(): Boolean

    private external fun nativeInit(cacheRootDirPath: String): Int

    private external fun nativeShutdown()

    private external fun nativeIsInitialized(): Boolean

    private external fun nativeOpenSession(resourceKey: String): Long

    private external fun nativeCloseSession(sessionId: Long): Boolean
}
