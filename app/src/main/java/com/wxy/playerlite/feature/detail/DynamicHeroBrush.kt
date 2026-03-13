package com.wxy.playerlite.feature.detail

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

internal val DynamicHeroFallbackColors: List<Color> = listOf(
    Color(0xFF221925),
    Color(0xFF7B3042),
    Color(0xFFF1D9C9)
)

@Composable
internal fun rememberDynamicHeroBrush(
    imageUrl: String?,
    fallbackColors: List<Color> = DynamicHeroFallbackColors
): Brush {
    val context = LocalContext.current
    var accentColor by remember(imageUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(imageUrl) {
        accentColor = null
        if (imageUrl.isNullOrBlank()) {
            return@LaunchedEffect
        }
        accentColor = runCatching {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(96)
                .build()
            val result = ImageLoader(context).execute(request)
            val drawable = (result as? SuccessResult)?.drawable ?: return@runCatching null
            sampleDominantColor(drawable.toBitmap(width = 32, height = 32))
        }.getOrNull()
    }

    return accentColor?.let(::dynamicHeroBrush) ?: Brush.verticalGradient(fallbackColors)
}

internal fun dynamicHeroBrush(accentColor: Color): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            shiftColor(accentColor, 0.32f),
            accentColor.copy(alpha = 0.92f),
            shiftColor(accentColor, 1.28f)
        )
    )
}

internal fun shiftColor(
    color: Color,
    factor: Float
): Color {
    return Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = 1f
    )
}

internal fun sampleDominantColor(bitmap: Bitmap): Color {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    val stepX = (bitmap.width / 8).coerceAtLeast(1)
    val stepY = (bitmap.height / 8).coerceAtLeast(1)
    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            count += 1
            y += stepY
        }
        x += stepX
    }
    if (count == 0L) {
        return DynamicHeroFallbackColors[1]
    }
    return Color(
        red = (red.toFloat() / count / 255f).coerceIn(0f, 1f),
        green = (green.toFloat() / count / 255f).coerceIn(0f, 1f),
        blue = (blue.toFloat() / count / 255f).coerceIn(0f, 1f),
        alpha = 1f
    )
}
