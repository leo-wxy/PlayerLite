package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompletionActionTest {
    @Test
    fun resolve_returnsAutoNextWhenNaturalCompletionStillHasNextTrack() {
        assertEquals(
            PlaybackCompletionAction.AUTO_NEXT,
            PlaybackCompletionAction.resolve(
                playCode = 0,
                activeIndex = 0,
                trackCount = 2,
                playbackMode = PlaybackMode.LIST_LOOP
            )
        )
    }

    @Test
    fun resolve_returnsLoopToFirstWhenNaturalCompletionIsLastTrack() {
        assertEquals(
            PlaybackCompletionAction.LOOP_TO_FIRST,
            PlaybackCompletionAction.resolve(
                playCode = 0,
                activeIndex = 1,
                trackCount = 2,
                playbackMode = PlaybackMode.LIST_LOOP
            )
        )
    }

    @Test
    fun resolve_returnsRepeatCurrentWhenSingleLoopCompletes() {
        assertEquals(
            PlaybackCompletionAction.REPEAT_CURRENT,
            PlaybackCompletionAction.resolve(
                playCode = 0,
                activeIndex = 0,
                trackCount = 2,
                playbackMode = PlaybackMode.SINGLE_LOOP
            )
        )
    }

    @Test
    fun resolve_returnsStopWithErrorWhenPlaybackDidNotCompleteNaturally() {
        assertEquals(
            PlaybackCompletionAction.STOP_WITH_ERROR,
            PlaybackCompletionAction.resolve(
                playCode = -2001,
                activeIndex = 0,
                trackCount = 2,
                playbackMode = PlaybackMode.SHUFFLE
            )
        )
    }
}
