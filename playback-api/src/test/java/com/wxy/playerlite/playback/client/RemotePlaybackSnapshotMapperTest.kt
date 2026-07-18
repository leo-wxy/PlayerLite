package com.wxy.playerlite.playback.client

import android.os.Bundle
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemotePlaybackSnapshotMapperTest {
    @Test
    fun map_prefersPlaybackParametersSpeedAndReadsAudioMetaFromCurrentMetadata() {
        val currentMetadataExtras = Bundle().apply {
            PlaybackMetadataExtras.writePlaybackSpeed(this, 1.5f)
            PlaybackMetadataExtras.writePlaybackMode(this, PlaybackMode.LIST_LOOP)
            PlaybackMetadataExtras.writeAudioEffectPreset(this, AudioEffectPreset.BRIGHT)
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
        assertEquals(AudioEffectPreset.BRIGHT, snapshot.audioEffectPreset)
        assertEquals("track-1", snapshot.currentMediaId)
        assertEquals("FLAC", snapshot.audioMeta?.codec)
        assertEquals(321_000L, snapshot.audioMeta?.durationMs)
        assertNotNull(snapshot.currentPlayable)
        assertEquals("347230", snapshot.currentPlayable?.songId)
        assertEquals("https://example.com/qingtian.jpg", snapshot.currentPlayable?.coverUrl)
    }

    @Test
    fun map_fallsBackToSessionAudioEffectPresetAndLeavesMissingPresetUnset() {
        val sessionExtras = Bundle().apply {
            PlaybackMetadataExtras.writeAudioEffectPreset(this, AudioEffectPreset.WARM)
            PlaybackMetadataExtras.writePreferredAudioQuality(this, PlaybackAudioQuality.HIRES)
            PlaybackMetadataExtras.writeAppliedAudioQuality(this, PlaybackAudioQuality.LOSSLESS)
        }

        val snapshotFromSession = RemotePlaybackSnapshotMapper.map(
            playbackState = 2,
            playWhenReady = false,
            isPlaying = false,
            isSeekSupported = true,
            currentPositionMs = 0L,
            durationMs = 0L,
            playbackParametersSpeed = 0f,
            currentMetadataExtras = null,
            sessionExtras = sessionExtras,
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = null,
            statusText = null
        )
        val defaultSnapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 2,
            playWhenReady = false,
            isPlaying = false,
            isSeekSupported = true,
            currentPositionMs = 0L,
            durationMs = 0L,
            playbackParametersSpeed = 0f,
            currentMetadataExtras = null,
            sessionExtras = Bundle(),
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = null,
            statusText = null
        )

        assertEquals(AudioEffectPreset.WARM, snapshotFromSession.audioEffectPreset)
        assertEquals(PlaybackAudioQuality.HIRES, snapshotFromSession.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, snapshotFromSession.appliedAudioQuality)
        assertNull(defaultSnapshot.audioEffectPreset)
        assertNull(defaultSnapshot.preferredAudioQuality)
        assertNull(defaultSnapshot.appliedAudioQuality)
    }

    @Test
    fun map_readsCacheProgressFromMetadataExtras() {
        val currentMetadataExtras = Bundle().apply {
            PlaybackMetadataExtras.writeCacheProgress(
                this,
                PlaybackCacheProgressSnapshot(
                    cachedBytes = 4_200_000L,
                    totalBytes = 8_400_000L,
                    displayRatio = 0.5f,
                    isFullyCached = false,
                    isEstimated = false
                )
            )
        }

        val snapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 456L,
            durationMs = 789L,
            playbackParametersSpeed = 1f,
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = Bundle(),
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = "track-1",
            statusText = "Playing"
        )

        assertNotNull(snapshot.cacheProgress)
        assertEquals(4_200_000L, snapshot.cacheProgress?.cachedBytes)
        assertEquals(8_400_000L, snapshot.cacheProgress?.totalBytes)
        assertEquals(0.5f, snapshot.cacheProgress?.displayRatio ?: 0f, 0f)
    }

    @Test
    fun map_carriesBufferedPositionFromControllerSnapshot() {
        val snapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 12_000L,
            bufferedPositionMs = 48_000L,
            durationMs = 120_000L,
            playbackParametersSpeed = 1f,
            currentMetadataExtras = Bundle(),
            sessionExtras = Bundle(),
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = "track-1",
            statusText = "Playing"
        )

        assertEquals(48_000L, snapshot.bufferedPositionMs)
    }

    @Test
    fun map_prefersSessionCacheProgressOverCurrentMetadata() {
        val currentMetadataExtras = Bundle().apply {
            PlaybackMetadataExtras.writeCacheProgress(
                this,
                PlaybackCacheProgressSnapshot(
                    cachedBytes = 0L,
                    totalBytes = 720_813L,
                    displayRatio = 0.0112932045f,
                    isFullyCached = false,
                    isEstimated = true
                )
            )
        }
        val sessionExtras = Bundle().apply {
            PlaybackMetadataExtras.writeCacheProgress(
                this,
                PlaybackCacheProgressSnapshot(
                    cachedBytes = 720_813L,
                    totalBytes = 720_813L,
                    displayRatio = 1f,
                    isFullyCached = true,
                    isEstimated = true
                )
            )
        }

        val snapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 3_400L,
            durationMs = 301_066L,
            playbackParametersSpeed = 1f,
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = sessionExtras,
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = "track-1",
            statusText = "Playing"
        )

        assertNotNull(snapshot.cacheProgress)
        assertEquals(720_813L, snapshot.cacheProgress?.cachedBytes)
        assertEquals(720_813L, snapshot.cacheProgress?.totalBytes)
        assertEquals(1f, snapshot.cacheProgress?.displayRatio ?: 0f, 0f)
        assertEquals(true, snapshot.cacheProgress?.isFullyCached)
    }

    @Test
    fun map_fallsBackToCurrentMetadataCacheProgressWhenSessionExtrasMissing() {
        val currentMetadataExtras = Bundle().apply {
            PlaybackMetadataExtras.writeCacheProgress(
                this,
                PlaybackCacheProgressSnapshot(
                    cachedBytes = 180_000L,
                    totalBytes = 720_813L,
                    displayRatio = 0.25f,
                    isFullyCached = false,
                    isEstimated = false
                )
            )
        }

        val snapshot = RemotePlaybackSnapshotMapper.map(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 3_400L,
            durationMs = 301_066L,
            playbackParametersSpeed = 1f,
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = null,
            rootMetadataExtras = Bundle(),
            currentPlayable = null,
            currentMediaId = "track-1",
            statusText = "Playing"
        )

        assertNotNull(snapshot.cacheProgress)
        assertEquals(180_000L, snapshot.cacheProgress?.cachedBytes)
        assertEquals(720_813L, snapshot.cacheProgress?.totalBytes)
        assertEquals(0.25f, snapshot.cacheProgress?.displayRatio ?: 0f, 0f)
        assertEquals(false, snapshot.cacheProgress?.isFullyCached)
    }
}
