package com.wxy.playerlite.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailLoadingCard

internal fun LazyListScope.artistAlbumsTabPanel(
    albumsState: ArtistAlbumsUiState,
    onRetry: () -> Unit,
    onLoadMoreAlbums: () -> Unit
) {
    when (albumsState) {
        ArtistAlbumsUiState.Loading -> {
            item {
                ArtistAlbumsTabPlaceholderCard(text = "专辑内容准备中")
            }
        }

        is ArtistAlbumsUiState.Error -> {
            item {
                DetailErrorCard(
                    message = albumsState.message,
                    onRetry = onRetry,
                    testTag = "artist_albums_error",
                    retryTag = "artist_albums_retry"
                )
            }
        }

        ArtistAlbumsUiState.Empty -> {
            item {
                ArtistAlbumsTabPlaceholderCard(text = "暂时没有可展示的专辑")
            }
        }

        is ArtistAlbumsUiState.Content -> {
            items(
                count = albumsState.items.size,
                key = { index -> albumsState.items[index].albumId }
            ) { index ->
                ArtistAlbumsTabAlbumCard(item = albumsState.items[index])
            }
            when {
                albumsState.loadMoreErrorMessage != null -> {
                    item {
                        ArtistAlbumsTabLoadMoreErrorFooter(
                            message = albumsState.loadMoreErrorMessage,
                            onRetry = onLoadMoreAlbums
                        )
                    }
                }

                albumsState.hasMore -> {
                    item {
                        ArtistAlbumsTabLoadMoreFooter(
                            isLoadingMore = albumsState.isLoadingMore,
                            onLoadMoreAlbums = onLoadMoreAlbums,
                            marker = "${albumsState.items.size}:${albumsState.hasMore}:${albumsState.isLoadingMore}"
                        )
                    }
                }

                else -> {
                    item {
                        ArtistAlbumsTabEndFooter()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistAlbumsTabPlaceholderCard(text: String) {
    DetailLoadingCard(
        text = text,
        modifier = Modifier.testTag("artist_albums_placeholder")
    )
}

@Composable
internal fun ArtistAlbumsTabAlbumCard(item: ArtistAlbumRow) {
    val meta = listOfNotNull(
        item.artistText.takeIf { it.isNotBlank() },
        item.type.takeIf { it.isNotBlank() },
        item.publishTimeText.takeIf { it.isNotBlank() },
        "${item.trackCount} 首"
    ).joinToString(separator = " · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("artist_album_${item.albumId}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ArtistAlbumsTabLoadMoreFooter(
    isLoadingMore: Boolean,
    onLoadMoreAlbums: () -> Unit,
    marker: String
) {
    if (!isLoadingMore) {
        LaunchedEffect(marker) {
            onLoadMoreAlbums()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("artist_albums_loading_more"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (isLoadingMore) "正在加载更多专辑" else "准备加载更多专辑",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ArtistAlbumsTabLoadMoreErrorFooter(
    message: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("artist_albums_load_more_error"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "专辑列表加载失败",
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

@Composable
internal fun ArtistAlbumsTabEndFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("artist_albums_end"),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "没有更多专辑了",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}
