package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsControllerTest {
    @Test
    fun updatePlaybackSpeed_shouldApplyLocalSpeedAndForwardCommand() {
        val runtime = FakeSettingsRuntime()
        val serviceController = FakeSettingsServiceController()
        val controller = PlaybackSettingsController(
            runtime = runtime,
            serviceController = serviceController
        )

        val accepted = controller.updatePlaybackSpeed(
            playbackSpeed = 1.5f,
            previousPlaybackSpeed = 1.0f
        )

        assertTrue(accepted)
        assertEquals(1.5f, runtime.localPlaybackSpeed, 0f)
        assertEquals(listOf("connectIfNeeded", "setPlaybackSpeed(1.5)"), serviceController.actions)
    }

    @Test
    fun updateAudioEffectPreset_whenCommandRejected_shouldRollbackLocalPreset() {
        val runtime = FakeSettingsRuntime()
        val serviceController = FakeSettingsServiceController(audioEffectAccepted = false)
        val controller = PlaybackSettingsController(
            runtime = runtime,
            serviceController = serviceController
        )

        val accepted = controller.updateAudioEffectPreset(
            audioEffectPreset = AudioEffectPreset.BRIGHT,
            previousAudioEffectPreset = AudioEffectPreset.OFF
        )

        assertEquals(false, accepted)
        assertEquals(AudioEffectPreset.OFF, runtime.localAudioEffectPreset)
        assertEquals("音效设置失败：后台播放进程未连接", runtime.reportedStatusText)
        assertEquals(
            listOf("connectIfNeeded", "setAudioEffectPreset(bright)"),
            serviceController.actions
        )
    }

    @Test
    fun updatePreferredAudioQuality_shouldApplyLocalChoiceAndForwardCommand() {
        val runtime = FakeSettingsRuntime()
        val serviceController = FakeSettingsServiceController()
        val controller = PlaybackSettingsController(
            runtime = runtime,
            serviceController = serviceController
        )

        val accepted = controller.updatePreferredAudioQuality(
            audioQuality = PlaybackAudioQuality.HIRES,
            previousAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        assertTrue(accepted)
        assertEquals(PlaybackAudioQuality.HIRES, runtime.localPreferredAudioQuality)
        assertEquals(
            listOf("connectIfNeeded", "setPreferredAudioQuality(hires)"),
            serviceController.actions
        )
    }
}

private class FakeSettingsRuntime : PlaybackRuntimePort {
    var localPlaybackSpeed: Float = 1.0f
    var localAudioEffectPreset: AudioEffectPreset = AudioEffectPreset.OFF
    var localPreferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH
    var reportedStatusText: String? = null

    override fun playbackQueueItems(): List<PlaylistItem> = emptyList()

    override fun playbackQueueActiveIndex(): Int = 0

    override fun currentPlaybackMode(): PlaybackMode = PlaybackMode.LIST_LOOP

    override fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> = emptyList()

    override fun replaceQueueFromDetail(items: List<PlaylistItem>, activeIndex: Int) = Unit

    override fun updatePlaylistItemsMetadata(updatesById: Map<String, PlaylistItem>) = Unit

    override fun updateLocalPlaybackMode(playbackMode: PlaybackMode) = Unit

    override fun selectPlaylistItem(index: Int) = Unit

    override fun removePlaylistItem(index: Int) = Unit

    override fun clearPlaylist() = Unit

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) = Unit

    override fun updateLocalPlaybackSpeed(playbackSpeed: Float) {
        localPlaybackSpeed = playbackSpeed
    }

    override fun revertPendingPlaybackSpeed(playbackSpeed: Float) {
        localPlaybackSpeed = playbackSpeed
    }

    override fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        localAudioEffectPreset = audioEffectPreset
    }

    override fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        localAudioEffectPreset = audioEffectPreset
    }

    override fun updateLocalPreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        localPreferredAudioQuality = audioQuality
    }

    override fun revertPendingPreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        localPreferredAudioQuality = audioQuality
    }

    override fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        isPreparing: Boolean,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?,
        audioEffectPreset: AudioEffectPreset?,
        preferredAudioQuality: PlaybackAudioQuality?,
        appliedAudioQuality: PlaybackAudioQuality?,
        cacheProgress: PlaybackCacheProgressSnapshot?
    ) = Unit

    override fun syncActiveItemById(itemId: String?) = Unit

    override fun setStatusText(statusText: String) {
        reportedStatusText = statusText
    }
}

private class FakeSettingsServiceController(
    private val playbackSpeedAccepted: Boolean = true,
    private val audioEffectAccepted: Boolean = true,
    private val audioQualityAccepted: Boolean = true
) : PlayerServiceController {
    val actions = mutableListOf<String>()

    override fun prewarmConnection() = Unit

    override fun ensurePlaybackServiceStartedForPlayback() = Unit

    override fun connectIfNeeded() {
        actions += "connectIfNeeded"
    }

    override fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long
    ): Boolean = true

    override fun play(): Boolean = true

    override fun pause(): Boolean = true

    override fun seekTo(positionMs: Long): Boolean = true

    override fun seekToNextMediaItem(): Boolean = true

    override fun seekToPreviousMediaItem(): Boolean = true

    override fun stop(): Boolean = true

    override fun clearCache(): Boolean = true

    override fun setPlaybackCacheLimitBytes(maxBytes: Long, onResult: ((Boolean) -> Unit)?): Boolean {
        actions += "setPlaybackCacheLimitBytes($maxBytes)"
        return true
    }

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean {
        actions += "setPlaybackSpeed($speed)"
        return playbackSpeedAccepted
    }

    override fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)?
    ): Boolean {
        actions += "setAudioEffectPreset(${audioEffectPreset.name.lowercase()})"
        return audioEffectAccepted
    }

    override fun setPreferredAudioQuality(
        audioQuality: PlaybackAudioQuality,
        onResult: ((Boolean) -> Unit)?
    ): Boolean {
        actions += "setPreferredAudioQuality(${audioQuality.wireValue})"
        return audioQualityAccepted
    }

    override fun setActiveAudioSourceConfigJson(
        configJson: String?,
        onResult: ((Boolean) -> Unit)?
    ): Boolean {
        actions += "setActiveAudioSourceConfigJson(${configJson.orEmpty()})"
        return true
    }

    override fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setDisplayMetadata(title: String?, subtitle: String?): Boolean = true

    override fun currentSnapshot(): RemotePlaybackSnapshot? = null

    override fun release() = Unit
}
