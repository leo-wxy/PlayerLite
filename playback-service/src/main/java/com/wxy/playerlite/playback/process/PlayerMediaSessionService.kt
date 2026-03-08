package com.wxy.playerlite.playback.process

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackSessionCommands
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

        if (!hasPlayableContext && !shouldForeground) {
            notificationManager().cancel(NOTIFICATION_ID)
            if (runningInForeground) {
                stopForegroundCompat()
                runningInForeground = false
            }
            return
        }

        val notification = buildNotification(state, isPlaying = shouldForeground)
        if (shouldForeground) {
            if (!runningInForeground) {
                startForeground(NOTIFICATION_ID, notification)
                runningInForeground = true
            } else {
                notificationManager().notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (runningInForeground) {
                stopForegroundCompat()
                runningInForeground = false
            }
            notificationManager().notify(NOTIFICATION_ID, notification)
        }
    }

    private fun publishSessionExtras(state: PlaybackProcessState) {
        val session = mediaSession ?: return
        val extras = Bundle().apply {
            PlaybackMetadataExtras.writeStatusText(this, state.statusText)
            PlaybackMetadataExtras.writeSeekSupported(this, state.isSeekSupported)
            PlaybackMetadataExtras.writePlaybackSpeed(this, state.playbackSpeed)
            state.audioMeta?.let { audioMeta ->
                PlaybackMetadataExtras.writeAudioMeta(this, audioMeta)
            }
            state.playbackOutputInfo?.let { info ->
                PlaybackMetadataExtras.writePlaybackOutputInfo(this, info)
            }
        }
        session.setSessionExtras(extras)
    }

    private fun buildNotification(state: PlaybackProcessState, isPlaying: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(
                state.currentTrack?.displayName?.ifBlank { packageName }
                    ?: packageName
            )
            .setContentText(state.statusText)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildContentIntent())
            .build()

    private fun buildContentIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            val sessionCommands = baseResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(PlaybackSessionCommands.ACTION_CLEAR_CACHE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_SPEED, Bundle.EMPTY))
                .build()
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
            return when (customCommand.customAction) {
                PlaybackSessionCommands.ACTION_CLEAR_CACHE -> {
                    val success = runtime.clearCache()
                    val resultCode = if (success) {
                        SessionResult.RESULT_SUCCESS
                    } else {
                        SessionResult.RESULT_ERROR_UNKNOWN
                    }
                    Futures.immediateFuture(SessionResult(resultCode))
                }

                PlaybackSessionCommands.ACTION_SET_PLAYBACK_SPEED -> {
                    val requested = args.getFloat(
                        PlaybackSessionCommands.EXTRA_PLAYBACK_SPEED,
                        PlaybackSpeed.DEFAULT.value
                    )
                    val success = runtime.setPlaybackSpeed(requested)
                    val resultCode = if (success) {
                        SessionResult.RESULT_SUCCESS
                    } else {
                        SessionResult.RESULT_ERROR_BAD_VALUE
                    }
                    Futures.immediateFuture(SessionResult(resultCode))
                }

                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }
    }
}
