package com.wxy.playerlite.cache.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBootstrapTest {
    @Test
    fun initCallIsSafeAcrossNativeAvailabilityStates() {
        val root = File(System.getProperty("java.io.tmpdir"), "cache-core-native-bootstrap-test")
        val available = CacheCoreNativeBridge.isAvailable()

        val initOk = CacheCoreNativeBridge.init(root.absolutePath)

        if (available) {
            assertTrue(initOk)
            assertTrue(CacheCoreNativeBridge.isInitialized())
        } else {
            assertFalse(initOk)
            assertFalse(CacheCoreNativeBridge.isInitialized())
        }
        CacheCoreNativeBridge.shutdown()
    }
}
