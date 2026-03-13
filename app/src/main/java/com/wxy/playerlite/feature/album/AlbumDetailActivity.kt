package com.wxy.playerlite.feature.album

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.wxy.playerlite.feature.detail.DetailTextDialog
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.previewSummaryText
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

internal const val EXTRA_ALBUM_ID = "album_id"

class AlbumDetailActivity : ComponentActivity() {
    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(
            albumId = albumIdFrom(intent),
            repository = AppContainer.albumDetailRepository(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                AlbumDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMoreTracks
                )
            }
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            albumId: String
        ): Intent {
            return Intent(context, AlbumDetailActivity::class.java)
                .putExtra(EXTRA_ALBUM_ID, albumId)
        }

        internal fun albumIdFrom(intent: Intent): String {
            return requireNotNull(intent.getStringExtra(EXTRA_ALBUM_ID)) {
                "Album detail requires album id"
            }
        }
    }
}

@Composable
internal fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    var isDescriptionVisible by rememberSaveable { mutableStateOf(false) }

    when (val contentState = state.contentState) {
        AlbumContentUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }

        is AlbumContentUiState.Error -> {
            DetailErrorCard(
                message = contentState.message,
                onRetry = onRetry
            )
        }

        is AlbumContentUiState.Content -> {
            MusicDetailScaffold(
                heroTestTag = "album_detail_hero_panel",
                onBack = onBack,
                heroBrush = rememberDynamicHeroBrush(
                    imageUrl = contentState.content.coverUrl
                ),
                heroContent = {
                    AlbumDetailHero(
                        content = contentState.content,
                        onDescriptionClick = {
                            isDescriptionVisible = true
                        }
                    )
                }
            ) {
                item {
                    when (val dynamicState = state.dynamicState) {
                        AlbumDynamicUiState.Loading -> {
                            DetailLoadingCard(text = "专辑动态信息加载中")
                        }

                        is AlbumDynamicUiState.Error -> {
                            DetailErrorCard(
                                message = dynamicState.message,
                                onRetry = onRetry,
                                testTag = "album_dynamic_error"
                            )
                        }

                        AlbumDynamicUiState.Empty -> {
                            DetailLoadingCard(text = "暂时没有更多专辑动态信息")
                        }

                        is AlbumDynamicUiState.Content -> {
                            AlbumDynamicMetaSection(content = dynamicState.content)
                        }
                    }
                }
                if (contentState.content.tracks.isEmpty()) {
                    item {
                        DetailLoadingCard(text = "暂时没有可展示的歌曲")
                    }
                } else {
                    items(
                        items = contentState.content.tracks,
                        key = { item -> item.trackId }
                    ) { item ->
                        AlbumTrackCard(item = item)
                    }
                    item {
                        DetailPagingFooter(
                            footerTagPrefix = "album_tracks",
                            loadTriggerKey = contentState.content.tracks.size,
                            isLoadingMore = contentState.isLoadingMore,
                            loadMoreErrorMessage = contentState.loadMoreErrorMessage,
                            endReached = contentState.endReached,
                            loadingText = "正在加载更多歌曲",
                            endText = "专辑曲目已全部展示",
                            onLoadMore = onLoadMore,
                            onRetry = onLoadMore
                        )
                    }
                }
            }

            if (isDescriptionVisible) {
                DetailTextDialog(
                    dialogTag = "album_description_sheet",
                    title = "专辑简介",
                    body = contentState.content.description.ifBlank { "暂时没有更多专辑简介。" },
                    onDismiss = {
                        isDescriptionVisible = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailHero(
    content: AlbumDetailContent,
    onDescriptionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
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
                    label = "歌手",
                    value = content.artistText.ifBlank { "未知" }
                )
                DetailMetaPill(
                    label = "歌曲数",
                    value = "${content.trackCount} 首"
                )
            }
            Text(
                text = listOf(content.company, content.publishTimeText)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = " · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DetailHeroSummaryPreview(
                label = "专辑简介",
                summary = previewSummaryText(content.description.ifBlank { "暂时没有更多专辑简介。" }),
                testTag = "album_description_preview",
                onClick = onDescriptionClick
            )
        }
    }
}

@Composable
private fun AlbumCover(
    imageUrl: String?,
    title: String
) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .testTag("album_detail_cover"),
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
private fun AlbumDynamicMetaSection(
    content: AlbumDynamicInfo
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("album_dynamic_meta_section"),
        shape = RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
            AlbumMetricText(label = "评论", value = content.commentCount.toString())
            AlbumMetricText(label = "分享", value = content.shareCount.toString())
            AlbumMetricText(label = "收藏", value = content.subscribedCount.toString())
        }
    }
}

@Composable
private fun AlbumMetricText(
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
private fun AlbumTrackCard(
    item: AlbumTrackRow
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag("album_track_${item.trackId}"),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
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
