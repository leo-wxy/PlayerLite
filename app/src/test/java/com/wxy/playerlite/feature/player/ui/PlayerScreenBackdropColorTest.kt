package com.wxy.playerlite.feature.player.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerScreenBackdropColorTest {

    @Test
    fun extractBackdropColor_whenCoverIsBright_shouldStayDarkButKeepTint() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(255, 233, 120))
        }

        val method = Class
            .forName("com.wxy.playerlite.feature.player.ui.PlayerScreenKt")
            .getDeclaredMethod("extractBackdropColor", Bitmap::class.java)
            .apply { isAccessible = true }

        val reflectedColor = method.invoke(null, bitmap)
        val color = when (reflectedColor) {
            is Color -> reflectedColor
            is Long -> Color(reflectedColor.toULong())
            is ULong -> Color(reflectedColor)
            else -> error("Unexpected reflected color type: ${reflectedColor?.javaClass}")
        }
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)

        assertTrue(
            "Expected bright cover to still map to a dark backdrop, but value was ${hsv[2]}",
            hsv[2] in 0.14f..0.30f
        )
        assertTrue(
            "Expected bright cover to preserve more tint instead of collapsing into near-gray, but saturation was ${hsv[1]}",
            hsv[1] >= 0.30f
        )
        assertTrue(
            "Expected hue to stay within the warm cover family, but hue was ${hsv[0]}",
            hsv[0] in 35f..75f
        )
    }

    @Test
    fun extractBackdropColorSafely_whenBitmapCannotBeRead_shouldReturnNullInsteadOfThrowing() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            recycle()
        }

        val method = Class
            .forName("com.wxy.playerlite.feature.player.ui.PlayerScreenKt")
            .getDeclaredMethod("extractBackdropColorSafely", Bitmap::class.java)
            .apply { isAccessible = true }

        val result = method.invoke(null, bitmap)

        assertNull(result)
    }
}
