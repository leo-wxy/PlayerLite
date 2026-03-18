package com.wxy.playerlite.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLyricsAutoScrollPolicyTest {
    @Test
    fun resolveLyricsAutoScrollTarget_shouldFollowSingleLineProgressInsteadOfWaitingForTwoLineJump() {
        assertEquals(
            1,
            resolveLyricsAutoScrollTarget(
                activeLineIndex = 4,
                firstVisibleItemIndex = 0
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollTarget_shouldAdvanceWindowWhenTargetDriftsFarEnough() {
        assertEquals(
            3,
            resolveLyricsAutoScrollTarget(
                activeLineIndex = 6,
                firstVisibleItemIndex = 0
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldSnapWhenViewportNeedsInitialPlacement() {
        assertEquals(
            LyricsAutoScrollRequest(
                targetIndex = 15,
                mode = LyricsAutoScrollMode.Snap
            ),
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 18,
                firstVisibleItemIndex = 0,
                isInitialPlacement = true
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldIgnoreSingleLinePlaybackDriftToAvoidMicroAnimations() {
        assertNull(
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 5,
                firstVisibleItemIndex = 1,
                isInitialPlacement = false
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldAnimateAfterTwoLinePlaybackDrift() {
        assertEquals(
            LyricsAutoScrollRequest(
                targetIndex = 3,
                mode = LyricsAutoScrollMode.Animate
            ),
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 6,
                firstVisibleItemIndex = 1,
                isInitialPlacement = false
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldSnapWhenPlaybackNeedsLongDistanceCatchUp() {
        assertEquals(
            LyricsAutoScrollRequest(
                targetIndex = 15,
                mode = LyricsAutoScrollMode.Snap
            ),
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 18,
                firstVisibleItemIndex = 0,
                isInitialPlacement = false
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldAnchorFollowUpAnimationToLastRequestedTarget() {
        assertNull(
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 6,
                firstVisibleItemIndex = 0,
                isInitialPlacement = false,
                2
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollRequest_shouldAnimateWhenLastRequestedTargetDriftsByTwoLines() {
        assertEquals(
            LyricsAutoScrollRequest(
                targetIndex = 4,
                mode = LyricsAutoScrollMode.Animate
            ),
            resolveLyricsAutoScrollRequest(
                activeLineIndex = 7,
                firstVisibleItemIndex = 0,
                isInitialPlacement = false,
                2
            )
        )
    }

    @Test
    fun resolveLyricsAutoScrollTarget_shouldIgnoreInvalidActiveLine() {
        assertNull(
            resolveLyricsAutoScrollTarget(
                activeLineIndex = -1,
                firstVisibleItemIndex = 0
            )
        )
    }
}
