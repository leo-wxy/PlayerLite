package com.wxy.playerlite

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.lifecycle.lifecycleScope
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.player.source.LocalFileSource
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val AUDIO_TRACK_PLAYSTATE_STOPPED = 1
private const val AUDIO_TRACK_PLAYSTATE_PAUSED = 2
private const val AUDIO_TRACK_PLAYSTATE_PLAYING = 3
private const val AUDIO_TRACK_PLAYSTATE_UNAVAILABLE = -1

class MainActivity : ComponentActivity() {
    private val player: INativePlayer = NativePlayer()

    private var prepareJob: Job? = null
    private var playJob: Job? = null
    private var playbackStateJob: Job? = null

    private var selectedFileName by mutableStateOf("No audio selected")
    private var selectedSource: IPlaysource? = null
    private var hasSelection by mutableStateOf(false)

    private var statusText by mutableStateOf("Pick a local audio file, then tap Play")
    private var audioMeta by mutableStateOf(
        AudioMetaDisplay(
            codec = "-",
            sampleRate = "-",
            channels = "-",
            bitRate = "-",
            durationMs = 0L
        )
    )
    private var isPreparing by mutableStateOf(false)
    private var playbackState by mutableStateOf(AUDIO_TRACK_PLAYSTATE_UNAVAILABLE)
    private var durationMs by mutableStateOf(0L)
    private var seekPositionMs by mutableStateOf(0L)
    private var seekDragPositionMs by mutableStateOf(0L)
    private var isSeekDragging by mutableStateOf(false)
    private var playRequestToken: Long = 0L

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            statusText = "Selection canceled"
            return@registerForActivityResult
        }

        selectedFileName = queryDisplayName(uri)
        prepareSelectedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        player.setProgressListener { progressMs ->
            if (playJob == null) {
                return@setProgressListener
            }
            val normalized = progressMs.coerceAtLeast(0L)
            val bounded = if (durationMs > 0L) normalized.coerceAtMost(durationMs) else normalized
            if (!isSeekDragging) {
                seekPositionMs = bounded
            }
        }

        setContent {
            PlayerLiteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlayerScreen(
                        fileName = selectedFileName,
                        status = statusText,
                        audioMeta = audioMeta,
                        hasSelection = hasSelection,
                        isPreparing = isPreparing,
                        playbackState = playbackState,
                        seekValueMs = if (isSeekDragging) seekDragPositionMs else seekPositionMs,
                        currentDurationText = formatDuration(if (isSeekDragging) seekDragPositionMs else seekPositionMs),
                        durationMs = durationMs,
                        totalDurationText = formatDuration(durationMs),
                        modifier = Modifier.padding(innerPadding),
                        onPickAudio = {
                            pickAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        onPlay = {
                            playSelectedAudio()
                        },
                        onPause = {
                            pausePlayback()
                        },
                        onResume = {
                            resumePlayback()
                        },
                        onSeekValueChange = { value ->
                            isSeekDragging = true
                            seekDragPositionMs = value
                        },
                        onSeekFinished = {
                            if (isSeekDragging) {
                                applySeek(seekDragPositionMs)
                            }
                            isSeekDragging = false
                        },
                        onStop = {
                            stopAll(updateStatus = true)
                        }
                    )
                }
            }
        }

        startPlaybackStateObserver()
    }

    override fun onDestroy() {
        stopAll(updateStatus = false)
        releaseSelectedSource()
        player.close()
        playbackStateJob?.cancel()
        playbackStateJob = null
        super.onDestroy()
    }

    private fun prepareSelectedFile(uri: Uri) {
        prepareJob?.cancel()
        prepareJob = lifecycleScope.launch {
            stopPlaybackOnly(updateStatus = false)
            releaseSelectedSource()
            isPreparing = true
            statusText = "Preparing file..."

            try {
                val sourceFile = withContext(Dispatchers.IO) {
                    copyUriToCacheFile(uri)
                }
                if (sourceFile == null) {
                    hasSelection = false
                    audioMeta = AudioMetaDisplay(
                        codec = "-",
                        sampleRate = "-",
                        channels = "-",
                        bitRate = "-",
                        durationMs = 0L
                    )
                    durationMs = 0L
                    seekPositionMs = 0L
                    seekDragPositionMs = 0L
                    isSeekDragging = false
                    statusText = "Failed to read audio file"
                    return@launch
                }

                val source = LocalFileSource(sourceFile)
                source.setSourceMode(IPlaysource.SourceMode.NORMAL)
                val sourceOpenCode = source.open()
                if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                    source.close()
                    hasSelection = false
                    audioMeta = AudioMetaDisplay(
                        codec = "-",
                        sampleRate = "-",
                        channels = "-",
                        bitRate = "-",
                        durationMs = 0L
                    )
                    durationMs = 0L
                    statusText = "Source open failed(${sourceOpenCode.code})"
                    return@launch
                }

                val mediaMeta = withContext(Dispatchers.Default) {
                    player.loadAudioMetaDisplayFromSource(source)
                }

                source.seek(0L, IPlaysource.SEEK_SET)

                selectedSource = source
                hasSelection = true
                seekPositionMs = 0L
                seekDragPositionMs = 0L
                isSeekDragging = false
                audioMeta = mediaMeta

                if (mediaMeta.durationMs > 0L) {
                    durationMs = mediaMeta.durationMs
                    statusText = "Ready to play"
                } else {
                    durationMs = 0L
                    statusText = "Ready to play (duration unavailable)"
                }
            } finally {
                isPreparing = false
            }
        }
    }

    private fun playSelectedAudio() {
        val source = selectedSource
        if (!hasSelection || source == null) {
            statusText = "Pick audio first"
            return
        }

        if (isPreparing) {
            statusText = "Wait for file preparation"
            return
        }

        if (playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            statusText = "Already playing"
            return
        }

        if (playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            statusText = "Paused. Tap Resume"
            return
        }

        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            statusText = "Source open failed(${sourceOpenCode.code})"
            return
        }

        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            statusText = "Source rewind failed"
            return
        }

        val token = ++playRequestToken
        playJob?.cancel()
        playJob = lifecycleScope.launch {
            player.stop()
            player.resume()
            seekPositionMs = 0L
            seekDragPositionMs = 0L
            isSeekDragging = false
            statusText = "Playing via native AudioTrack..."

            try {
                val playCode = playFromSourceWithRetry(source)

                if (token != playRequestToken) {
                    return@launch
                }

                if (playCode == 0) {
                    seekPositionMs = durationMs
                    seekDragPositionMs = durationMs
                }

                statusText = when (playCode) {
                    0 -> "Playback finished"
                    -2001 -> "Stopped"
                    -2005 -> "Playback already in progress"
                    -2006 -> "Seek is available only while playback is active"
                    else -> "Playback failed($playCode): ${player.lastError()}"
                }
            } catch (_: CancellationException) {
                // stopPlaybackOnly updates the UI state/status.
            } finally {
                if (token == playRequestToken) {
                    refreshPlaybackStateNow()
                }
            }
        }
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

    private fun pausePlayback() {
        if (playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            statusText = "Already paused"
            return
        }
        if (playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            statusText = "Nothing is playing"
            return
        }

        val code = player.pause()
        if (code == 0) {
            statusText = "Paused"
            lifecycleScope.launch { refreshPlaybackStateNow() }
        } else {
            statusText = "Pause failed($code): ${player.lastError()}"
        }
    }

    private fun resumePlayback() {
        if (playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            statusText = "Already playing"
            return
        }
        if (playbackState != AUDIO_TRACK_PLAYSTATE_PAUSED) {
            statusText = "Nothing is playing"
            return
        }

        val code = player.resume()
        if (code == 0) {
            statusText = "Playing via native AudioTrack..."
            lifecycleScope.launch { refreshPlaybackStateNow() }
        } else {
            statusText = "Resume failed($code): ${player.lastError()}"
        }
    }

    private fun applySeek(targetMs: Long) {
        if (playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            statusText = "Seek is available while playing"
            return
        }

        val maxDuration = durationMs.coerceAtLeast(0L)
        val clampedTargetMs = targetMs.coerceIn(0L, maxDuration)
        val code = player.seek(clampedTargetMs)
        if (code == 0) {
            seekPositionMs = clampedTargetMs
            seekDragPositionMs = clampedTargetMs
            statusText = "Seeked to ${formatDuration(clampedTargetMs)}"
        } else {
            statusText = "Seek failed($code): ${player.lastError()}"
        }
    }

    private fun stopPlaybackOnly(updateStatus: Boolean) {
        selectedSource?.stop()
        player.stop()
        player.resume()

        playJob?.cancel()
        playJob = null

        seekPositionMs = 0L
        seekDragPositionMs = 0L
        isSeekDragging = false
        lifecycleScope.launch { refreshPlaybackStateNow() }

        if (updateStatus) {
            statusText = "Stopped"
        }
    }

    private fun stopAll(updateStatus: Boolean) {
        prepareJob?.cancel()
        prepareJob = null
        isPreparing = false

        stopPlaybackOnly(updateStatus = updateStatus)
    }

    private fun releaseSelectedSource() {
        selectedSource?.abort()
        selectedSource?.close()
        selectedSource = null
    }

    private fun startPlaybackStateObserver() {
        playbackStateJob?.cancel()
        playbackStateJob = lifecycleScope.launch {
            while (isActive) {
                refreshPlaybackStateNow()
                delay(200)
            }
        }
    }

    private suspend fun refreshPlaybackStateNow() {
        playbackState = withContext(Dispatchers.Default) {
            player.playbackState()
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs < 0L) {
            return "00:00"
        }
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun copyUriToCacheFile(uri: Uri): File? {
        val safeName = queryDisplayName(uri).replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val inputFile = File(cacheDir, "input_$safeName")

        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(inputFile).use { output ->
                source.copyTo(output)
            }
        }
        return inputFile
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return "selected_audio"
    }
}

@Composable
private fun PlayerScreen(
    fileName: String,
    status: String,
    audioMeta: AudioMetaDisplay,
    hasSelection: Boolean,
    isPreparing: Boolean,
    playbackState: Int,
    seekValueMs: Long,
    currentDurationText: String,
    durationMs: Long,
    totalDurationText: String,
    modifier: Modifier = Modifier,
    onPickAudio: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeekValueChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        val isPlaying = playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        val isPaused = playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED
        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
        val sliderValue = seekValueMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat()
        val seekEnabled = durationMs > 0L && isPlaying && !isPaused
        val progressRatio = if (durationMs > 0L) sliderValue / sliderMax else 0f
        val progressPercent = (progressRatio * 100f).toInt().coerceIn(0, 100)

        var reveal by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            reveal = true
        }
        val contentAlpha by animateFloatAsState(
            targetValue = if (reveal) 1f else 0f,
            animationSpec = tween(durationMillis = 550),
            label = "content_alpha"
        )
        val contentOffset by animateDpAsState(
            targetValue = if (reveal) 0.dp else 18.dp,
            animationSpec = tween(durationMillis = 550),
            label = "content_offset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF3DE),
                            Color(0xFFFFE6CF),
                            Color(0xFFFDEFD8)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.65f)
                    .fillMaxHeight(0.24f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x22F97316),
                                Color(0x2244B3A2)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.42f)
                    .fillMaxHeight(0.18f)
                    .clip(RoundedCornerShape(42.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x22619B8A),
                                Color(0x00FFFFFF)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "PLAYER LITE",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Text(
                                        text = "Local Audio Deck",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    PlaybackBadge(
                                        isPreparing = isPreparing,
                                        playbackState = playbackState
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DeckDisc(
                                        isPlaying = isPlaying,
                                        isPaused = isPaused,
                                        modifier = Modifier.size(108.dp)
                                    )
                                    Text(
                                        text = "$progressPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = if (isPlaying && !isPaused) "Native AudioTrack route active" else "Cue loaded and waiting",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            DeckProgressBar(
                                progressPercent = progressPercent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            )
                        }
                    }

                    FloatingPickButton(
                        enabled = !isPreparing,
                        onClick = onPickAudio,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = (-8).dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Audio Info",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Codec",
                                value = audioMeta.codec,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Sample Rate",
                                value = audioMeta.sampleRate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Channels",
                                value = audioMeta.channels,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Bit Rate",
                                value = audioMeta.bitRate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { value -> onSeekValueChange(value.toLong()) },
                            onValueChangeFinished = onSeekFinished,
                            valueRange = 0f..sliderMax,
                            enabled = seekEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$currentDurationText / $totalDurationText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                PlaybackControls(
                    hasSelection = hasSelection,
                    isPreparing = isPreparing,
                    isPlaying = isPlaying,
                    isPaused = isPaused,
                    onPlay = onPlay,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FloatingPickButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val floating = rememberInfiniteTransition(label = "pick_float_motion")
    val floatY by floating.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pick_float_y"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.94f,
        animationSpec = tween(durationMillis = 200),
        label = "pick_button_scale"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            translationY = floatY
            scaleX = buttonScale
            scaleY = buttonScale
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = "Pick File",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    hasSelection: Boolean,
    isPreparing: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleEnabled = !isPreparing && (hasSelection || isPlaying || isPaused)
    val stopEnabled = isPreparing || isPlaying || isPaused
    val showingPause = isPlaying
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.24f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "play_halo_alpha"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.06f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "play_center_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onStop,
            enabled = stopEnabled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(52.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0x20B91C1C),
                contentColor = Color(0xFFB91C1C),
                disabledContainerColor = Color(0x10B91C1C),
                disabledContentColor = Color(0x66B91C1C)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = "Stop",
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha))
            )

            FilledIconButton(
                onClick = {
                    when {
                        isPlaying -> onPause()
                        isPaused -> onResume()
                        else -> onPlay()
                    }
                },
                enabled = toggleEnabled,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = centerScale
                        scaleY = centerScale
                    },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.58f)
                )
            ) {
                Icon(
                    imageVector = if (showingPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (showingPause) "Pause" else "Play",
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            rotationZ = if (showingPause) 0f else -8f
                        }
                )
            }
        }
    }
}

@Composable
private fun DeckDisc(
    isPlaying: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val active = isPlaying && !isPaused
    val phaseTransition = rememberInfiniteTransition(label = "deck_motion")
    val spinAngle by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deck_spin"
    )

    val discScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.94f,
        animationSpec = tween(durationMillis = 300),
        label = "deck_scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = if (active) spinAngle else 14f
            scaleX = discScale
            scaleY = discScale
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF29414A),
                        Color(0xFF17242A),
                        Color(0xFF0F161B)
                    )
                ),
                radius = radius
            )
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = radius * 0.72f
            )
            drawCircle(
                color = Color(0x99FFFFFF),
                radius = radius * 0.15f
            )
        }
        Text(
            text = "AUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFFDF6EA)
        )
    }
}

@Composable
private fun DeckProgressBar(
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    val progressTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val progressStartColor = MaterialTheme.colorScheme.secondary
    val progressEndColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val ratio = (progressPercent.coerceIn(0, 100) / 100f)
        drawRoundRect(
            color = progressTrackColor,
            cornerRadius = CornerRadius(size.height, size.height)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    progressStartColor,
                    progressEndColor
                )
            ),
            size = Size(size.width * ratio, size.height),
            cornerRadius = CornerRadius(size.height, size.height)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlaybackBadge(
    isPreparing: Boolean,
    playbackState: Int
) {
    val (label, tone) = when {
        isPreparing -> "Preparing" to Color(0xFFD97706)
        playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING -> "Playing" to Color(0xFF0F766E)
        playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED -> "Paused" to Color(0xFF0E7490)
        playbackState == AUDIO_TRACK_PLAYSTATE_STOPPED -> "Stopped" to Color(0xFF475569)
        else -> "Idle" to Color(0xFF64748B)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = tone
        )
    }
}
