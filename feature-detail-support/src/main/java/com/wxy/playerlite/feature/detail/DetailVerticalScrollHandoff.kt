package com.wxy.playerlite.feature.detail

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

private const val DetailVerticalGestureBiasRatio = 1.25f

@Composable
fun rememberDetailVerticalScrollHandoffConnection(
    outerScrollableState: ScrollableState,
    innerScrollableState: ScrollableState,
    canOuterConsumeUpward: () -> Boolean,
    canOuterConsumeDownward: () -> Boolean,
    remainingOuterUpwardDistancePx: () -> Float,
    remainingOuterDownwardDistancePx: () -> Float = { Float.MAX_VALUE }
): NestedScrollConnection {
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val canOuterConsumeUpwardState by rememberUpdatedState(canOuterConsumeUpward)
    val canOuterConsumeDownwardState by rememberUpdatedState(canOuterConsumeDownward)
    val remainingOuterUpwardDistancePxState by rememberUpdatedState(remainingOuterUpwardDistancePx)
    val remainingOuterDownwardDistancePxState by rememberUpdatedState(remainingOuterDownwardDistancePx)
    return remember(outerScrollableState, innerScrollableState, decayAnimationSpec) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || !available.isVerticalDominant()) {
                    return Offset.Zero
                }
                if (available.y >= 0f || !canOuterConsumeUpwardState()) {
                    return Offset.Zero
                }
                return Offset(
                    x = 0f,
                    y = outerScrollableState.dispatchVerticalGestureDelta(available.y)
                )
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || !available.isVerticalDominant()) {
                    return Offset.Zero
                }
                if (available.y <= 0f || innerScrollableState.canScrollBackward) {
                    return Offset.Zero
                }
                if (!canOuterConsumeDownwardState()) {
                    return Offset.Zero
                }
                return Offset(
                    x = 0f,
                    y = outerScrollableState.dispatchVerticalGestureDelta(available.y)
                )
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!available.isVerticalDominant()) {
                    return Velocity.Zero
                }
                if (available.y >= 0f || !canOuterConsumeUpwardState()) {
                    return Velocity.Zero
                }
                return outerScrollableState.consumeVerticalGestureFling(
                    gestureVelocityY = available.y,
                    availableDistancePx = remainingOuterUpwardDistancePxState(),
                    decayAnimationSpec = decayAnimationSpec
                )
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                if (!available.isVerticalDominant()) {
                    return Velocity.Zero
                }
                if (available.y <= 0f || innerScrollableState.canScrollBackward) {
                    return Velocity.Zero
                }
                if (!canOuterConsumeDownwardState()) {
                    return Velocity.Zero
                }
                return outerScrollableState.consumeVerticalGestureFling(
                    gestureVelocityY = available.y,
                    availableDistancePx = remainingOuterDownwardDistancePxState(),
                    decayAnimationSpec = decayAnimationSpec
                )
            }
        }
    }
}

private fun Offset.isVerticalDominant(): Boolean {
    return abs(y) > abs(x) * DetailVerticalGestureBiasRatio
}

private fun Velocity.isVerticalDominant(): Boolean {
    return abs(y) > abs(x) * DetailVerticalGestureBiasRatio
}

private fun ScrollableState.dispatchVerticalGestureDelta(deltaY: Float): Float {
    return -dispatchRawDelta(-deltaY)
}

private suspend fun ScrollableState.consumeVerticalGestureFling(
    gestureVelocityY: Float,
    availableDistancePx: Float,
    decayAnimationSpec: DecayAnimationSpec<Float>
): Velocity {
    val distanceLimitPx = availableDistancePx.coerceAtLeast(0f)
    if (abs(gestureVelocityY) < 1f || distanceLimitPx <= 0.5f) {
        return Velocity.Zero
    }

    val scrollVelocity = -gestureVelocityY
    val targetDistancePx = abs(decayAnimationSpec.calculateTargetValue(0f, scrollVelocity))
    if (targetDistancePx <= 0.5f) {
        return Velocity.Zero
    }

    val velocityFraction = if (distanceLimitPx.isFinite()) {
        (distanceLimitPx / targetDistancePx).coerceIn(0f, 1f)
    } else {
        1f
    }
    if (velocityFraction <= 0f) {
        return Velocity.Zero
    }

    val consumedGestureVelocity = gestureVelocityY * velocityFraction
    var previousValue = 0f
    var consumedDistancePx = 0f

    scroll(scrollPriority = MutatePriority.UserInput) {
        AnimationState(
            initialValue = 0f,
            initialVelocity = -consumedGestureVelocity
        ).animateDecay(decayAnimationSpec) {
            val requestedDelta = value - previousValue
            val remainingDistancePx = distanceLimitPx - abs(consumedDistancePx)
            if (distanceLimitPx.isFinite() && remainingDistancePx <= 0.5f) {
                cancelAnimation()
                return@animateDecay
            }
            val boundedDelta = if (distanceLimitPx.isFinite()) {
                requestedDelta.coerceIn(-remainingDistancePx, remainingDistancePx)
            } else {
                requestedDelta
            }
            val consumedDelta = scrollBy(boundedDelta)
            previousValue = value
            consumedDistancePx += consumedDelta
            if (abs(consumedDelta - boundedDelta) > 0.5f) {
                cancelAnimation()
            }
        }
    }

    return Velocity(x = 0f, y = consumedGestureVelocity)
}
