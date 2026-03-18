package com.wxy.playerlite.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerLiteDesignThemeTokensRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_shouldExposeSharedVisualTokens() {
        var tokens: PlayerLiteVisualTokens? = null

        composeRule.setContent {
            PlayerLiteDesignTheme(darkTheme = false) {
                tokens = PlayerLiteVisualTheme.colors
            }
        }

        composeRule.runOnIdle {
            val current = requireNotNull(tokens)
            assertEquals(Color(0xFFF9F9FB), current.canvas)
            assertEquals(Color(0xFFE53935), current.accentStrong)
            assertEquals(Color(0xFF0087A0), current.accentSupport)
            assertEquals(
                Color(0xFF616161),
                current.miniPlayerProgressTrack
            )
        }
    }
}
