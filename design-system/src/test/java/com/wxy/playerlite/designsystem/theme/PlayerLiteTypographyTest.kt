package com.wxy.playerlite.designsystem.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLiteTypographyTest {
    @Test
    fun playerLiteTypography_shouldUseSansSerifAcrossAllMaterialRoles() {
        with(PlayerLiteTypography) {
            assertEquals(FontFamily.SansSerif, displayLarge.fontFamily)
            assertEquals(FontFamily.SansSerif, displayMedium.fontFamily)
            assertEquals(FontFamily.SansSerif, displaySmall.fontFamily)
            assertEquals(FontFamily.SansSerif, headlineLarge.fontFamily)
            assertEquals(FontFamily.SansSerif, headlineMedium.fontFamily)
            assertEquals(FontFamily.SansSerif, headlineSmall.fontFamily)
            assertEquals(FontFamily.SansSerif, titleLarge.fontFamily)
            assertEquals(FontFamily.SansSerif, titleMedium.fontFamily)
            assertEquals(FontFamily.SansSerif, titleSmall.fontFamily)
            assertEquals(FontFamily.SansSerif, bodyLarge.fontFamily)
            assertEquals(FontFamily.SansSerif, bodyMedium.fontFamily)
            assertEquals(FontFamily.SansSerif, bodySmall.fontFamily)
            assertEquals(FontFamily.SansSerif, labelLarge.fontFamily)
            assertEquals(FontFamily.SansSerif, labelMedium.fontFamily)
            assertEquals(FontFamily.SansSerif, labelSmall.fontFamily)
        }
    }
}
