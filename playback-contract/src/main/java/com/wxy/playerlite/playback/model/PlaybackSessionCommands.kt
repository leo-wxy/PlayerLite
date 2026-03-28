package com.wxy.playerlite.playback.model

object PlaybackSessionCommands {
    const val ACTION_CLEAR_CACHE = "com.wxy.playerlite.playback.action.CLEAR_CACHE"
    const val ACTION_SET_PLAYBACK_CACHE_LIMIT =
        "com.wxy.playerlite.playback.action.SET_PLAYBACK_CACHE_LIMIT"
    const val ACTION_SET_PLAYBACK_SPEED = "com.wxy.playerlite.playback.action.SET_PLAYBACK_SPEED"
    const val ACTION_SET_PLAYBACK_MODE = "com.wxy.playerlite.playback.action.SET_PLAYBACK_MODE"
    const val ACTION_SET_AUDIO_EFFECT_PRESET = "com.wxy.playerlite.playback.action.SET_AUDIO_EFFECT_PRESET"
    const val ACTION_SET_PREFERRED_AUDIO_QUALITY = "com.wxy.playerlite.playback.action.SET_PREFERRED_AUDIO_QUALITY"
    const val ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG =
        "com.wxy.playerlite.playback.action.SET_ACTIVE_AUDIO_SOURCE_CONFIG"
    const val ACTION_SET_DISPLAY_METADATA = "com.wxy.playerlite.playback.action.SET_DISPLAY_METADATA"
    const val EXTRA_PLAYBACK_CACHE_LIMIT_BYTES = "playback_cache_limit_bytes"
    const val EXTRA_PLAYBACK_SPEED = "playback_speed"
    const val EXTRA_PLAYBACK_MODE = "playback_mode"
    const val EXTRA_AUDIO_EFFECT_PRESET = "audio_effect_preset"
    const val EXTRA_PREFERRED_AUDIO_QUALITY = "preferred_audio_quality"
    const val EXTRA_ACTIVE_AUDIO_SOURCE_CONFIG_JSON = "active_audio_source_config_json"
    const val EXTRA_DISPLAY_TITLE = "display_title"
    const val EXTRA_DISPLAY_SUBTITLE = "display_subtitle"
}
