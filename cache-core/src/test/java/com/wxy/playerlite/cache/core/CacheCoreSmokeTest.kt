package com.wxy.playerlite.cache.core

import org.junit.Assert.assertFalse
import org.junit.Test

class CacheCoreSmokeTest {
    @Test
    fun initFlagIsFalseByDefault() {
        assertFalse(CacheCore.isInitialized())
    }
}
