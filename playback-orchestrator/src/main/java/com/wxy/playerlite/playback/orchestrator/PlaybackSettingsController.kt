package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.player.AudioEffectPreset

class PlaybackSettingsController(
    private val runtime: PlaybackRuntimePort,
    private val serviceController: PlayerServiceController
) {
    fun updatePlaybackSpeed(
        playbackSpeed: Float,
        previousPlaybackSpeed: Float
    ): Boolean {
        runtime.updateLocalPlaybackSpeed(playbackSpeed)
        serviceController.connectIfNeeded()
        val accepted = serviceController.setPlaybackSpeed(playbackSpeed) { success ->
            if (!success) {
                runtime.revertPendingPlaybackSpeed(previousPlaybackSpeed)
            }
        }
        if (!accepted) {
            runtime.revertPendingPlaybackSpeed(previousPlaybackSpeed)
            runtime.setStatusText("倍速设置失败：后台播放进程未连接")
        }
        return accepted
    }

    fun updateAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        previousAudioEffectPreset: AudioEffectPreset
    ): Boolean {
        runtime.updateLocalAudioEffectPreset(audioEffectPreset)
        serviceController.connectIfNeeded()
        val accepted = serviceController.setAudioEffectPreset(audioEffectPreset) { success ->
            if (!success) {
                runtime.revertPendingAudioEffectPreset(previousAudioEffectPreset)
            }
        }
        if (!accepted) {
            runtime.revertPendingAudioEffectPreset(previousAudioEffectPreset)
            runtime.setStatusText("音效设置失败：后台播放进程未连接")
        }
        return accepted
    }
}
