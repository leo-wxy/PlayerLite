package com.wxy.playerlite.feature.search

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createComposeRule
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTokens
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchFeatureThemeColorSchemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_shouldMatchSharedStylePalette() {
        var colorScheme: ColorScheme? = null

        composeRule.setContent {
            SearchFeatureTheme(darkTheme = false) {
                colorScheme = MaterialTheme.colorScheme
            }
        }

        composeRule.runOnIdle {
            val scheme = requireNotNull(colorScheme)
            assertEquals(Color(0xFFE53935), scheme.primary)
            assertEquals(Color(0xFF616161), scheme.secondary)
            assertEquals(Color(0xFF0087A0), scheme.tertiary)
            assertEquals(Color(0xFFF9F9FB), scheme.background)
        }
    }

    @Test
    fun lightTheme_shouldExposeSearchVisualTokensDerivedFromSharedPalette() {
        var searchTokens: SearchFeatureVisualTokens? = null
        var sharedTokens: PlayerLiteVisualTokens? = null

        composeRule.setContent {
            SearchFeatureTheme(darkTheme = false) {
                searchTokens = SearchFeatureVisualTheme.colors
                sharedTokens = PlayerLiteVisualTheme.colors
            }
        }

        composeRule.runOnIdle {
            val search = requireNotNull(searchTokens)
            val shared = requireNotNull(sharedTokens)

            assertEquals(lerp(shared.canvas, shared.surfaceMuted, 0.72f), search.pageBackgroundStart)
            assertEquals(shared.canvas, search.pageBackgroundEnd)
            assertEquals(shared.surfaceRaised, search.panel)
            assertEquals(shared.dividerSubtle, search.divider)
            assertEquals(shared.accentStrong, search.accent)
            assertEquals(shared.accentStrong.copy(alpha = 0.84f), search.accentMuted)
            assertEquals(shared.textSecondary, search.textSecondary)
        }
    }
}
