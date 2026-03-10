package com.wxy.playerlite.feature.user

internal object InitialLoginLaunchGate {
    fun shouldLaunch(
        isSessionReady: Boolean,
        isLoggedIn: Boolean,
        hasHandledInitialGate: Boolean
    ): Boolean {
        return isSessionReady && !isLoggedIn && !hasHandledInitialGate
    }

    fun shouldShowMainContent(
        isSessionReady: Boolean,
        isLoggedIn: Boolean,
        hasHandledInitialGate: Boolean
    ): Boolean {
        return isSessionReady && (isLoggedIn || hasHandledInitialGate)
    }
}
