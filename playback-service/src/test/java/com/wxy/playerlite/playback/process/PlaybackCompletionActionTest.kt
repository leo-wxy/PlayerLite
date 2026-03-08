package com.wxy.playerlite.playback.process

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
                trackCount = 2
            )
        )
    }

    @Test
    fun resolve_returnsStopAtEndWhenNaturalCompletionIsLastTrack() {
        assertEquals(
            PlaybackCompletionAction.STOP_AT_END,
            PlaybackCompletionAction.resolve(
                playCode = 0,
                activeIndex = 1,
                trackCount = 2
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
                trackCount = 2
            )
        )
    }
}
