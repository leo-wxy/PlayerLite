package com.wxy.playerlite.playback.process

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@UnstableApi
internal class PlayerSessionPlayer(
    private val runtime: PlaybackProcessRuntime,
    private val serviceScope: CoroutineScope
) : SimpleBasePlayer(android.os.Looper.getMainLooper()) {
    private val commandMutex = Mutex()

    init {
        serviceScope.launch(Dispatchers.Main.immediate) {
            runtime.state.collect {
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val runtimeState = runtime.state.value
        val currentTrackId = runtimeState.currentTrack?.id
        val mediaItems = runtimeState.tracks.mapIndexed { index, track ->
            val baseItem = track.toMediaItem(
                statusText = if (track.id == currentTrackId) runtimeState.statusText else null
            )
            val item = if (track.id == currentTrackId && runtimeState.playbackOutputInfo != null) {
                val extras = Bundle(baseItem.mediaMetadata.extras ?: Bundle())
                PlaybackMetadataExtras.writePlaybackOutputInfo(extras, runtimeState.playbackOutputInfo)
                baseItem.buildUpon()
                    .setMediaMetadata(baseItem.mediaMetadata.buildUpon().setExtras(extras).build())
                    .build()
            } else {
                baseItem
            }
            MediaItemData.Builder(item.mediaId.ifBlank { "item-$index" })
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setIsSeekable(index == runtimeState.activeIndex && runtimeState.durationMs > 0L)
                .setDurationUs(
                    if (index == runtimeState.activeIndex && runtimeState.durationMs > 0L) {
                        runtimeState.durationMs * 1000L
                    } else {
                        C.TIME_UNSET
                    }
                )
                .build()
        }

        val hasMediaItems = mediaItems.isNotEmpty()
        val activeIndex = runtimeState.activeIndex.takeIf { it in mediaItems.indices } ?: C.INDEX_UNSET

        val isPlaying = PlayerSessionMapping.isPlaying(runtimeState.playbackState)
        return State.Builder()
            .setAvailableCommands(
                buildAvailableCommands(
                    hasMediaItem = hasMediaItems,
                    canSeekPrevious = runtime.canMoveToPrevious(),
                    canSeekNext = runtime.canMoveToNext()
                )
            )
            .setPlaylist(mediaItems)
            .setCurrentMediaItemIndex(activeIndex)
            .setIsLoading(runtimeState.isPreparing)
            .setPlayWhenReady(runtimeState.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(
                PlayerSessionMapping.media3PlaybackState(
                    nativePlaybackState = runtimeState.playbackState,
                    hasMediaItem = hasMediaItems,
                    playWhenReady = runtimeState.playWhenReady,
                    isPreparing = runtimeState.isPreparing
                )
            )
            .setContentPositionMs(runtimeState.positionMs.coerceAtLeast(0L))
            .build()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        return runSerializedCommand {
            runtime.setQueue(mediaItems = mediaItems, startIndex = startIndex)
            if (mediaItems.isNotEmpty() && startPositionMs != C.TIME_UNSET) {
                runtime.prepareCurrent()
                runtime.seekTo(startPositionMs)
            }
        }
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        return runSerializedCommand {
            if (playWhenReady) {
                runtime.setPlayWhenReady(true)
                if (runtime.state.value.playbackState == PLAYBACK_STATE_PAUSED) {
                    runtime.resume()
                } else {
                    runtime.playCurrent()
                }
            } else {
                runtime.setPlayWhenReady(false)
                runtime.pause()
            }
        }
    }

    override fun handlePrepare(): ListenableFuture<*> {
        return runSerializedCommand {
            runtime.prepareCurrent()
        }
    }

    override fun handleStop(): ListenableFuture<*> {
        return runSerializedCommand {
            runtime.stop()
        }
    }

    override fun handleRelease(): ListenableFuture<*> {
        runtime.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        return runSerializedCommand {
            val shouldPlay = runtime.state.value.playWhenReady

            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    if (runtime.moveToNext()) {
                        if (shouldPlay) {
                            runtime.setPlayWhenReady(true)
                            runtime.playCurrent()
                        } else {
                            runtime.prepareCurrent()
                        }
                    }
                    return@runSerializedCommand
                }

                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    if (runtime.moveToPrevious()) {
                        if (shouldPlay) {
                            runtime.setPlayWhenReady(true)
                            runtime.playCurrent()
                        } else {
                            runtime.prepareCurrent()
                        }
                    }
                    return@runSerializedCommand
                }
            }

            if (mediaItemIndex != C.INDEX_UNSET && runtime.setActiveIndex(mediaItemIndex)) {
                if (shouldPlay) {
                    runtime.setPlayWhenReady(true)
                    runtime.playCurrent()
                } else {
                    runtime.prepareCurrent()
                }
            }

            if (positionMs != C.TIME_UNSET) {
                runtime.seekTo(positionMs)
            }
        }
    }

    private fun runSerializedCommand(action: suspend () -> Unit): ListenableFuture<*> {
        val future = SettableFuture.create<Void>()
        serviceScope.launch(Dispatchers.Main.immediate) {
            commandMutex.withLock {
                try {
                    action()
                    future.set(null)
                } catch (error: Throwable) {
                    future.setException(error)
                }
            }
        }
        return future
    }

    private fun buildAvailableCommands(
        hasMediaItem: Boolean,
        canSeekPrevious: Boolean,
        canSeekNext: Boolean
    ): Player.Commands {
        val builder = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_RELEASE)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)

        if (hasMediaItem) {
            builder
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        }
        if (canSeekPrevious) {
            builder
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if (canSeekNext) {
            builder
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return builder.build()
    }
}
