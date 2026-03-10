package com.wxy.playerlite.feature.main

import java.io.Serializable

internal enum class MainTab : Serializable {
    HOME,
    USER_CENTER
}

internal enum class HomeSurfaceMode : Serializable {
    OVERVIEW,
    PLAYER_EXPANDED
}

internal data class MainShellState(
    val selectedTab: MainTab = MainTab.HOME,
    val homeSurfaceMode: HomeSurfaceMode = HomeSurfaceMode.OVERVIEW
) : Serializable {
    val shouldShowBottomBar: Boolean
        get() = !(selectedTab == MainTab.HOME && homeSurfaceMode == HomeSurfaceMode.PLAYER_EXPANDED)

    val shouldShowPlayerBackButton: Boolean
        get() = selectedTab == MainTab.HOME && homeSurfaceMode == HomeSurfaceMode.PLAYER_EXPANDED

    fun selectTab(tab: MainTab): MainShellState {
        return if (tab == MainTab.HOME && selectedTab == MainTab.HOME && homeSurfaceMode == HomeSurfaceMode.PLAYER_EXPANDED) {
            copy(homeSurfaceMode = HomeSurfaceMode.OVERVIEW)
        } else {
            copy(selectedTab = tab)
        }
    }

    fun openPlayer(): MainShellState {
        return copy(
            selectedTab = MainTab.HOME,
            homeSurfaceMode = HomeSurfaceMode.PLAYER_EXPANDED
        )
    }

    fun collapsePlayer(): MainShellState {
        return copy(homeSurfaceMode = HomeSurfaceMode.OVERVIEW)
    }
}
