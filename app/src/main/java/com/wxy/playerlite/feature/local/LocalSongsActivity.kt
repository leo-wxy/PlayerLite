package com.wxy.playerlite.feature.local

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class LocalSongsActivity : ComponentActivity() {
    private val viewModel: LocalSongsViewModel by viewModels {
        LocalSongsViewModel.factory(this)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionStateChanged(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onPermissionStateChanged(hasLocalSongsPermission())
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                LaunchedEffect(viewModel) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            LocalSongsUiEvent.OpenPlayer -> {
                                setResult(
                                    RESULT_OK,
                                    createOpenPlayerResultIntent()
                                )
                                finish()
                            }

                            is LocalSongsUiEvent.ShowMessage -> {
                                Toast.makeText(
                                    this@LocalSongsActivity,
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                LocalSongsScreen(
                    state = state,
                    onBack = ::finish,
                    onRequestPermission = {
                        permissionLauncher.launch(requiredLocalSongsPermission())
                    },
                    onScan = {
                        if (hasLocalSongsPermission()) {
                            viewModel.onScanRequested()
                        } else {
                            permissionLauncher.launch(requiredLocalSongsPermission())
                        }
                    },
                    onPlayAll = viewModel::playAll,
                    onSongClick = viewModel::playSong
                )
            }
        }
    }

    companion object {
        private const val EXTRA_OPEN_PLAYER = "open_player"

        fun createIntent(context: Context): Intent {
            return Intent(context, LocalSongsActivity::class.java)
        }

        fun createOpenPlayerResultIntent(): Intent {
            return Intent().putExtra(EXTRA_OPEN_PLAYER, true)
        }

        fun shouldOpenPlayerFromResult(
            resultCode: Int,
            data: Intent?
        ): Boolean {
            return resultCode == RESULT_OK &&
                data?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
        }
    }

    private fun hasLocalSongsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            requiredLocalSongsPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun requiredLocalSongsPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LocalSongsScreen(
    state: LocalSongsUiState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("本地歌曲") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onScan,
                        modifier = Modifier.testTag("local_songs_scan_action")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "扫描本地歌曲"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.requiresPermission -> {
                LocalSongsPermissionState(
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            state.isLoading -> {
                LocalSongsLoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            state.errorMessage != null && state.songs.isEmpty() -> {
                LocalSongsStatusState(
                    title = "本地歌曲加载失败",
                    subtitle = state.errorMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            state.songs.isEmpty() -> {
                LocalSongsStatusState(
                    title = "还没有扫描到本地歌曲",
                    subtitle = "点击右上角“扫描”后，这里的结果会被缓存，下次打开可直接展示。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("local_songs_list"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_songs_play_all")
                        ) {
                            Text("播放全部")
                        }
                    }
                    itemsIndexed(
                        items = state.songs,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(index) }
                                .testTag("local_songs_item_${item.id}"),
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = 2.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LibraryMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.artist} · ${item.album}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSongsPermissionState(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "需要音频读取权限",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "授权后才能扫描本机音频并缓存结果，下次打开可直接展示。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(onClick = onRequestPermission) {
            Text("去授权")
        }
    }
}

@Composable
private fun LocalSongsLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(12.dp))
        Text("正在扫描本地歌曲")
    }
}

@Composable
private fun LocalSongsStatusState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
