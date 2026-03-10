package com.wxy.playerlite.feature.user

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialLoginLaunchGateTest {
    @Test
    fun shouldLaunch_whenSessionReadyAndLoggedOutAndNotHandled() {
        assertTrue(
            InitialLoginLaunchGate.shouldLaunch(
                isSessionReady = true,
                isLoggedIn = false,
                hasHandledInitialGate = false
            )
        )
    }

    @Test
    fun shouldNotLaunch_whenGateAlreadyHandled() {
        assertFalse(
            InitialLoginLaunchGate.shouldLaunch(
                isSessionReady = true,
                isLoggedIn = false,
                hasHandledInitialGate = true
            )
        )
    }

    @Test
    fun shouldNotLaunch_whenStillRestoringSession() {
        assertFalse(
            InitialLoginLaunchGate.shouldLaunch(
                isSessionReady = false,
                isLoggedIn = false,
                hasHandledInitialGate = false
            )
        )
    }

    @Test
    fun shouldNotShowMainContent_whenStillRestoringSession() {
        assertFalse(
            InitialLoginLaunchGate.shouldShowMainContent(
                isSessionReady = false,
                isLoggedIn = false,
                hasHandledInitialGate = false
            )
        )
    }

    @Test
    fun shouldNotShowMainContent_beforeInitialLoginGateLaunches() {
        assertFalse(
            InitialLoginLaunchGate.shouldShowMainContent(
                isSessionReady = true,
                isLoggedIn = false,
                hasHandledInitialGate = false
            )
        )
    }

    @Test
    fun shouldShowMainContent_afterInitialLoginGateHandled() {
        assertTrue(
            InitialLoginLaunchGate.shouldShowMainContent(
                isSessionReady = true,
                isLoggedIn = false,
                hasHandledInitialGate = true
            )
        )
    }
}
