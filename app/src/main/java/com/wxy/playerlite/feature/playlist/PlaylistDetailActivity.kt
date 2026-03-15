package com.wxy.playerlite.feature.playlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailHeroSummaryPreview
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.DetailMetaPill
import com.wxy.playerlite.feature.detail.DetailPagingFooter
import com.wxy.playerlite.feature.detail.DetailSectionPlayAllButton
import com.wxy.playerlite.feature.detail.DetailTextDialog
import com.wxy.playerlite.feature.detail.createOpenPlayerAfterQueueReplacementIntent
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.previewSummaryText
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.player.runtime.RuntimeDetailPlaybackGateway
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

internal const val EXTRA_PLAYLIST_ID = "playlist_id"

class PlaylistDetailActivity : ComponentActivity() {
    private val viewModel: PlaylistDetailViewModel by viewModels {
        PlaylistDetailViewModel.factory(
            playlistId = playlistIdFrom(intent),
            repository = AppContainer.playlistDetailRepository(this),
            playbackGateway = RuntimeDetailPlaybackGateway(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                PlaylistDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMoreTracks,
                    onPlayAll = {
                        if (viewModel.playAll()) {
                            startActivity(createOpenPlayerAfterQueueReplacementIntent(this))
                        }
                    },
                    onTrackClick = { index ->
                        if (viewModel.playTrack(index)) {
                            startActivity(createOpenPlayerAfterQueueReplacementIntent(this))
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            playlistId: String
        ): Intent {
            return Intent(context, PlaylistDetailActivity::class.java)
                .putExtra(EXTRA_PLAYLIST_ID, playlistId)
        }

        internal fun playlistIdFrom(intent: Intent): String {
            return requireNotNull(intent.getStringExtra(EXTRA_PLAYLIST_ID)) {
                "Playlist detail requires playlist id"
            }
        }
    }
}

@Composable
internal fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    var isDescriptionVisible by rememberSaveable { mutableStateOf(false) }

    when (val headerState = state.headerState) {
        PlaylistHeaderUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is PlaylistHeaderUiState.Error -> {
            DetailErrorCard(
                message = headerState.message,
                onRetry = onRetry
            )
        }

        is PlaylistHeaderUiState.Content -> {
            val description = headerState.content.description.ifBlank { "为你准备的歌单内容。" }
            MusicDetailScaffold(
                heroTestTag = "playlist_detail_hero_panel",
                onBack = onBack,
                heroBrush = rememberDynamicHeroBrush(
                    imageUrl = headerState.content.coverUrl
                ),
                heroContent = {
                    PlaylistDetailHero(
                        content = headerState.content,
                        onDescriptionClick = {
                            isDescriptionVisible = true
                        }
                    )
                }
            ) {
                item {
                    when (val dynamicState = state.dynamicState) {
                        PlaylistDynamicUiState.Loading -> {
                            DetailLoadingCard(text = "歌单动态信息加载中")
                        }

                        is PlaylistDynamicUiState.Error -> {
                            DetailErrorCard(
                                message = dynamicState.message,
                                onRetry = onRetry,
                                testTag = "playlist_dynamic_error"
                            )
                        }

                        PlaylistDynamicUiState.Empty -> {
                            DetailLoadingCard(text = "暂时没有更多歌单动态信息")
                        }

                        is PlaylistDynamicUiState.Content -> {
                            PlaylistDynamicMetaSection(content = dynamicState.content)
                        }
                    }
                }
                item {
                    PlaylistTracksSectionHeader(onPlayAll = onPlayAll)
                }
                when (val tracksState = state.tracksState) {
                    PlaylistTracksUiState.Loading -> {
                        item {
                            DetailLoadingCard(text = "歌曲列表加载中")
                        }
                    }

                    is PlaylistTracksUiState.Error -> {
                        item {
                            PlaylistTracksErrorCard(
                                message = tracksState.message,
                                onRetry = onRetry
                            )
                        }
                    }

                    PlaylistTracksUiState.Empty -> {
                        item {
                            DetailLoadingCard(text = "暂时没有可展示的歌曲")
                        }
                    }

                    is PlaylistTracksUiState.Content -> {
                        items(
                            count = tracksState.items.size,
                            key = { index -> tracksState.items[index].trackId }
                        ) { index ->
                            PlaylistTrackCard(
                                item = tracksState.items[index],
                                onClick = {
                                    onTrackClick(index)
                                }
                            )
                        }
                        item {
                            DetailPagingFooter(
                                footerTagPrefix = "playlist_tracks",
                                loadTriggerKey = tracksState.items.size,
                                isLoadingMore = tracksState.isLoadingMore,
                                loadMoreErrorMessage = tracksState.loadMoreErrorMessage,
                                endReached = tracksState.endReached,
                                loadingText = "正在加载更多歌曲",
                                endText = "歌单歌曲已全部展示",
                                onLoadMore = onLoadMore,
                                onRetry = onLoadMore
                            )
                        }
                    }
                }
            }

            if (isDescriptionVisible) {
                DetailTextDialog(
                    dialogTag = "playlist_description_sheet",
                    title = "歌单简介",
                    body = description,
                    onDismiss = {
                        isDescriptionVisible = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailHero(
    content: PlaylistHeaderContent,
    onDescriptionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCover(
            imageUrl = content.coverUrl,
            title = content.title
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailMetaPill(
                    label = "歌单作者",
                    value = content.creatorName.ifBlank { "未知" },
                    modifier = Modifier.testTag("playlist_creator_meta")
                )
                DetailMetaPill(
                    label = "歌曲数",
                    value = "${content.trackCount} 首",
                    modifier = Modifier.testTag("playlist_track_count_meta")
                )
            }
            DetailHeroSummaryPreview(
                label = "歌单简介",
                summary = previewSummaryText(content.description.ifBlank { "为你准备的歌单内容。" }),
                testTag = "playlist_description_preview",
                onClick = onDescriptionClick
            )
        }
    }
}

@Composable
private fun PlaylistTracksSectionHeader(
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "歌曲列表",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("playlist_tracks_section")
        )
        Spacer(modifier = Modifier.width(14.dp))
        DetailSectionPlayAllButton(
            onClick = onPlayAll,
            testTag = "playlist_play_all_button"
        )
    }
}

@Composable
private fun PlaylistCover(
    imageUrl: String?,
    title: String
) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .testTag("playlist_detail_cover"),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun PlaylistTracksErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("playlist_tracks_error"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "歌曲列表加载失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun PlaylistDynamicMetaSection(
    content: PlaylistDynamicInfo
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("playlist_dynamic_meta_section"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistMetricText(label = "评论", value = content.commentCount.toString())
            PlaylistMetricText(
                label = "收藏",
                value = if (content.isSubscribed) "已收藏" else "未收藏"
            )
            PlaylistMetricText(label = "播放", value = content.playCount.toString())
        }
    }
}

@Composable
private fun PlaylistMetricText(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlaylistTrackCard(
    item: PlaylistTrackRow,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("playlist_track_${item.trackId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.artistText} · ${item.albumTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatTrackDuration(item.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
