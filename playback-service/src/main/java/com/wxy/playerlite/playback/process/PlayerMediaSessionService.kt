package com.wxy.playerlite.playback.process

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wxy.playerlite.playback.service.R
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackLaunchRequest
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackSessionCommands
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class PlayerMediaSessionService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var playbackRuntime: PlaybackProcessRuntime
    private var mediaSession: MediaSession? = null
    private var notificationJob: Job? = null
    private var runningInForeground = false
    private var foregroundPromotionPending = false

    override fun onCreate() {
        super.onCreate()

        playbackRuntime = PlaybackProcessRuntime(
            appContext = applicationContext,
            serviceScope = serviceScope
        )
        val sessionPlayer = PlayerSessionPlayer(
            runtime = playbackRuntime,
            serviceScope = serviceScope
        )
        val sessionBuilder = MediaSession.Builder(this, sessionPlayer)
            .setId(SESSION_ID)
            .setCallback(ServiceSessionCallback(playbackRuntime))
        buildContentIntent()?.let { sessionBuilder.setSessionActivity(it) }
        mediaSession = sessionBuilder.build()

        ensureNotificationChannel()
        notificationJob = serviceScope.launch {
            playbackRuntime.state.collect { state ->
                publishSessionExtras(state)
                updateForegroundNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foregroundPromotionPending = true
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        notificationJob = null

        mediaSession?.release()
        mediaSession = null
        playbackRuntime.release()

        if (runningInForeground) {
            stopForegroundCompat()
            runningInForeground = false
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateForegroundNotification(state: PlaybackProcessState) {
        val shouldForeground = state.playbackState == PLAYBACK_STATE_PLAYING
        val hasPlayableContext = state.currentTrack != null
        val notification = buildNotification(state, isPlaying = shouldForeground)
        val controller = ForegroundNotificationController(
            startForeground = { startForeground(NOTIFICATION_ID, notification) },
            stopForeground = { stopForegroundCompat() },
            notify = { notificationManager().notify(NOTIFICATION_ID, notification) },
            cancel = { notificationManager().cancel(NOTIFICATION_ID) },
            onForegroundStartRejected = { message ->
                Log.w(TAG, "Foreground promotion rejected: $message")
            }
        )
        val result = controller.update(
            runningInForeground = runningInForeground,
            shouldForeground = shouldForeground,
            hasPlayableContext = hasPlayableContext,
            allowForegroundPromotion = foregroundPromotionPending || runningInForeground
        )
        runningInForeground = result.runningInForeground
        if (result.foregroundPromotionRejected || !shouldForeground || !hasPlayableContext) {
            foregroundPromotionPending = false
        } else if (runningInForeground) {
            foregroundPromotionPending = false
        }
    }

    private fun publishSessionExtras(state: PlaybackProcessState) {
        val session = mediaSession ?: return
        val extras = buildSessionExtras(state)
        session.setSessionExtras(extras)
    }

    private fun buildNotification(state: PlaybackProcessState, isPlaying: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(resolveNotificationSmallIcon())
            .setContentTitle(resolveNotificationTitle(state, packageName))
            .setContentText(resolveNotificationSubtitle(state))
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildContentIntent())
            .build()

    private fun buildContentIntent(): PendingIntent? {
        val launchIntent = PlaybackLaunchRequest.createPlayerActivityIntent(context = this)
        return PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background playback controls"
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private companion object {
        private const val TAG = "PlayerMediaSessionSvc"
        private const val SESSION_ID = "player-lite-main-session"
        private const val CHANNEL_ID = "player_lite_playback"
        private const val NOTIFICATION_ID = 1001
    }

    private class ServiceSessionCallback(
        private val runtime: PlaybackProcessRuntime
    ) : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val sessionCommands = appendSupportedPlaybackSessionCommands(
                baseResult.availableSessionCommands
            )
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(baseResult.availablePlayerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val handled = handlePlaybackCustomCommand(
                runtime = runtime,
                customAction = customCommand.customAction,
                args = args
            )
            return handled?.let(Futures::immediateFuture)
                ?: super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}

internal fun appendSupportedPlaybackSessionCommands(
    baseCommands: SessionCommands
): SessionCommands {
    return baseCommands
        .buildUpon()
        .add(SessionCommand(PlaybackSessionCommands.ACTION_CLEAR_CACHE, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_CACHE_LIMIT, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_SPEED, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_MODE, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_AUDIO_EFFECT_PRESET, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_PREFERRED_AUDIO_QUALITY, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG, Bundle.EMPTY))
        .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_DISPLAY_METADATA, Bundle.EMPTY))
        .build()
}

internal fun handlePlaybackCustomCommand(
    runtime: PlaybackProcessRuntime,
    customAction: String,
    args: Bundle
): SessionResult? {
    return when (customAction) {
        PlaybackSessionCommands.ACTION_CLEAR_CACHE -> {
            val success = runtime.clearCache()
            SessionResult(
                if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_UNKNOWN
            )
        }

        PlaybackSessionCommands.ACTION_SET_PLAYBACK_CACHE_LIMIT -> {
            val requested = args.getLong(
                PlaybackSessionCommands.EXTRA_PLAYBACK_CACHE_LIMIT_BYTES,
                0L
            )
            val success = runtime.setPlaybackCacheLimitBytes(requested)
            SessionResult(
                if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
            )
        }

        PlaybackSessionCommands.ACTION_SET_PLAYBACK_SPEED -> {
            val requested = args.getFloat(
                PlaybackSessionCommands.EXTRA_PLAYBACK_SPEED,
                PlaybackSpeed.DEFAULT.value
            )
            val success = runtime.setPlaybackSpeed(requested)
            SessionResult(
                if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
            )
        }

        PlaybackSessionCommands.ACTION_SET_PLAYBACK_MODE -> {
            val requested = PlaybackMode.fromWireValue(
                args.getString(PlaybackSessionCommands.EXTRA_PLAYBACK_MODE)
            )
            runtime.setPlaybackMode(requested)
            SessionResult(SessionResult.RESULT_SUCCESS)
        }

        PlaybackSessionCommands.ACTION_SET_AUDIO_EFFECT_PRESET -> {
            val requested = AudioEffectPreset.fromWireValue(
                args.getString(PlaybackSessionCommands.EXTRA_AUDIO_EFFECT_PRESET)
            )
            val success = runtime.setAudioEffectPreset(requested)
            SessionResult(
                if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
            )
        }

        PlaybackSessionCommands.ACTION_SET_PREFERRED_AUDIO_QUALITY -> {
            val requested = PlaybackAudioQuality.fromWireValue(
                args.getString(PlaybackSessionCommands.EXTRA_PREFERRED_AUDIO_QUALITY)
            )
            if (requested == null) {
                SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
            } else {
                val success = runtime.setPreferredAudioQuality(requested)
                SessionResult(
                    if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
                )
            }
        }

        PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG -> {
            val success = runtime.setActiveAudioSourceConfigJson(
                args.getString(PlaybackSessionCommands.EXTRA_ACTIVE_AUDIO_SOURCE_CONFIG_JSON)
            )
            SessionResult(
                if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
            )
        }

        PlaybackSessionCommands.ACTION_SET_DISPLAY_METADATA -> {
            runtime.setDisplayMetadata(
                title = args.getString(PlaybackSessionCommands.EXTRA_DISPLAY_TITLE),
                subtitle = args.getString(PlaybackSessionCommands.EXTRA_DISPLAY_SUBTITLE)
            )
            SessionResult(SessionResult.RESULT_SUCCESS)
        }

        else -> null
    }
}

internal fun buildSessionExtras(state: PlaybackProcessState): Bundle {
    return Bundle().apply {
        PlaybackMetadataExtras.writeStatusText(this, state.statusText)
        PlaybackMetadataExtras.writeSeekSupported(this, state.isSeekSupported)
        PlaybackMetadataExtras.writePlaybackSpeed(this, state.playbackSpeed)
        PlaybackMetadataExtras.writePlaybackMode(this, state.playbackMode)
        PlaybackMetadataExtras.writeAudioEffectPreset(this, state.audioEffectPreset)
        PlaybackMetadataExtras.writePreferredAudioQuality(this, state.preferredAudioQuality)
        state.appliedAudioQuality?.let { appliedAudioQuality ->
            PlaybackMetadataExtras.writeAppliedAudioQuality(this, appliedAudioQuality)
        }
        state.audioMeta?.let { audioMeta ->
            PlaybackMetadataExtras.writeAudioMeta(this, audioMeta)
        }
        state.cacheProgress?.let { cacheProgress ->
            PlaybackMetadataExtras.writeCacheProgress(this, cacheProgress)
        }
        state.playbackOutputInfo?.let { info ->
            PlaybackMetadataExtras.writePlaybackOutputInfo(this, info)
        }
    }
}

internal fun resolveNotificationTitle(
    state: PlaybackProcessState,
    fallbackPackageName: String
): String {
    return state.displayTitleOverride
        ?.takeIf { it.isNotBlank() }
        ?: state.currentTrack?.displayName?.ifBlank { fallbackPackageName }
        ?: fallbackPackageName
}

internal fun resolveNotificationSubtitle(state: PlaybackProcessState): String {
    return state.displaySubtitleOverride
        ?.takeIf { it.isNotBlank() }
        ?: buildSongArtistSubtitle(state.currentTrack)
        ?: state.statusText
}

internal fun buildSongArtistSubtitle(track: PlaybackTrack?): String? {
    val title = track?.displayName?.takeIf { it.isNotBlank() }
    val artist = track?.artistText?.takeIf { it.isNotBlank() }
    return when {
        title != null && artist != null -> "$title - $artist"
        title != null -> title
        artist != null -> artist
        else -> null
    }
}

internal fun resolveNotificationSmallIcon(): Int {
    return R.drawable.ic_playerlite_notification_small
}
