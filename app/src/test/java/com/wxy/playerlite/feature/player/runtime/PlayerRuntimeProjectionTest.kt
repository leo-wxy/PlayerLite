package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRuntimeProjectionTest {
    @Test
    fun updateRemotePlaybackState_updatesAudioMetaFromRemoteSnapshot() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())
        val audioMeta = AudioMetaDisplay(
            codec = "FLAC",
            sampleRate = "96000 Hz",
            channels = "2",
            bitRate = "Lossless",
            durationMs = 321_000L
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 321_000L,
            isSeekSupported = true,
            playbackSpeed = 2.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
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
            playbackOutputInfo = null,
            audioMeta = audioMeta
        )

        val state = runtime.uiStateFlow.value
        assertEquals(2.0f, state.playbackSpeed, 0f)
        assertEquals(PlaybackMode.LIST_LOOP, state.playbackMode)
        assertEquals(audioMeta, state.audioMeta)
        assertEquals("347230", state.currentSongId)
        assertEquals("晴天", state.currentTrackTitle)
        assertEquals("周杰伦", state.currentTrackArtist)
        assertEquals("https://example.com/qingtian.jpg", state.currentCoverUrl)
    }

    @Test
    fun updateRemotePlaybackState_keepsProjectedAudioMetaWhenTransitionSnapshotHasNoIdentity() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 321_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = AudioMetaDisplay(
                codec = "AAC",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "256 kbps",
                durationMs = 321_000L
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = null,
            isProgressAdvancing = false,
            currentPlayable = null,
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals("AAC", runtime.uiStateFlow.value.audioMeta.codec)
    }

    @Test
    fun updateRemotePlaybackState_clearsStaleAudioMetaWhenAuthoritativeSnapshotHasNoMetadata() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 321_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = AudioMetaDisplay(
                codec = "AAC",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "256 kbps",
                durationMs = 321_000L
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals("-", runtime.uiStateFlow.value.audioMeta.codec)
    }

    @Test
    fun updateRemotePlaybackState_keepsLocalPlaybackModeWhenRemoteHasNoCurrentMedia() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateLocalPlaybackMode(PlaybackMode.SHUFFLE)
        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = null,
            isProgressAdvancing = false,
            currentPlayable = null,
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(PlaybackMode.SHUFFLE, runtime.uiStateFlow.value.playbackMode)
    }

    @Test
    fun updateRemotePlaybackState_projectsCacheProgressFromAuthoritativeSnapshot() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(0.7f, state.displayedCacheProgressRatio ?: 0f, 0f)
        assertEquals(7_000_000L, state.cacheProgress?.cachedBytes)
    }

    @Test
    fun updateRemotePlaybackState_doesNotUseBufferedPositionWhenCacheProgressMissing() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            bufferedPositionMs = 90_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = null
        )

        val state = runtime.uiStateFlow.value
        assertEquals(90_000L, state.bufferedPositionMs)
        assertEquals(null, state.displayedCacheProgressRatio)
    }

    @Test
    fun updateRemotePlaybackState_cacheProgressBelowPlaybackProgress_shouldNotBeRaisedByPlaybackProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 120_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 3_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.3f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(0.3f, state.displayedCacheProgressRatio ?: 0f, 0f)
    }

    @Test
    fun updateRemotePlaybackState_sameTrackNullCacheProgress_shouldKeepPreviousCacheProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 42_000L,
            bufferedPositionMs = 42_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = null
        )

        val state = runtime.uiStateFlow.value
        assertEquals(7_000_000L, state.cacheProgress?.cachedBytes)
        assertEquals(0.7f, state.displayedCacheProgressRatio ?: 0f, 0f)
    }

    @Test
    fun updateRemotePlaybackState_sameTrackLowerCacheProgress_shouldKeepPreviousCacheProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 42_000L,
            bufferedPositionMs = 42_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 3_900_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.39f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(7_000_000L, state.cacheProgress?.cachedBytes)
        assertEquals(0.7f, state.displayedCacheProgressRatio ?: 0f, 0f)
    }

    @Test
    fun updateRemotePlaybackState_sameTrackSeekReanchoredCacheProgress_shouldAcceptLowerProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 120_000L,
            bufferedPositionMs = 120_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 2_000_000L,
                totalBytes = 10_000_000L,
                displayStartRatio = 0.6f,
                displayRatio = 0.62f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(2_000_000L, state.cacheProgress?.cachedBytes)
        assertEquals(0.6f, state.displayedCacheProgressStartRatio ?: 0f, 0f)
        assertEquals(0.62f, state.displayedCacheProgressRatio ?: 0f, 0f)
    }

    @Test
    fun updateRemotePlaybackState_afterLocalSeekDrag_shouldAcceptReanchoredLowerCacheProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        runtime.onSeekValueChange(120_000L)
        runtime.finishSeekDrag()

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 120_000L,
            bufferedPositionMs = 120_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 2_000_000L,
                totalBytes = 10_000_000L,
                displayStartRatio = 0.6f,
                displayRatio = 0.62f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(2_000_000L, state.cacheProgress?.cachedBytes)
        assertEquals(0.6f, state.displayedCacheProgressStartRatio ?: 0f, 0f)
        assertEquals(0.62f, state.displayedCacheProgressRatio ?: 0f, 0f)
    }

    @Test
    fun updateRemotePlaybackState_differentTrackNullCacheProgress_shouldNotReusePreviousCacheProgress() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-1",
                title = "本地文件",
                playbackUri = "file:///storage/emulated/0/test-1.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = PlaybackCacheProgressSnapshot(
                cachedBytes = 7_000_000L,
                totalBytes = 10_000_000L,
                displayRatio = 0.7f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 42_000L,
            bufferedPositionMs = 42_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-2",
            isProgressAdvancing = false,
            currentPlayable = PlayableItemSnapshot(
                id = "track-2",
                title = "本地文件 2",
                playbackUri = "file:///storage/emulated/0/test-2.mp3"
            ),
            playbackOutputInfo = null,
            audioMeta = null,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = null
        )

        val state = runtime.uiStateFlow.value
        assertEquals(null, state.cacheProgress)
        assertEquals(null, state.displayedCacheProgressRatio)
    }
}
