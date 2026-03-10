package com.wxy.playerlite.playback.process

import com.wxy.playerlite.player.AudioMetaDisplay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPreparationCoordinatorProtectedOnlineTest {
    @Test
    fun prepareNetworkSource_shouldRejectProtectedRequestWithoutAuthorizationContext() = runBlocking {
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                music = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "protected-1",
                    title = "Protected",
                    playbackUri = "https://example.com/protected.mp3",
                    requiresAuthorization = true
                )
            ),
            createSource = { error("source should not be opened without authorization context") },
            loadAudioMeta = {
                AudioMetaDisplay(
                    codec = "mp3",
                    sampleRate = "44100 Hz",
                    channels = "2",
                    bitRate = "128 kbps",
                    durationMs = 10_000L
                )
            }
        )

        assertTrue(result is PreparationResult.Invalid)
        assertTrue((result as PreparationResult.Invalid).message.contains("authorization"))
    }
}
