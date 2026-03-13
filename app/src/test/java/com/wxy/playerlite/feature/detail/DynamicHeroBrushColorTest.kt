package com.wxy.playerlite.feature.detail

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicHeroBrushColorTest {
    @Test
    fun sampleDominantColor_shouldAverageBitmapPixels() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(200, 80, 40))
        }

        val color = sampleDominantColor(bitmap)

        assertTrue(color.red > color.green)
        assertTrue(color.green > color.blue / 2f)
    }

    @Test
    fun shiftColor_shouldScaleRgbChannelsWithinBounds() {
        val shifted = shiftColor(Color(0.4f, 0.2f, 0.1f, 1f), factor = 1.5f)

        assertEquals(0.6f, shifted.red, 0.01f)
        assertEquals(0.3f, shifted.green, 0.01f)
        assertEquals(0.15f, shifted.blue, 0.01f)
    }
}
