package com.wxy.playerlite.playback.client

import android.os.Bundle
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemotePlaybackSnapshotMapperTest {
    @Test
    fun map_prefersPlaybackParametersSpeedAndReadsAudioMetaFromCurrentMetadata() {
        val currentMetadataExtras = Bundle().apply {
            PlaybackMetadataExtras.writePlaybackSpeed(this, 1.5f)
            PlaybackMetadataExtras.writePlaybackMode(this, PlaybackMode.LIST_LOOP)
            PlaybackMetadataExtras.writeAudioMeta(
                this,
                AudioMetaDisplay(
                    codec = "FLAC",
                    sampleRate = "96000 Hz",
                    channels = "2",
                    bitRate = "Lossless",
                    durationMs = 321_000L
                )
            )
        }
        val sessionExtras = Bundle().apply {
            PlaybackMetadataExtras.writePlaybackSpeed(this, 1.2f)
        }

        val snapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 456L,
            durationMs = 789L,
            playbackParametersSpeed = 2.0f,
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = sessionExtras,
            rootMetadataExtras = Bundle(),
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                songId = "347230",
                title = "晴天",
                artistText = "周杰伦",
                albumTitle = "叶惠美",
                coverUrl = "https://example.com/qingtian.jpg",
                durationMs = 321_000L,
                playbackUri = "https://example.com/qingtian.mp3"
            ),
            currentMediaId = "track-1",
            statusText = "Playing"
        )

        assertEquals(2.0f, snapshot.playbackSpeed, 0f)
        assertEquals(PlaybackMode.LIST_LOOP, snapshot.playbackMode)
        assertEquals("track-1", snapshot.currentMediaId)
        assertEquals("FLAC", snapshot.audioMeta?.codec)
        assertEquals(321_000L, snapshot.audioMeta?.durationMs)
        assertNotNull(snapshot.currentPlayable)
        assertEquals("347230", snapshot.currentPlayable?.songId)
        assertEquals("https://example.com/qingtian.jpg", snapshot.currentPlayable?.coverUrl)
    }
}
