package com.wxy.playerlite.feature.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.player.source.LocalFileSource
import com.wxy.playerlite.playlist.PlaylistController
import com.wxy.playerlite.playlist.PlaylistItem
import com.wxy.playerlite.playlist.PlaylistState
import com.wxy.playerlite.playlist.SharedPreferencesPlaylistStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val player: INativePlayer = NativePlayer()
    private val playlistController = PlaylistController(
        storage = SharedPreferencesPlaylistStorage(
            appContext.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
        ),
        scope = viewModelScope
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiStateFlow: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var uiState: PlayerUiState
        get() = _uiState.value
        set(value) {
            _uiState.value = value
        }

    private var playlistState: PlaylistState = PlaylistState.empty()
    private var selectedSource: IPlaysource? = null
    private var preparedItemId: String? = null
    private var shouldAutoPlayWhenPrepared = false
    private var playRequestToken: Long = 0L

    private var prepareJob: Job? = null
    private var playJob: Job? = null
    private var playbackStateJob: Job? = null

    init {
        player.setProgressListener { progressMs ->
            if (playJob == null) {
                return@setProgressListener
            }
            val normalized = progressMs.coerceAtLeast(0L)
            val bounded = if (uiState.durationMs > 0L) normalized.coerceAtMost(uiState.durationMs) else normalized
            if (!uiState.isSeekDragging) {
                uiState = uiState.copy(seekPositionMs = bounded)
            }
        }

        player.setPlaybackOutputInfoListener { info ->
            val routeText = formatPlaybackOutputInfo(info)
            Log.i(TAG, "Native playback route: $routeText")
            uiState = uiState.copy(playbackOutputInfoText = routeText)
        }

        restorePlaylistState()
        startPlaybackStateObserver()
    }

    fun onAudioPicked(uri: Uri?) {
        if (uri == null) {
            uiState = uiState.copy(statusText = "Selection canceled")
            return
        }
        addPickedUriToPlaylist(uri)
    }

    fun onTogglePlaylistSheet() {
        uiState = uiState.copy(showPlaylistSheet = !uiState.showPlaylistSheet)
    }

    fun onDismissPlaylistSheet() {
        uiState = uiState.copy(showPlaylistSheet = false)
    }

    fun onSeekValueChange(value: Long) {
        uiState = uiState.copy(
            isSeekDragging = true,
            seekDragPositionMs = value
        )
    }

    fun onSeekFinished() {
        if (uiState.isSeekDragging) {
            applySeek(uiState.seekDragPositionMs)
        }
        uiState = uiState.copy(isSeekDragging = false)
    }

    fun onHostStop() {
        playlistController.flush()
    }

    fun selectPlaylistItem(index: Int) {
        if (index !in playlistState.items.indices) {
            return
        }

        val target = playlistState.items[index]
        val isSamePrepared = playlistState.activeIndex == index && preparedItemId == target.id
        if (isSamePrepared) {
            uiState = uiState.copy(showPlaylistSheet = false)
            return
        }

        switchToPlaylistIndex(index)
        uiState = uiState.copy(showPlaylistSheet = false)
    }

    fun removePlaylistItem(index: Int) {
        val target = playlistState.items.getOrNull(index) ?: return
        val removingActive = index == playlistState.activeIndex
        val shouldContinuePlaying = uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING

        playlistState = playlistController.removeAt(index)
        syncSelectionFromPlaylist()

        if (playlistState.items.isEmpty()) {
            stopPlaybackOnly(updateStatus = false)
            releaseSelectedSource()
            resetAudioMetaState()
            uiState = uiState.copy(
                statusText = "播放列表已清空",
                showPlaylistSheet = false
            )
            return
        }

        if (removingActive) {
            shouldAutoPlayWhenPrepared = shouldContinuePlaying
            uiState = uiState.copy(
                statusText = if (shouldContinuePlaying) {
                    "已移除当前项，准备下一项..."
                } else {
                    "已移除当前项"
                }
            )
            prepareActiveItem()
            return
        }

        uiState = uiState.copy(statusText = "已移除: ${target.displayName}")
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }
        if (fromIndex !in playlistState.items.indices || toIndex !in playlistState.items.indices) {
            return
        }

        playlistState = playlistController.moveItem(fromIndex, toIndex)
        syncSelectionFromPlaylist()
    }

    fun skipToPreviousTrack() {
        val targetIndex = playlistState.activeIndex - 1
        if (targetIndex !in playlistState.items.indices) {
            uiState = uiState.copy(statusText = "已是第一首")
            return
        }
        switchToPlaylistIndex(targetIndex)
    }

    fun skipToNextTrack() {
        val targetIndex = playlistState.activeIndex + 1
        if (targetIndex !in playlistState.items.indices) {
            uiState = uiState.copy(statusText = "已是最后一首")
            return
        }
        switchToPlaylistIndex(targetIndex)
    }

    fun playSelectedAudio() {
        val activeItem = playlistState.activeItem
        if (!uiState.hasSelection || activeItem == null) {
            uiState = uiState.copy(statusText = "Pick audio first")
            return
        }

        if (uiState.isPreparing) {
            shouldAutoPlayWhenPrepared = true
            uiState = uiState.copy(statusText = "Wait for file preparation")
            return
        }

        val source = selectedSource
        if (source == null || preparedItemId != activeItem.id) {
            shouldAutoPlayWhenPrepared = true
            prepareActiveItem()
            uiState = uiState.copy(statusText = "Preparing selected track...")
            return
        }

        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            uiState = uiState.copy(statusText = "Already playing")
            return
        }

        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            uiState = uiState.copy(statusText = "Paused. Tap Resume")
            return
        }

        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            uiState = uiState.copy(statusText = "Source open failed(${sourceOpenCode.code})")
            return
        }

        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            uiState = uiState.copy(statusText = "Source rewind failed")
            return
        }

        val token = ++playRequestToken
        playJob?.cancel()
        playJob = viewModelScope.launch {
            player.stop()
            player.resume()
            uiState = uiState.copy(
                seekPositionMs = 0L,
                seekDragPositionMs = 0L,
                isSeekDragging = false,
                statusText = "Playing via native AudioTrack..."
            )

            try {
                val playCode = playFromSourceWithRetry(source)

                if (token != playRequestToken) {
                    return@launch
                }

                if (playCode == 0) {
                    uiState = uiState.copy(
                        seekPositionMs = uiState.durationMs,
                        seekDragPositionMs = uiState.durationMs
                    )
                    if (advanceToNextAfterCompletion()) {
                        return@launch
                    }
                }

                uiState = uiState.copy(
                    statusText = when (playCode) {
                        0 -> "Playback finished"
                        -2001 -> "Stopped"
                        -2005 -> "Playback already in progress"
                        -2006 -> "Seek is available only while playback is active"
                        else -> "Playback failed($playCode): ${player.lastError()}"
                    }
                )
            } catch (_: CancellationException) {
                // stopPlaybackOnly updates the UI state/status.
            } finally {
                if (token == playRequestToken) {
                    refreshPlaybackStateNow()
                }
            }
        }
    }

    fun pausePlayback() {
        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            uiState = uiState.copy(statusText = "Already paused")
            return
        }
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            uiState = uiState.copy(statusText = "Nothing is playing")
            return
        }

        val code = player.pause()
        if (code == 0) {
            uiState = uiState.copy(statusText = "Paused")
            viewModelScope.launch { refreshPlaybackStateNow() }
        } else {
            uiState = uiState.copy(statusText = "Pause failed($code): ${player.lastError()}")
        }
    }

    fun resumePlayback() {
        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            uiState = uiState.copy(statusText = "Already playing")
            return
        }
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PAUSED) {
            uiState = uiState.copy(statusText = "Nothing is playing")
            return
        }

        val code = player.resume()
        if (code == 0) {
            uiState = uiState.copy(statusText = "Playing via native AudioTrack...")
            viewModelScope.launch { refreshPlaybackStateNow() }
        } else {
            uiState = uiState.copy(statusText = "Resume failed($code): ${player.lastError()}")
        }
    }

    fun stopAll(updateStatus: Boolean) {
        prepareJob?.cancel()
        prepareJob = null
        shouldAutoPlayWhenPrepared = false
        uiState = uiState.copy(isPreparing = false)

        stopPlaybackOnly(updateStatus = updateStatus)
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs < 0L) {
            return "00:00"
        }
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun restorePlaylistState() {
        playlistState = playlistController.restore(::isPlaylistItemReadable)
        syncSelectionFromPlaylist()
        if (playlistState.activeItem != null) {
            prepareActiveItem(stopPlayback = false)
        }
    }

    private fun addPickedUriToPlaylist(uri: Uri) {
        persistReadPermission(uri)
        val displayName = queryDisplayName(uri)
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            displayName = displayName
        )
        playlistState = playlistController.addItem(item, makeActive = true)
        syncSelectionFromPlaylist()
        uiState = uiState.copy(statusText = "Added to playlist: $displayName")
        shouldAutoPlayWhenPrepared = false
        prepareActiveItem()
    }

    private fun switchToPlaylistIndex(targetIndex: Int) {
        val target = playlistState.items.getOrNull(targetIndex) ?: return
        val shouldContinuePlaying = uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        playlistState = playlistController.setActiveIndex(targetIndex)
        syncSelectionFromPlaylist()
        shouldAutoPlayWhenPrepared = shouldContinuePlaying
        uiState = uiState.copy(
            statusText = if (shouldContinuePlaying) {
                "正在切换到: ${target.displayName}"
            } else {
                "已切换到: ${target.displayName}"
            }
        )
        prepareActiveItem()
    }

    private fun prepareActiveItem(stopPlayback: Boolean = true) {
        val activeItem = playlistState.activeItem
        if (activeItem == null) {
            releaseSelectedSource()
            resetAudioMetaState()
            uiState = uiState.copy(statusText = "Pick a local audio file, then tap Play")
            return
        }

        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            if (stopPlayback) {
                stopPlaybackOnly(updateStatus = false)
            }
            releaseSelectedSource()
            uiState = uiState.copy(
                isPreparing = true,
                statusText = "Preparing file...",
                playbackOutputInfoText = "-"
            )

            try {
                val sourceUri = Uri.parse(activeItem.uri)
                val sourceFile = withContext(Dispatchers.IO) {
                    copyUriToCacheFile(sourceUri)
                }
                if (sourceFile == null) {
                    removeInvalidActiveItem(activeItem, "Failed to read audio file")
                    return@launch
                }

                val source = LocalFileSource(sourceFile)
                source.setSourceMode(IPlaysource.SourceMode.NORMAL)
                val sourceOpenCode = source.open()
                if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                    source.close()
                    removeInvalidActiveItem(activeItem, "Source open failed(${sourceOpenCode.code})")
                    return@launch
                }

                val mediaMeta = withContext(Dispatchers.Default) {
                    player.loadAudioMetaDisplayFromSource(source)
                }

                source.seek(0L, IPlaysource.SEEK_SET)

                selectedSource = source
                preparedItemId = activeItem.id
                uiState = uiState.copy(
                    seekPositionMs = 0L,
                    seekDragPositionMs = 0L,
                    isSeekDragging = false,
                    audioMeta = mediaMeta,
                    durationMs = if (mediaMeta.durationMs > 0L) mediaMeta.durationMs else 0L,
                    statusText = if (mediaMeta.durationMs > 0L) {
                        "Ready to play"
                    } else {
                        "Ready to play (duration unavailable)"
                    }
                )
            } catch (_: IllegalArgumentException) {
                removeInvalidActiveItem(activeItem, "Invalid media uri")
                return@launch
            } finally {
                uiState = uiState.copy(isPreparing = false)
            }

            if (shouldAutoPlayWhenPrepared && preparedItemId == activeItem.id) {
                shouldAutoPlayWhenPrepared = false
                playSelectedAudio()
            }
        }
    }

    private fun removeInvalidActiveItem(item: PlaylistItem, message: String) {
        playlistState = playlistController.removeItemById(item.id)
        syncSelectionFromPlaylist()
        releaseSelectedSource()
        resetAudioMetaState()
        uiState = uiState.copy(statusText = "$message，已从播放列表移除")
        if (playlistState.activeItem != null) {
            prepareActiveItem(stopPlayback = false)
        }
    }

    private fun resetAudioMetaState() {
        uiState = uiState.copy(
            audioMeta = emptyAudioMeta(),
            playbackOutputInfoText = "-",
            durationMs = 0L,
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false
        )
    }

    private fun syncSelectionFromPlaylist() {
        val activeItem = playlistState.activeItem
        uiState = uiState.copy(
            selectedFileName = activeItem?.displayName ?: "No audio selected",
            hasSelection = playlistState.items.isNotEmpty(),
            playlistItems = playlistState.items,
            activePlaylistIndex = playlistState.activeIndex,
            showPlaylistSheet = if (playlistState.items.isEmpty()) false else uiState.showPlaylistSheet
        )
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions.
        } catch (_: IllegalArgumentException) {
            // Ignore invalid permission requests.
        }
    }

    private fun isPlaylistItemReadable(item: PlaylistItem): Boolean {
        val uri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { _ -> true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun applySeek(targetMs: Long) {
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            uiState = uiState.copy(statusText = "Seek is available while playing")
            return
        }

        val maxDuration = uiState.durationMs.coerceAtLeast(0L)
        val clampedTargetMs = targetMs.coerceIn(0L, maxDuration)
        val code = player.seek(clampedTargetMs)
        if (code == 0) {
            uiState = uiState.copy(
                seekPositionMs = clampedTargetMs,
                seekDragPositionMs = clampedTargetMs,
                statusText = "Seeked to ${formatDuration(clampedTargetMs)}"
            )
        } else {
            uiState = uiState.copy(statusText = "Seek failed($code): ${player.lastError()}")
        }
    }

    private fun stopPlaybackOnly(updateStatus: Boolean) {
        selectedSource?.stop()
        player.stop()
        player.resume()

        playJob?.cancel()
        playJob = null

        uiState = uiState.copy(
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            statusText = if (updateStatus) "Stopped" else uiState.statusText
        )

        viewModelScope.launch { refreshPlaybackStateNow() }
    }

    private fun releaseSelectedSource() {
        selectedSource?.abort()
        selectedSource?.close()
        selectedSource = null
        preparedItemId = null
    }

    private fun startPlaybackStateObserver() {
        playbackStateJob?.cancel()
        playbackStateJob = viewModelScope.launch {
            while (isActive) {
                refreshPlaybackStateNow()
                delay(200)
            }
        }
    }

    private suspend fun refreshPlaybackStateNow() {
        val playState = withContext(Dispatchers.Default) {
            player.playbackState()
        }
        uiState = uiState.copy(playbackState = playState)
    }

    private fun advanceToNextAfterCompletion(): Boolean {
        val oldState = playlistState
        val nextState = playlistController.moveToNext()
        if (nextState == oldState) {
            return false
        }

        playlistState = nextState
        syncSelectionFromPlaylist()
        shouldAutoPlayWhenPrepared = true
        uiState = uiState.copy(statusText = "Track finished, preparing next...")
        prepareActiveItem(stopPlayback = false)
        return true
    }

    private suspend fun playFromSourceWithRetry(source: IPlaysource): Int {
        var lastCode = -2005
        repeat(3) { attempt ->
            val code = withContext(Dispatchers.Default) {
                player.playFromSource(source)
            }
            if (code != -2005) {
                return code
            }

            lastCode = code
            player.stop()
            player.resume()
            if (attempt < 2) {
                delay(120)
            }
        }
        return lastCode
    }

    private fun copyUriToCacheFile(uri: Uri): File? {
        val safeName = queryDisplayName(uri).replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val inputFile = File(appContext.cacheDir, "input_$safeName")

        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(inputFile).use { output ->
                source.copyTo(output)
            }
        }
        return inputFile
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return "selected_audio"
    }

    override fun onCleared() {
        stopAll(updateStatus = false)
        releaseSelectedSource()
        playlistController.flush()
        player.close()
        playbackStateJob?.cancel()
        playbackStateJob = null
        super.onCleared()
    }

    private fun formatPlaybackOutputInfo(info: PlaybackOutputInfo): String {
        val inputText = "${formatSampleRate(info.inputSampleRateHz)}/${formatChannels(info.inputChannels)}/${info.inputEncoding}"
        val outputText = "${formatSampleRate(info.outputSampleRateHz)}/${formatChannels(info.outputChannels)}/${info.outputEncoding}"
        val modeText = if (info.usesResampler) "重采样" else "直通"
        return "输入 $inputText -> 输出 $outputText ($modeText)"
    }

    private fun formatSampleRate(sampleRateHz: Int): String {
        return if (sampleRateHz > 0) {
            "${sampleRateHz}Hz"
        } else {
            "?Hz"
        }
    }

    private fun formatChannels(channels: Int): String {
        return if (channels > 0) {
            "${channels}ch"
        } else {
            "?ch"
        }
    }

    private companion object {
        private const val TAG = "PlayerViewModel"
    }
}
