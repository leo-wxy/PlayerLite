package com.wxy.playerlite.feature.search

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
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
}
