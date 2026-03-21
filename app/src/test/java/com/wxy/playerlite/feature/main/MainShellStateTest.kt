package com.wxy.playerlite.feature.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainShellStateTest {
    @Test
    fun defaultState_shouldStartAtHomeTab() {
        val state = MainShellState()

        assertEquals(MainTab.HOME, state.selectedTab)
    }

    @Test
    fun selectUserCenter_shouldSwitchTab() {
        val state = MainShellState(selectedTab = MainTab.HOME)

        val next = state.selectTab(MainTab.USER_CENTER)

        assertEquals(MainTab.USER_CENTER, next.selectedTab)
    }

    @Test
    fun reselectHome_shouldKeepHomeSelected() {
        val state = MainShellState(selectedTab = MainTab.HOME)

        val next = state.selectTab(MainTab.HOME)

        assertEquals(MainTab.HOME, next.selectedTab)
    }
}
