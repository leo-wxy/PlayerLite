package com.wxy.playerlite.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainShellStateTest {
    @Test
    fun defaultState_shouldStartAtHomeOverview() {
        val state = MainShellState()

        assertEquals(MainTab.HOME, state.selectedTab)
        assertEquals(HomeSurfaceMode.OVERVIEW, state.homeSurfaceMode)
    }

    @Test
    fun openPlayer_shouldKeepHomeSelectedAndExpandPlayer() {
        val state = MainShellState()

        val next = state.openPlayer()

        assertEquals(MainTab.HOME, next.selectedTab)
        assertEquals(HomeSurfaceMode.PLAYER_EXPANDED, next.homeSurfaceMode)
    }

    @Test
    fun selectUserCenter_shouldSwitchTabWithoutLosingHomeMode() {
        val state = MainShellState(
            selectedTab = MainTab.HOME,
            homeSurfaceMode = HomeSurfaceMode.PLAYER_EXPANDED
        )

        val next = state.selectTab(MainTab.USER_CENTER)

        assertEquals(MainTab.USER_CENTER, next.selectedTab)
        assertEquals(HomeSurfaceMode.PLAYER_EXPANDED, next.homeSurfaceMode)
    }

    @Test
    fun reselectHome_whenPlayerExpanded_shouldCollapseToOverview() {
        val state = MainShellState(
            selectedTab = MainTab.HOME,
            homeSurfaceMode = HomeSurfaceMode.PLAYER_EXPANDED
        )

        val next = state.selectTab(MainTab.HOME)

        assertEquals(MainTab.HOME, next.selectedTab)
        assertEquals(HomeSurfaceMode.OVERVIEW, next.homeSurfaceMode)
    }

    @Test
    fun playerExpanded_shouldHideBottomBarAndShowBackButton() {
        val state = MainShellState(
            selectedTab = MainTab.HOME,
            homeSurfaceMode = HomeSurfaceMode.PLAYER_EXPANDED
        )

        assertFalse(state.shouldShowBottomBar)
        assertTrue(state.shouldShowPlayerBackButton)
    }

    @Test
    fun homeOverview_shouldShowBottomBarAndHideBackButton() {
        val state = MainShellState()

        assertTrue(state.shouldShowBottomBar)
        assertFalse(state.shouldShowPlayerBackButton)
    }
}
