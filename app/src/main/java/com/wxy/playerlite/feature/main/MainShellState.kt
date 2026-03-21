package com.wxy.playerlite.feature.main

import java.io.Serializable

internal enum class MainTab : Serializable {
    HOME,
    USER_CENTER
}

internal data class MainShellState(
    val selectedTab: MainTab = MainTab.HOME
) : Serializable {
    fun selectTab(tab: MainTab): MainShellState {
        return copy(selectedTab = tab)
    }
}
