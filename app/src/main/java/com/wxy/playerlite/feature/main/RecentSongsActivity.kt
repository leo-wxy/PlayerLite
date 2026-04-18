package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.player.PlayerActivity
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.feature.song.SongDetailActivity
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class RecentSongsActivity : ComponentActivity() {
    private val viewModel: RecentSongsViewModel by viewModels()
    private val songDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && SongDetailActivity.wasRemovedFromRecent(result.data)) {
            viewModel.retry()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                RecentSongsScreen(
                    state = state,
                    onBack = ::finish,
                    onLoginClick = {
                        startActivity(LoginActivity.createIntent(this@RecentSongsActivity))
                    },
                    onRetry = viewModel::retry,
                    onSelectTab = viewModel::selectTab,
                    onItemClick = { item ->
                        val songs = (state.contentState as? RecentPlaybackContentState.SongContent)
                            ?.items
                            .orEmpty()
                        playRecentSongs(items = songs, target = item)
                    },
                    onItemInsertNext = ::insertSongNext,
                    onItemOpenDetail = ::openSongDetail,
                    onItemOpenArtist = ::openArtistDetail,
                    onItemOpenAlbum = ::openAlbumDetail,
                    onLocalItemClick = { item ->
                        val localItems = (state.contentState as? RecentPlaybackContentState.LocalContent)
                            ?.items
                            .orEmpty()
                        playLocalRecent(localItems = localItems, target = item)
                    },
                    onLocalItemInsertNext = ::insertLocalNext,
                    onLocalItemOpenDetail = ::openLocalDetail,
                    onLocalItemOpenArtist = ::openArtistDetail,
                    onLocalItemOpenAlbum = ::openAlbumDetail
                )
            }
        }
    }

    private fun insertSongNext(item: RecentSongItemUiModel) {
        val inserted = AppPlaybackGraph.runtime(this)
            .insertPlaylistItemNext(item.toPlaylistItem(queueIndex = 0))
        showMessage(
            if (inserted) {
                "已加入下一首播放"
            } else {
                "当前没有可插入的播放上下文"
            }
        )
    }

    private fun openSongDetail(item: RecentSongItemUiModel) {
        val songId = (item.detailAction as? ContentEntryAction.OpenDetail)
            ?.target
            ?.let { it as? SearchRouteTarget.Song }
            ?.songId
            ?.takeIf { it.isNotBlank() }
            ?: run {
                showMessage("当前歌曲详情暂时无法打开")
                return
            }
        songDetailLauncher.launch(
            SongDetailActivity.createOnlineIntent(
                context = this,
                songId = songId
            )
        )
    }

    private fun playRecentSongs(
        items: List<RecentSongItemUiModel>,
        target: RecentSongItemUiModel
    ) {
        val activeIndex = items.indexOfFirst { it.id == target.id }
        if (activeIndex < 0) {
            showMessage("当前歌曲暂时无法播放")
            return
        }
        val started = AppPlaybackGraph.detailPlaybackGateway(this)
            .play(
                DetailPlaybackRequest(
                    items = items.mapIndexed { index, item -> item.toPlaylistItem(index) },
                    activeIndex = activeIndex
                )
            )
        if (!started) {
            showMessage("播放启动失败，请稍后重试")
            return
        }
        startActivity(createRecentPlaybackPlayerIntent(this))
    }

    private fun openArtistDetail(artistId: String) {
        startActivity(ArtistDetailActivity.createIntent(this, artistId))
    }

    private fun openAlbumDetail(albumId: String) {
        startActivity(AlbumDetailActivity.createIntent(this, albumId))
    }

    private fun playLocalRecent(
        localItems: List<RecentLocalPlaybackItemUiModel>,
        target: RecentLocalPlaybackItemUiModel
    ) {
        val activeIndex = localItems.indexOfFirst { it.recordKey == target.recordKey }
        if (activeIndex < 0) {
            showMessage("当前歌曲暂时无法播放")
            return
        }
        val started = AppPlaybackGraph.detailPlaybackGateway(this)
            .play(
                DetailPlaybackRequest(
                    items = localItems.mapIndexed { index, item -> item.toPlaylistItem(index) },
                    activeIndex = activeIndex
                )
            )
        if (!started) {
            showMessage("播放启动失败，请稍后重试")
            return
        }
        startActivity(createRecentPlaybackPlayerIntent(this))
    }

    private fun insertLocalNext(item: RecentLocalPlaybackItemUiModel) {
        val inserted = AppPlaybackGraph.runtime(this)
            .insertPlaylistItemNext(item.toPlaylistItem(queueIndex = 0))
        showMessage(
            if (inserted) {
                "已加入下一首播放"
            } else {
                "当前没有可插入的播放上下文"
            }
        )
    }

    private fun openLocalDetail(item: RecentLocalPlaybackItemUiModel) {
        val intent = if (!item.songId.isNullOrBlank()) {
            SongDetailActivity.createOnlineIntent(
                context = this,
                songId = item.songId,
                recentRecordKey = item.recordKey,
                fallbackTitle = item.title,
                fallbackArtistText = item.artistText,
                fallbackAlbumTitle = item.albumTitle,
                fallbackDurationMs = item.durationMs,
                fallbackCoverUrl = item.imageUrl,
                fallbackPrimaryArtistId = item.primaryArtistId,
                fallbackAlbumId = item.albumId
            )
        } else {
            SongDetailActivity.createLocalIntent(
                context = this,
                playbackUri = item.playbackUri,
                title = item.title,
                artistText = item.artistText,
                albumTitle = item.albumTitle.orEmpty(),
                durationMs = item.durationMs,
                coverUrl = item.imageUrl,
                recentRecordKey = item.recordKey
            )
        }
        songDetailLauncher.launch(intent)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, RecentSongsActivity::class.java)
        }
    }
}

internal fun createRecentPlaybackPlayerIntent(context: Context): Intent {
    return PlayerActivity.createIntent(
        context = context,
        startPlayback = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentSongsScreen(
    state: RecentSongsUiState,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onSelectTab: (RecentPlaybackTab) -> Unit,
    onItemClick: (RecentSongItemUiModel) -> Unit,
    onItemInsertNext: (RecentSongItemUiModel) -> Unit,
    onItemOpenDetail: (RecentSongItemUiModel) -> Unit,
    onItemOpenArtist: (String) -> Unit,
    onItemOpenAlbum: (String) -> Unit,
    onLocalItemClick: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemInsertNext: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemOpenDetail: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemOpenArtist: (String) -> Unit,
    onLocalItemOpenAlbum: (String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("最近播放") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (state.isLoggedIn || state.selectedTab == RecentPlaybackTab.LOCAL) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("recent_songs_retry_action")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            RecentPlaybackTabStrip(
                selectedTab = state.selectedTab,
                onSelectTab = onSelectTab,
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.isLoggedIn && state.selectedTab != RecentPlaybackTab.LOCAL) {
                RecentSongsLoginState(
                    onLoginClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("recent_songs_login_state")
                )
            } else {
                RecentPlaybackContent(
                    selectedTab = state.selectedTab,
                    contentState = state.contentState,
                    onRetry = onRetry,
                    onSongClick = onItemClick,
                    onSongInsertNext = onItemInsertNext,
                    onSongOpenDetail = onItemOpenDetail,
                    onSongOpenArtist = onItemOpenArtist,
                    onSongOpenAlbum = onItemOpenAlbum,
                    onLocalItemClick = onLocalItemClick,
                    onLocalItemInsertNext = onLocalItemInsertNext,
                    onLocalItemOpenDetail = onLocalItemOpenDetail,
                    onLocalItemOpenArtist = onLocalItemOpenArtist,
                    onLocalItemOpenAlbum = onLocalItemOpenAlbum,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun RecentPlaybackTabStrip(
    selectedTab: RecentPlaybackTab,
    onSelectTab: (RecentPlaybackTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RecentPlaybackTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectTab(tab) }
                    .testTag("recent_playback_tab_${tab.testTag}"),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    }
                )
            ) {
                Text(
                    text = tab.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RecentPlaybackContent(
    selectedTab: RecentPlaybackTab,
    contentState: RecentPlaybackContentState,
    onRetry: () -> Unit,
    onSongClick: (RecentSongItemUiModel) -> Unit,
    onSongInsertNext: (RecentSongItemUiModel) -> Unit,
    onSongOpenDetail: (RecentSongItemUiModel) -> Unit,
    onSongOpenArtist: (String) -> Unit,
    onSongOpenAlbum: (String) -> Unit,
    onLocalItemClick: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemInsertNext: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemOpenDetail: (RecentLocalPlaybackItemUiModel) -> Unit,
    onLocalItemOpenArtist: (String) -> Unit,
    onLocalItemOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (contentState) {
        RecentPlaybackContentState.Idle,
        RecentPlaybackContentState.Loading -> {
            RecentSongsLoadingState(
                modifier = modifier.testTag("recent_playback_loading_${selectedTab.testTag}")
            )
        }

        RecentPlaybackContentState.Empty -> {
            RecentSongsStatusState(
                title = "${selectedTab.label}最近播放为空",
                subtitle = "还没有可展示的最近播放${selectedTab.label}。",
                modifier = modifier.testTag("recent_playback_empty_${selectedTab.testTag}")
            )
        }

        is RecentPlaybackContentState.Error -> {
            RecentSongsStatusState(
                title = "${selectedTab.label}最近播放加载失败",
                subtitle = contentState.message,
                modifier = modifier.testTag("recent_playback_error_${selectedTab.testTag}")
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("recent_playback_retry_${selectedTab.testTag}")
                ) {
                    Text("重试")
                }
            }
        }

        is RecentPlaybackContentState.LocalContent -> {
            LazyColumn(
                modifier = modifier.testTag("recent_playback_list_${selectedTab.testTag}"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = contentState.items,
                    key = { _, item -> item.id }
                ) { _, item ->
                    RecentLocalPlaybackRow(
                        item = item,
                        onClick = { onLocalItemClick(item) },
                        onOpenDetail = { onLocalItemOpenDetail(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_local_item_${item.id}")
                    )
                }
            }
        }

        is RecentPlaybackContentState.SongContent -> {
            LazyColumn(
                modifier = modifier.testTag("recent_playback_list_${selectedTab.testTag}"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = contentState.items,
                    key = { _, item -> item.id }
                ) { _, item ->
                    RecentSongRow(
                        item = item,
                        onClick = { onSongClick(item) },
                        onOpenDetail = { onSongOpenDetail(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_songs_item_${item.id}")
                    )
                }
            }
        }

        is RecentPlaybackContentState.GenericContent -> {
            LazyColumn(
                modifier = modifier.testTag("recent_playback_list_${selectedTab.testTag}"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                itemsIndexed(
                    items = contentState.items,
                    key = { _, item -> item.id }
                ) { index, item ->
                    RecentPlaybackGenericRow(
                        item = item,
                        showDivider = index != contentState.items.lastIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_playback_item_${selectedTab.testTag}_${item.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentLocalPlaybackRow(
    item: RecentLocalPlaybackItemUiModel,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaText = item.albumTitle?.takeIf { it.isNotBlank() }
                    ?: item.durationMs.takeIf { it > 0 }?.let { formatDurationLabel(it) }
                if (metaText != null) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            RecentDetailEntryButton(
                testTag = "recent_local_item_more_${item.id}",
                onClick = onOpenDetail
            )
        }
    }
}

@Composable
private fun RecentSongRow(
    item: RecentSongItemUiModel,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.albumTitle?.let { albumTitle ->
                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            RecentDetailEntryButton(
                testTag = "recent_songs_item_more_${item.id}",
                onClick = onOpenDetail
            )
        }
    }
}

@Composable
private fun RecentDetailEntryButton(
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = "查看歌曲详情",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}

private fun formatDurationLabel(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun RecentPlaybackGenericRow(
    item: RecentPlaybackListItemUiModel,
    showDivider: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.imageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("recent_playback_cover_${item.id}"),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaText = listOfNotNull(item.badge, item.meta)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                    if (metaText != null) {
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .testTag("recent_playback_more_placeholder_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp, end = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
private fun RecentSongsLoginState(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "登录后才能查看最近播放",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "当前为游客浏览模式，登录后会同步你的在线播放数据。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onLoginClick) {
            Text("去登录")
        }
    }
}

@Composable
private fun RecentSongsLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RecentSongsStatusState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.let {
            Spacer(modifier = Modifier.height(18.dp))
            it()
        }
    }
}
