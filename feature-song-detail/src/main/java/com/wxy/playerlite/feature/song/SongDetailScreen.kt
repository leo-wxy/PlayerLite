package com.wxy.playerlite.feature.song

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.formatTrackDuration

@Composable
fun SongDetailScreen(
    state: SongDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onOpenLandscapeClick: () -> Unit,
    onOpenArtistClick: () -> Unit,
    onOpenAlbumClick: () -> Unit,
    onRemoveFromRecentClick: () -> Unit,
    onOpenSongClick: (String) -> Unit,
    onOpenPlaylistClick: (String) -> Unit,
    bottomOverlayPadding: Dp = 0.dp,
) {
    when (val contentState = state.contentState) {
        SongDetailContentState.Loading -> SongDetailLoadingScreen(
            onBack = onBack,
            bottomOverlayPadding = bottomOverlayPadding
        )

        is SongDetailContentState.Error -> SongDetailErrorScreen(
            message = contentState.message,
            onBack = onBack,
            onRetry = onRetry,
            bottomOverlayPadding = bottomOverlayPadding
        )

        is SongDetailContentState.Content -> SongDetailContentScreen(
            content = contentState.content,
            onBack = onBack,
            onPlayClick = onPlayClick,
            onPlayNextClick = onPlayNextClick,
            onOpenLandscapeClick = onOpenLandscapeClick,
            onOpenArtistClick = onOpenArtistClick,
            onOpenAlbumClick = onOpenAlbumClick,
            onRemoveFromRecentClick = onRemoveFromRecentClick,
            onOpenSongClick = onOpenSongClick,
            onOpenPlaylistClick = onOpenPlaylistClick,
            bottomOverlayPadding = bottomOverlayPadding
        )
    }
}

@Composable
private fun SongDetailLoadingScreen(
    onBack: () -> Unit,
    bottomOverlayPadding: Dp
) {
    SongDetailBackdropScaffold(
        onBack = onBack,
        bottomOverlayPadding = bottomOverlayPadding
    ) {
        item {
            SongDetailStateHero(
                title = "歌曲详情",
                summary = "正在加载歌曲与简要百科信息",
                modifier = Modifier.padding(top = 56.dp)
            )
        }
        item {
            DetailLoadingCard(
                text = "歌曲详情加载中",
                modifier = Modifier.testTag("song_detail_loading")
            )
        }
    }
}

@Composable
private fun SongDetailErrorScreen(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    bottomOverlayPadding: Dp
) {
    SongDetailBackdropScaffold(
        onBack = onBack,
        bottomOverlayPadding = bottomOverlayPadding
    ) {
        item {
            SongDetailStateHero(
                title = "歌曲详情",
                summary = "暂时无法展示当前歌曲的详细信息",
                modifier = Modifier.padding(top = 56.dp)
            )
        }
        item {
            DetailErrorCard(
                message = message,
                onRetry = onRetry,
                testTag = "song_detail_error",
                retryTag = "song_detail_retry"
            )
        }
    }
}

@Composable
private fun SongDetailContentScreen(
    content: SongDetailContent,
    onBack: () -> Unit,
    onPlayClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onOpenLandscapeClick: () -> Unit,
    onOpenArtistClick: () -> Unit,
    onOpenAlbumClick: () -> Unit,
    onRemoveFromRecentClick: () -> Unit,
    onOpenSongClick: (String) -> Unit,
    onOpenPlaylistClick: (String) -> Unit,
    bottomOverlayPadding: Dp,
) {
    SongDetailBackdropScaffold(
        onBack = onBack,
        imageUrl = content.coverUrl,
        bottomOverlayPadding = bottomOverlayPadding
    ) {
        item {
            SongDetailHero(
                content = content,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(top = 28.dp)
            )
        }
        item {
            SongDetailActionListSection(
            content = content,
            onPlayNextClick = onPlayNextClick,
            onOpenLandscapeClick = onOpenLandscapeClick,
            onOpenArtistClick = onOpenArtistClick,
            onOpenAlbumClick = onOpenAlbumClick,
            onRemoveFromRecentClick = onRemoveFromRecentClick
        )
        }
        content.wiki?.let { wiki ->
            if (wiki.similarSongs.isNotEmpty()) {
                item {
                    SongDetailSimilarSongsSection(
                        songs = wiki.similarSongs,
                        onOpenSongClick = onOpenSongClick,
                        modifier = Modifier.testTag("song_detail_similar_songs_section")
                    )
                }
            }
            if (wiki.relatedPlaylists.isNotEmpty()) {
                item {
                    SongDetailRelatedPlaylistsSection(
                        playlists = wiki.relatedPlaylists,
                        onOpenPlaylistClick = onOpenPlaylistClick,
                        modifier = Modifier.testTag("song_detail_related_playlists_section")
                    )
                }
            }
        }
    }
}

@Composable
private fun SongDetailBackdropScaffold(
    onBack: () -> Unit,
    imageUrl: String? = null,
    bottomOverlayPadding: Dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val contentBottomPadding = if (bottomOverlayPadding > 0.dp) {
        bottomOverlayPadding + 28.dp
    } else {
        32.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .testTag("song_detail_root")
    ) {
        SongDetailImmersiveBackground(imageUrl = imageUrl)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("detail_scaffold_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 72.dp,
                end = 20.dp,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content
        )
        SongDetailImmersiveTopBar(onBack = onBack)
    }
}

@Composable
private fun SongDetailImmersiveBackground(
    imageUrl: String?
) {
    val backdropBase = MaterialTheme.colorScheme.inverseSurface
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backdropBase,
                            backdropBase.copy(alpha = 0.96f),
                            Color.Black
                        )
                    )
                )
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.44f
                        scaleX = 1.10f
                        scaleY = 1.10f
                    }
                    .blur(56.dp),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backdropBase.copy(alpha = 0.26f),
                            backdropBase.copy(alpha = 0.58f),
                            Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        )
    }
}

@Composable
private fun SongDetailImmersiveTopBar(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onBack)
                .testTag("detail_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun SongDetailHero(
    content: SongDetailContent,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metaText = buildList {
        add(content.artistText.ifBlank { "未知歌手" })
        content.albumTitle?.takeIf { it.isNotBlank() }?.let(::add)
        add(formatTrackDuration(content.durationMs))
        add(if (content.source == SongDetailSource.ONLINE) "在线歌曲" else "本地歌曲")
    }.joinToString(separator = " · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("song_detail_hero_panel"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(248.dp)
                .testTag("song_detail_info_cover"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
            )
        ) {
            SongDetailHeroArt(
                imageUrl = content.coverUrl,
                title = content.title
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("song_detail_info_header"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("song_detail_title")
            )
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("song_detail_subtitle")
            )
        }
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .height(52.dp)
                .testTag("song_detail_primary_action"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "播放",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SongDetailActionListSection(
    content: SongDetailContent,
    onPlayNextClick: () -> Unit,
    onOpenLandscapeClick: () -> Unit,
    onOpenArtistClick: () -> Unit,
    onOpenAlbumClick: () -> Unit,
    onRemoveFromRecentClick: () -> Unit
) {
    SongDetailPanel(
        modifier = Modifier.testTag("song_detail_action_list"),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        SongDetailActionRow(
            testTag = "song_detail_secondary_action",
            icon = Icons.Rounded.SkipNext,
            label = "下一首播放",
            onClick = onPlayNextClick
        )
        SongDetailActionDivider()
        SongDetailActionRow(
            testTag = "song_detail_landscape_action",
            icon = Icons.Rounded.StayCurrentLandscape,
            label = "横屏模式",
            onClick = onOpenLandscapeClick
        )
        SongDetailActionDivider()
        SongDetailActionRow(
            testTag = "song_detail_open_artist",
            icon = Icons.Rounded.Person,
            label = "查看歌手",
            enabled = !content.primaryArtistId.isNullOrBlank(),
            onClick = onOpenArtistClick
        )
        SongDetailActionDivider()
        SongDetailActionRow(
            testTag = "song_detail_open_album",
            icon = Icons.Rounded.Album,
            label = "查看专辑",
            enabled = !content.albumId.isNullOrBlank(),
            onClick = onOpenAlbumClick
        )
        if (content.canRemoveFromRecent) {
            SongDetailActionDivider()
            SongDetailActionRow(
                testTag = "song_detail_remove_from_recent",
                icon = Icons.Rounded.DeleteOutline,
                label = "从最近播放删除",
                onClick = onRemoveFromRecentClick
            )
        }
    }
}

@Composable
private fun SongDetailActionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
    )
}

@Composable
private fun SongDetailActionRow(
    testTag: String,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.42f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.12f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SongDetailSimilarSongsSection(
    songs: List<SongDetailRelatedSongUi>,
    onOpenSongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SongDetailSection(
        title = "相似歌曲",
        modifier = modifier
    ) {
        songs.forEachIndexed { index, song ->
            if (index > 0) {
                SongDetailSectionDivider()
            }
            SongDetailRelatedSongRow(
                item = song,
                onClick = { onOpenSongClick(song.songId) }
            )
        }
    }
}

@Composable
private fun SongDetailRelatedPlaylistsSection(
    playlists: List<SongDetailRelatedPlaylistUi>,
    onOpenPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SongDetailSection(
        title = "相关歌单",
        modifier = modifier
    ) {
        playlists.forEachIndexed { index, playlist ->
            if (index > 0) {
                SongDetailSectionDivider()
            }
            SongDetailRelatedPlaylistRow(
                item = playlist,
                onClick = { onOpenPlaylistClick(playlist.playlistId) }
            )
        }
    }
}

@Composable
private fun SongDetailSection(
    title: String,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit
) {
    SongDetailPanel(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
        body()
    }
}

@Composable
private fun SongDetailPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            body()
        }
    }
}

@Composable
private fun SongDetailSectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f),
        modifier = Modifier.padding(vertical = 14.dp)
    )
}

@Composable
private fun SongDetailRelatedSongRow(
    item: SongDetailRelatedSongUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongDetailRelatedCover(
            imageUrl = item.coverUrl,
            title = item.title
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SongDetailRelatedPlaylistRow(
    item: SongDetailRelatedPlaylistUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongDetailRelatedCover(
            imageUrl = item.coverUrl,
            title = item.title
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SongDetailRelatedCover(
    imageUrl: String?,
    title: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
        )
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNullOrBlank()) {
                Text(
                    text = title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
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
}

@Composable
private fun SongDetailHeroArt(
    imageUrl: String?,
    title: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.08f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun SongDetailStateHero(
    title: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(112.dp)
                .testTag("song_detail_hero_cover"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
