package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed

internal fun PlayerUiState.withPlaybackSpeed(speed: Float): PlayerUiState {
    return copy(playbackSpeed = PlaybackSpeed.normalizeValue(speed))
}

internal fun PlayerUiState.withAudioEffectPreset(audioEffectPreset: AudioEffectPreset): PlayerUiState {
    return copy(audioEffectPreset = audioEffectPreset)
}
