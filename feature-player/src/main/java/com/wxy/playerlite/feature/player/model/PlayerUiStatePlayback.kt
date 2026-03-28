package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed

fun PlayerUiState.withPlaybackSpeed(speed: Float): PlayerUiState {
    return copy(playbackSpeed = PlaybackSpeed.normalizeValue(speed))
}

fun PlayerUiState.withAudioEffectPreset(audioEffectPreset: AudioEffectPreset): PlayerUiState {
    return copy(audioEffectPreset = audioEffectPreset)
}

fun PlayerUiState.withAudioQuality(
    preferredAudioQuality: PlaybackAudioQuality,
    appliedAudioQuality: PlaybackAudioQuality?
): PlayerUiState {
    return copy(
        preferredAudioQuality = preferredAudioQuality,
        appliedAudioQuality = appliedAudioQuality
    )
}
