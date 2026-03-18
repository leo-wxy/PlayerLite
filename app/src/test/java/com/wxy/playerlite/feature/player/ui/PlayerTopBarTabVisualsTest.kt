package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTopBarTabVisualsTest {

    @Test
    fun resolvePlayerTopBarTabVisuals_shouldUseSecondaryForUnselectedAndKeepSameWeight() {
        val colorScheme = PlayerLiteThemeContract.colorScheme(darkTheme = false)
        val visualTokens = PlayerLiteThemeContract.visualTokens(
            darkTheme = false,
            colorScheme = colorScheme
        )

        val selected = resolvePlayerTopBarTabVisuals(
            selected = true,
            visualTokens = visualTokens
        )
        val unselected = resolvePlayerTopBarTabVisuals(
            selected = false,
            visualTokens = visualTokens
        )

        assertEquals(visualTokens.accentStrong, selected.textColor)
        assertEquals(visualTokens.accentStrong, selected.indicatorColor)
        assertEquals(
            PlayerLiteThemeContract.DefaultBrandPalettes.light.neutral,
            unselected.textColor
        )
        assertEquals(FontWeight.Medium, selected.fontWeight)
        assertEquals(FontWeight.Medium, unselected.fontWeight)
        assertEquals(selected.fontWeight, unselected.fontWeight)
    }
}
