package com.wxy.playerlite.feature.artist

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailHeroSummaryPreview
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.DetailSectionPlayAllButton
import com.wxy.playerlite.feature.detail.DetailTextDialog
import com.wxy.playerlite.feature.detail.createOpenPlayerAfterQueueReplacementIntent
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.previewSummaryText
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.player.runtime.RuntimeDetailPlaybackGateway
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

internal const val EXTRA_ARTIST_ID = "artist_id"

class ArtistDetailActivity : ComponentActivity() {
    private val viewModel: ArtistDetailViewModel by viewModels {
        ArtistDetailViewModel.factory(
            artistId = artistIdFrom(intent),
            repository = AppContainer.artistDetailRepository(this),
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
                ArtistDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::retry,
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
            artistId: String
        ): Intent {
            return Intent(context, ArtistDetailActivity::class.java)
                .putExtra(EXTRA_ARTIST_ID, artistId)
        }

        internal fun artistIdFrom(intent: Intent): String {
            return requireNotNull(intent.getStringExtra(EXTRA_ARTIST_ID)) {
                "Artist detail requires artist id"
            }
        }
    }
}

@Composable
internal fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    val isDescriptionVisibleState = rememberSaveable { mutableStateOf(false) }
    val isAvatarPreviewVisibleState = rememberSaveable { mutableStateOf(false) }

    when (val headerState = state.headerState) {
        ArtistDetailHeaderUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is ArtistDetailHeaderUiState.Error -> {
            DetailErrorCard(
                message = headerState.message,
                onRetry = onRetry
            )
        }

        is ArtistDetailHeaderUiState.Content -> {
            val descriptionSummary = artistDescriptionSummary(
                content = headerState.content,
                encyclopediaState = state.encyclopediaState
            )
            val descriptionBody = artistDescriptionBody(
                content = headerState.content,
                encyclopediaState = state.encyclopediaState
            )
            MusicDetailScaffold(
                heroTestTag = "artist_detail_hero_panel",
                onBack = onBack,
                heroBrush = rememberDynamicHeroBrush(
                    imageUrl = headerState.content.avatarUrl ?: headerState.content.coverUrl
                ),
                heroContent = {
                    ArtistDetailHero(
                        content = headerState.content,
                        descriptionSummary = descriptionSummary,
                        onDescriptionClick = {
                            isDescriptionVisibleState.value = true
                        },
                        onAvatarClick = {
                            isAvatarPreviewVisibleState.value = true
                        }
                    )
                }
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "热门歌曲",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("artist_hot_songs_section")
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        DetailSectionPlayAllButton(
                            onClick = onPlayAll,
                            testTag = "artist_play_all_button"
                        )
                    }
                }
                when (val hotSongsState = state.hotSongsState) {
                    ArtistHotSongsUiState.Loading -> {
                        item {
                            DetailLoadingCard(text = "热门歌曲加载中")
                        }
                    }

                    is ArtistHotSongsUiState.Error -> {
                        item {
                            ArtistHotSongsErrorCard(
                                message = hotSongsState.message,
                                onRetry = onRetry
                            )
                        }
                    }

                    ArtistHotSongsUiState.Empty -> {
                        item {
                            DetailLoadingCard(text = "暂时没有可展示的热门歌曲")
                        }
                    }

                    is ArtistHotSongsUiState.Content -> {
                        items(
                            count = hotSongsState.items.size,
                            key = { index -> hotSongsState.items[index].trackId }
                        ) { index ->
                            ArtistHotSongCard(
                                item = hotSongsState.items[index],
                                onClick = {
                                    onTrackClick(index)
                                }
                            )
                        }
                    }
                }
            }

            if (isDescriptionVisibleState.value) {
                DetailTextDialog(
                    dialogTag = "artist_description_sheet",
                    title = "歌手简介",
                    body = descriptionBody,
                    onDismiss = {
                        isDescriptionVisibleState.value = false
                    }
                )
            }

            if (isAvatarPreviewVisibleState.value) {
                ArtistAvatarPreviewDialog(
                    imageUrl = headerState.content.avatarUrl ?: headerState.content.coverUrl,
                    name = headerState.content.name,
                    onDismiss = {
                        isAvatarPreviewVisibleState.value = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistHotSongsErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("artist_hot_songs_error"),
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "热门歌曲加载失败",
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
private fun ArtistDetailHero(
    content: ArtistDetailContent,
    descriptionSummary: String,
    onDescriptionClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistAvatar(
            imageUrl = content.avatarUrl ?: content.coverUrl,
            name = content.name,
            onClick = onAvatarClick
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = content.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            if (content.aliases.isNotEmpty()) {
                Text(
                    text = content.aliases.joinToString(separator = " · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
            Text(
                text = "${content.musicCount} 首歌曲 · ${content.albumCount} 张专辑",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f)
            )
            DetailHeroSummaryPreview(
                label = "歌手简介",
                summary = previewSummaryText(descriptionSummary),
                testTag = "artist_description_preview",
                onClick = onDescriptionClick
            )
        }
    }
}

@Composable
private fun ArtistAvatar(
    imageUrl: String?,
    name: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .testTag("artist_detail_avatar"),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ArtistAvatarPreviewDialog(
    imageUrl: String?,
    name: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("artist_avatar_preview_dialog"),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (imageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("artist_avatar_preview_image"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .testTag("artist_avatar_preview_image"),
                        contentScale = ContentScale.Crop
                    )
                }
                Button(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun ArtistHotSongCard(
    item: ArtistHotSongRow,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("artist_hot_song_${item.trackId}"),
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

private fun artistDescriptionSummary(
    content: ArtistDetailContent,
    encyclopediaState: ArtistEncyclopediaUiState
): String {
    return when (encyclopediaState) {
        is ArtistEncyclopediaUiState.Content -> encyclopediaState.content.summary
            .ifBlank { content.encyclopediaSummary.ifBlank { content.briefDesc } }

        ArtistEncyclopediaUiState.Empty -> content.encyclopediaSummary.ifBlank { content.briefDesc }
        is ArtistEncyclopediaUiState.Error -> content.encyclopediaSummary.ifBlank { content.briefDesc }
        ArtistEncyclopediaUiState.Loading -> content.encyclopediaSummary.ifBlank { content.briefDesc }
    }.ifBlank {
        "暂时没有更多歌手简介。"
    }
}

private fun artistDescriptionBody(
    content: ArtistDetailContent,
    encyclopediaState: ArtistEncyclopediaUiState
): String {
    val sections = when (encyclopediaState) {
        is ArtistEncyclopediaUiState.Content -> encyclopediaState.content.sections
        else -> content.encyclopediaSections
    }
    val summary = artistDescriptionSummary(content, encyclopediaState)
    val sectionText = sections.joinToString(separator = "\n\n") { section ->
        buildString {
            if (section.title.isNotBlank()) {
                append(section.title)
                append('\n')
            }
            append(section.body)
        }
    }
    return listOf(summary, sectionText)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(separator = "\n\n")
        .ifBlank {
            "暂时没有更多歌手简介。"
        }
}
