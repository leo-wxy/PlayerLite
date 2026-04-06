package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun PlayerLandscapeVisualReflection(
    coverUrl: String?,
    isPlaying: Boolean,
    isPaused: Boolean,
    isPreparing: Boolean,
    sourceSize: Dp,
    reflectionHeight: Dp,
    onBackdropColorExtracted: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val stageBaseColor = Color(0xFF2A2624)
    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .drawWithCache {
                val coverHeight = size.width
                val reflectionTop = coverHeight
                val reflectionHeightPx = (size.height - coverHeight).coerceAtLeast(0f)
                val sideFadeWidth = size.width * 0.16f
                val sideFadeStartY = reflectionTop + (reflectionHeightPx * 0.10f)
                val verticalMask = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.82f),
                        0.22f to Color.White.copy(alpha = 0.42f),
                        0.58f to Color.Transparent
                    ),
                    startY = reflectionTop,
                    endY = reflectionTop + reflectionHeightPx
                )
                val leftFade = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to stageBaseColor,
                        0.85f to stageBaseColor.copy(alpha = 0.42f),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, reflectionTop + reflectionHeightPx),
                    end = Offset(sideFadeWidth, sideFadeStartY)
                )
                val rightFade = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to stageBaseColor,
                        0.85f to stageBaseColor.copy(alpha = 0.42f),
                        1f to Color.Transparent
                    ),
                    start = Offset(size.width, reflectionTop + reflectionHeightPx),
                    end = Offset(size.width - sideFadeWidth, sideFadeStartY)
                )

                onDrawWithContent {
                    drawContent()
                    if (reflectionHeightPx <= 0f) return@onDrawWithContent

                    clipRect(
                        left = 0f,
                        top = reflectionTop,
                        right = size.width,
                        bottom = reflectionTop + reflectionHeightPx
                    ) {
                        withTransform({
                            translate(top = reflectionTop + coverHeight)
                            scale(scaleX = 1f, scaleY = -1f, pivot = Offset.Zero)
                        }) {
                            this@onDrawWithContent.drawContent()
                        }
                        drawRect(
                            color = Color.Black.copy(alpha = 0.28f),
                            topLeft = Offset(0f, reflectionTop),
                            size = Size(size.width, reflectionHeightPx),
                            blendMode = BlendMode.SrcAtop
                        )
                        drawRect(
                            brush = leftFade,
                            topLeft = Offset.Zero,
                            size = Size(sideFadeWidth, size.height)
                        )
                        drawRect(
                            brush = rightFade,
                            topLeft = Offset(size.width - sideFadeWidth, 0f),
                            size = Size(sideFadeWidth, size.height)
                        )
                        drawRect(
                            brush = verticalMask,
                            topLeft = Offset(0f, reflectionTop),
                            size = Size(size.width, reflectionHeightPx),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag("player_screen_landscape_cover_frame")
        ) {
            PlayerCoverCard(
                isPlaying = isPlaying,
                isPaused = isPaused,
                coverUrl = coverUrl,
                onBackdropColorExtracted = onBackdropColorExtracted,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(reflectionHeight)
                .testTag("player_screen_landscape_visual_reflection")
        )
    }
}

@Composable
internal fun PlayerLandscapeControlsReflection(
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val accentColor = PlayerLiteVisualTheme.colors.accentStrong
    PlayerLandscapeReflectionSurface(
        tag = "player_screen_landscape_controls_reflection",
        reflectionAlpha = 0.08f,
        verticalScale = 0.50f,
        stripHeight = 14.dp,
        waveAmplitude = 2.dp,
        tintColor = Color(0xFF151A20),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boundedProgress = progressFraction.coerceIn(0.05f, 1f)
            val trackWidth = size.width * 0.84f
            val trackHeight = size.height * 0.18f
            val trackLeft = (size.width - trackWidth) / 2f
            val trackTop = size.height * 0.12f
            val trackCorner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
                cornerRadius = trackCorner
            )
            drawRoundRect(
                color = accentColor.copy(alpha = 0.32f),
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, trackTop),
                size = androidx.compose.ui.geometry.Size(trackWidth * boundedProgress, trackHeight),
                cornerRadius = trackCorner
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.56f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = trackLeft + (trackWidth * boundedProgress) - min(size.width * 0.022f, 7f),
                    y = trackTop - (trackHeight * 0.12f)
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = min(size.width * 0.028f, 10f),
                    height = trackHeight * 1.24f
                ),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            val controlCenters = listOf(0.16f, 0.34f, 0.50f, 0.66f, 0.84f)
            val controlY = size.height * 0.70f
            controlCenters.forEachIndexed { index, ratio ->
                val isPrimary = index == 2
                val radius = if (isPrimary) size.height * 0.12f else size.height * 0.085f
                drawCircle(
                    color = if (isPrimary) {
                        accentColor.copy(alpha = 0.34f)
                    } else {
                        Color.White.copy(alpha = 0.24f)
                    },
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(size.width * ratio, controlY)
                )
            }
        }
    }
}

@Composable
private fun PlayerLandscapeReflectionSurface(
    tag: String,
    reflectionAlpha: Float,
    verticalScale: Float,
    stripHeight: Dp,
    waveAmplitude: Dp,
    tintColor: Color,
    showRippleHighlights: Boolean = true,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit
) {
    val waveTransition = rememberInfiniteTransition(label = "${tag}_wave")
    val phase = waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${tag}_phase"
    )
    val rememberedPhase = remember(phase.value) { phase.value }

    Box(
        modifier = modifier
            .testTag(tag)
            .clip(shape)
            .graphicsLayer {
                alpha = reflectionAlpha
                scaleY = -verticalScale
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .playerLandscapeWaterDistortion(
                phase = rememberedPhase,
                waveAmplitude = waveAmplitude,
                stripHeight = stripHeight,
                tintColor = tintColor,
                showRippleHighlights = showRippleHighlights
            )
    ) {
        content()
    }
}

private fun Modifier.playerLandscapeWaterDistortion(
    phase: Float,
    waveAmplitude: Dp,
    stripHeight: Dp,
    tintColor: Color,
    showRippleHighlights: Boolean
): Modifier {
    return drawWithCache {
        val stripHeightPx = stripHeight.toPx().coerceAtLeast(5f)
        val waveAmplitudePx = waveAmplitude.toPx().coerceAtLeast(1f)
        val maskBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.94f),
                0.24f to Color.White.copy(alpha = 0.68f),
                0.62f to Color.White.copy(alpha = 0.24f),
                1f to Color.Transparent
            )
        )
        val tintBrush = Brush.verticalGradient(
            colors = listOf(
                tintColor.copy(alpha = 0.01f),
                tintColor.copy(alpha = 0.03f),
                tintColor.copy(alpha = 0.06f)
            )
        )

        onDrawWithContent {
            if (size.width <= 0f || size.height <= 0f) {
                return@onDrawWithContent
            }

            val stripCount = max(1, ceil(size.height / stripHeightPx).toInt())
            repeat(stripCount) { index ->
                val top = index * stripHeightPx
                val bottom = min(size.height, top + stripHeightPx)
                val progress = index / stripCount.toFloat()
                val localAmplitude = waveAmplitudePx * (0.32f + progress)
                val offset = sin(phase + (index * 0.62f)) * localAmplitude
                clipRect(top = top, bottom = bottom) {
                    translate(left = offset) {
                        this@onDrawWithContent.drawContent()
                    }
                }
            }

            drawRect(brush = tintBrush, blendMode = BlendMode.SrcAtop)
            if (showRippleHighlights) {
                drawLandscapeRippleHighlights(phase = phase)
            }
            drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
        }
    }
}

private fun DrawScope.drawLandscapeRippleHighlights(
    phase: Float
) {
    repeat(4) { index ->
        val baseY = size.height * (0.18f + (index * 0.18f))
        val waveHeight = 2f + index
        val wavelength = size.width / (1.42f + (index * 0.16f))
        val step = max(12f, size.width / 28f)
        val path = Path().apply {
            moveTo(0f, baseY)
            var x = 0f
            while (x <= size.width) {
                val angle = ((x / wavelength) * (PI.toFloat() * 2f)) + phase * (1.05f + index * 0.12f)
                lineTo(x, baseY + (sin(angle) * waveHeight))
                x += step
            }
        }
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.12f - (index * 0.018f)),
            style = Stroke(width = 1.3f)
        )
    }
}
