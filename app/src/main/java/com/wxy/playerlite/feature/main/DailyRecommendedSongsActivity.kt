package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.detail.BasePlaybackDetailActivity
import com.wxy.playerlite.feature.detail.DetailBottomScrim
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.DetailSectionPlayAllButton
import com.wxy.playerlite.feature.detail.DetailStateCard
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent
import com.wxy.playerlite.feature.song.SongDetailActivity
import com.wxy.playerlite.feature.user.LoginActivity
import kotlin.math.max

private const val DAILY_RECOMMENDED_HERO_LIST_INDEX = 0
private val DAILY_RECOMMENDED_COMPACT_TOP_BAR_CONTENT_HEIGHT = 56.dp

class DailyRecommendedSongsActivity : BasePlaybackDetailActivity() {
    private val viewModel: DailyRecommendedSongsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPlaybackDetailContent { bottomOverlayPadding ->
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val headerChromeProgressState = remember { mutableFloatStateOf(0f) }
            val heroAccentColor = rememberDynamicHeroAccentColor(
                imageUrl = state.heroImageUrl()
            )
            val statusBarBlendColor = lerp(
                start = heroAccentColor,
                stop = MaterialTheme.colorScheme.background,
                fraction = headerChromeProgressState.floatValue.coerceIn(0f, 1f)
            )
            val useLightStatusBarContent = shouldUseLightStatusBarContent(statusBarBlendColor)
            val topBarContentColor = if (useLightStatusBarContent) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !useLightStatusBarContent
            }

            BackHandler(onBack = ::finish)
            LaunchedEffect(viewModel) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        DailyRecommendedSongsUiEvent.OpenPlayer -> {
                            openPlayerAfterQueueReplacement()
                        }

                        is DailyRecommendedSongsUiEvent.ShowMessage -> {
                            showMessage(event.message)
                        }
                    }
                }
            }

            DailyRecommendedSongsScreen(
                state = state,
                heroAccentColor = heroAccentColor,
                topBarContentColor = topBarContentColor,
                onBack = ::finish,
                onLoginClick = {
                    startActivity(LoginActivity.createIntent(this@DailyRecommendedSongsActivity))
                },
                onRetry = viewModel::retry,
                onPlayAll = viewModel::playAll,
                onItemClick = viewModel::playAt,
                onItemInsertNext = ::insertSongNext,
                onItemOpenDetail = ::openSongDetail,
                onItemOpenArtist = ::openArtistDetail,
                onItemOpenAlbum = ::openAlbumDetail,
                onHeaderChromeProgressChange = {
                    headerChromeProgressState.floatValue = it
                },
                bottomOverlayPadding = bottomOverlayPadding
            )
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun insertSongNext(item: DailyRecommendedSongUiModel) {
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

    private fun openSongDetail(item: DailyRecommendedSongUiModel) {
        startActivity(
            SongDetailActivity.createOnlineIntent(
                context = this,
                songId = item.songId
            )
        )
    }

    private fun openArtistDetail(artistId: String) {
        startActivity(
            ArtistDetailActivity.createIntent(
                context = this,
                artistId = artistId
            )
        )
    }

    private fun openAlbumDetail(albumId: String) {
        startActivity(
            AlbumDetailActivity.createIntent(
                context = this,
                albumId = albumId
            )
        )
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, DailyRecommendedSongsActivity::class.java)
        }
    }
}

@Composable
internal fun DailyRecommendedSongsScreen(
    state: DailyRecommendedSongsUiState,
    heroAccentColor: Color = MaterialTheme.colorScheme.primary,
    topBarContentColor: Color = MaterialTheme.colorScheme.surface,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onPlayAll: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemInsertNext: (DailyRecommendedSongUiModel) -> Unit,
    onItemOpenDetail: (DailyRecommendedSongUiModel) -> Unit,
    onItemOpenArtist: (String) -> Unit,
    onItemOpenAlbum: (String) -> Unit,
    onHeaderChromeProgressChange: ((Float) -> Unit)? = null,
    bottomOverlayPadding: Dp = 0.dp
) {
    val contentState = state.contentState
    if (state.isLoggedIn && contentState is DailyRecommendedSongsContentState.Content) {
        DailyRecommendedSongsContentScreen(
            contentState = contentState,
            heroAccentColor = heroAccentColor,
            topBarContentColor = topBarContentColor,
            onBack = onBack,
            onPlayAll = onPlayAll,
            onItemClick = onItemClick,
            onItemInsertNext = onItemInsertNext,
            onItemOpenDetail = onItemOpenDetail,
            onItemOpenArtist = onItemOpenArtist,
            onItemOpenAlbum = onItemOpenAlbum,
            onHeaderChromeProgressChange = onHeaderChromeProgressChange,
            bottomOverlayPadding = bottomOverlayPadding
        )
    } else {
        SideEffect {
            onHeaderChromeProgressChange?.invoke(0f)
        }
        DailyRecommendedSongsStateScreen(
            state = state,
            topBarContentColor = topBarContentColor,
            onBack = onBack,
            onLoginClick = onLoginClick,
            onRetry = onRetry,
            bottomOverlayPadding = bottomOverlayPadding
        )
    }
}

@Composable
private fun DailyRecommendedSongsContentScreen(
    contentState: DailyRecommendedSongsContentState.Content,
    heroAccentColor: Color,
    topBarContentColor: Color,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemInsertNext: (DailyRecommendedSongUiModel) -> Unit,
    onItemOpenDetail: (DailyRecommendedSongUiModel) -> Unit,
    onItemOpenArtist: (String) -> Unit,
    onItemOpenAlbum: (String) -> Unit,
    onHeaderChromeProgressChange: ((Float) -> Unit)?,
    bottomOverlayPadding: Dp
) {
    val listState = rememberLazyListState()
    val heroImageUrl = contentState.items.firstOrNull()?.coverUrl
    val density = LocalDensity.current
    val heroHeightPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val rawCollapseProgress by remember(listState, heroHeightPx) {
        derivedStateOf {
            if (heroHeightPx <= 0f) {
                0f
            } else {
                (listState.dailyRecommendedHeaderConsumedHeightPx() / (heroHeightPx * 0.72f))
                    .coerceIn(0f, 1f)
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = rawCollapseProgress,
        animationSpec = tween(durationMillis = 180),
        label = "dailyRecommendedHeaderCollapseProgress"
    )
    val compactTopBarHeight = rememberDailyRecommendedCompactTopBarHeight()

    LaunchedEffect(collapseProgress, onHeaderChromeProgressChange) {
        onHeaderChromeProgressChange?.invoke(collapseProgress)
    }

    DailyRecommendedSongsContentShell(
        onBack = onBack,
        listState = listState,
        bottomOverlayPadding = bottomOverlayPadding,
        heroBrush = rememberDynamicHeroBrush(imageUrl = heroImageUrl),
        overlayContent = {
            DailyRecommendedSongsCollapsingTopBar(
                accentColor = heroAccentColor,
                topBarContentColor = topBarContentColor,
                collapseProgress = collapseProgress,
                compactTopBarHeight = compactTopBarHeight,
                onBack = onBack
            )
        },
        heroContent = {
            DailyRecommendedSongsContentHero(
                items = contentState.items,
                collapseProgress = collapseProgress,
                onPlayAll = onPlayAll
            )
        }
    ) {
        item {
            DailyRecommendedSongsTracksSectionHeader(
                count = contentState.items.size,
                modifier = Modifier.testTag("daily_recommended_tracks_section")
            )
        }
        itemsIndexed(
            items = contentState.items,
            key = { _, item -> item.id }
        ) { index, item ->
            DailyRecommendedSongRow(
                item = item,
                order = index + 1,
                onClick = { onItemClick(index) },
                onInsertNext = { onItemInsertNext(item) },
                onOpenDetail = { onItemOpenDetail(item) },
                onOpenArtist = onItemOpenArtist,
                onOpenAlbum = onItemOpenAlbum
            )
        }
    }
}

@Composable
private fun DailyRecommendedSongsContentShell(
    onBack: () -> Unit,
    listState: LazyListState,
    bottomOverlayPadding: Dp,
    heroBrush: Brush,
    overlayContent: @Composable BoxScope.() -> Unit,
    heroContent: @Composable ColumnScope.() -> Unit,
    bodyContent: LazyListScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MusicDetailScaffold(
            heroTestTag = "daily_recommended_hero_panel",
            onBack = onBack,
            bottomOverlayPadding = bottomOverlayPadding,
            listState = listState,
            heroBrush = heroBrush,
            drawHeroBackground = false,
            heroHorizontalPadding = 0.dp,
            heroTopPadding = 0.dp,
            heroBottomPadding = 0.dp,
            drawHeroBehindStatusBar = true,
            showBackButton = false,
            backButtonTint = Color.Transparent,
            heroContent = heroContent,
            bodyContent = bodyContent
        )
        overlayContent()
    }
}

@Composable
private fun DailyRecommendedSongsStateScreen(
    state: DailyRecommendedSongsUiState,
    topBarContentColor: Color,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    bottomOverlayPadding: Dp
) {
    val listState = rememberLazyListState()
    val heroImageUrl = state.heroImageUrl()

    MusicDetailScaffold(
        heroTestTag = "daily_recommended_hero_panel",
        onBack = onBack,
        bottomOverlayPadding = bottomOverlayPadding,
        listState = listState,
        heroBrush = rememberDynamicHeroBrush(imageUrl = heroImageUrl),
        heroTopPadding = 70.dp,
        heroBottomPadding = 16.dp,
        drawHeroBehindStatusBar = true,
        backButtonTint = topBarContentColor,
        heroContent = {
            DailyRecommendedSongsStateHero(
                state = state,
                onLoginClick = onLoginClick
            )
        }
    ) {
        dailyRecommendedSongsStateBodyContent(
            state = state,
            onRetry = onRetry
        )
    }
}

private fun LazyListScope.dailyRecommendedSongsStateBodyContent(
    state: DailyRecommendedSongsUiState,
    onRetry: () -> Unit
) {
    val contentState = state.contentState
    when {
        !state.isLoggedIn -> {
            item {
                DetailStateCard(
                    title = "当前尚未登录",
                    body = "每日推荐歌曲依赖当前在线账号登录态，请先登录。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        contentState is DailyRecommendedSongsContentState.Loading ||
            contentState is DailyRecommendedSongsContentState.Idle -> {
            item {
                DetailLoadingCard(
                    text = "每日推荐加载中",
                    modifier = Modifier.testTag("daily_recommended_loading_state")
                )
            }
        }

        contentState is DailyRecommendedSongsContentState.Empty -> {
            item {
                DetailStateCard(
                    title = "今日推荐为空",
                    body = "当前账号还没有可展示的每日推荐歌曲。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("daily_recommended_empty_state")
                )
            }
        }

        contentState is DailyRecommendedSongsContentState.Error -> {
            item {
                DetailErrorCard(
                    message = contentState.message,
                    onRetry = onRetry,
                    testTag = "daily_recommended_error_state",
                    retryTag = "daily_recommended_retry_button"
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun DailyRecommendedSongsCollapsingTopBar(
    accentColor: Color,
    topBarContentColor: Color,
    collapseProgress: Float,
    compactTopBarHeight: Dp,
    onBack: () -> Unit
) {
    val safeTopPadding = rememberDailyRecommendedSafeTopPadding()
    val gradientAlpha = collapseProgress.coerceIn(0f, 1f)
    val titleAlpha = ((collapseProgress - 0.22f) / 0.5f).coerceIn(0f, 1f)
    val titleTranslationY = with(LocalDensity.current) { 8.dp.toPx() } * (1f - titleAlpha)
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(compactTopBarHeight)
            .testTag("daily_recommended_collapsing_top_bar")
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = gradientAlpha
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            lerp(accentColor, backgroundColor, 0.16f),
                            lerp(accentColor, backgroundColor, 0.42f),
                            backgroundColor
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = safeTopPadding, end = 8.dp)
                .height(DAILY_RECOMMENDED_COMPACT_TOP_BAR_CONTENT_HEIGHT)
        ) {
            DailyRecommendedTopChromeAction(
                testTag = "detail_back_button",
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = topBarContentColor
                )
            }
            Text(
                text = "每日推荐",
                style = MaterialTheme.typography.titleMedium,
                color = topBarContentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleTranslationY
                    }
            )
        }
    }
}

@Composable
private fun DailyRecommendedTopChromeAction(
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun DailyRecommendedSongsContentHero(
    items: List<DailyRecommendedSongUiModel>,
    collapseProgress: Float,
    onPlayAll: () -> Unit
) {
    val heroImageUrl = items.firstOrNull()?.coverUrl
    val firstReason = items.firstOrNull()?.recommendReason.orEmpty()
    val secondaryTextColor = Color.White.copy(alpha = 0.78f)
    val heroIdentityFadeProgress = ((collapseProgress - 0.04f) / 0.56f).coerceIn(0f, 1f)
    val collapsingContentAlpha = 1f - heroIdentityFadeProgress
    val collapsingTranslationY = with(LocalDensity.current) { 24.dp.toPx() } * heroIdentityFadeProgress
    val statsText = buildList {
        add("${items.size} 首歌曲")
        add("每日更新")
        add("登录账号专属")
    }.joinToString(separator = " · ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("daily_recommended_hero_cover")
        ) {
            DailyRecommendedSongsHeroArt(
                imageUrl = heroImageUrl,
                title = items.firstOrNull()?.title ?: "每日推荐"
            )
            DetailBottomScrim()
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = collapsingContentAlpha
                    translationY = collapsingTranslationY
                }
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "每日推荐",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "根据你的听歌偏好每日更新，按今天的推荐顺序播放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (firstReason.isNotBlank()) {
                    Text(
                        text = "推荐理由：$firstReason",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailSectionPlayAllButton(
                    onClick = onPlayAll,
                    testTag = "daily_recommended_hero_primary_action"
                )
            }
        }
    }
}

@Composable
private fun DailyRecommendedSongsHeroArt(
    imageUrl: String?,
    title: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
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
private fun DailyRecommendedSongsStateHero(
    state: DailyRecommendedSongsUiState,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(112.dp)
                .testTag("daily_recommended_hero_cover"),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Column(
            modifier = if (!state.isLoggedIn) {
                Modifier.testTag("daily_recommended_login_state")
            } else {
                Modifier
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "每日推荐",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (!state.isLoggedIn) {
                Text(
                    text = "登录后查看每日推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = state.stateHeroSummaryText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!state.isLoggedIn) {
            Button(
                onClick = onLoginClick,
                modifier = Modifier.testTag("daily_recommended_login_button"),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("去登录")
            }
        }
    }
}

@Composable
private fun DailyRecommendedSongsTracksSectionHeader(
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "今日推荐歌曲",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "共 $count 首，保留今日推荐顺序与推荐理由。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyRecommendedSongRow(
    item: DailyRecommendedSongUiModel,
    order: Int,
    onClick: () -> Unit,
    onInsertNext: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    val metaLine = buildList {
        if (item.artistText.isNotBlank()) {
            add(item.artistText)
        }
        item.albumTitle?.takeIf { it.isNotBlank() }?.let(::add)
        item.recommendReason?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator = " · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("daily_recommended_row_${item.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
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
                metaLine.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.durationMs > 0L) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatTrackDuration(item.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("daily_recommended_row_more_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多操作"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("下一首播放") },
                        onClick = {
                            menuExpanded = false
                            onInsertNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("查看歌曲详情") },
                        onClick = {
                            menuExpanded = false
                            onOpenDetail()
                        }
                    )
                    item.primaryArtistId?.takeIf { it.isNotBlank() }?.let { artistId ->
                        DropdownMenuItem(
                            text = { Text("查看歌手") },
                            onClick = {
                                menuExpanded = false
                                onOpenArtist(artistId)
                            }
                        )
                    }
                    item.albumId?.takeIf { it.isNotBlank() }?.let { albumId ->
                        DropdownMenuItem(
                            text = { Text("查看专辑") },
                            onClick = {
                                menuExpanded = false
                                onOpenAlbum(albumId)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun DailyRecommendedSongUiModel.toPlaylistItem(queueIndex: Int): PlaylistItem {
    return PlaylistItem(
        id = "daily-reco:$queueIndex:$songId",
        displayName = title,
        songId = songId,
        title = title,
        artistText = artistText,
        primaryArtistId = primaryArtistId,
        albumTitle = albumTitle,
        coverUrl = coverUrl,
        durationMs = durationMs,
        itemType = PlaylistItemType.ONLINE,
        contextType = "daily_recommended_songs",
        contextId = "daily-reco",
        contextTitle = "每日推荐"
    )
}

private fun DailyRecommendedSongsUiState.heroImageUrl(): String? {
    return (contentState as? DailyRecommendedSongsContentState.Content)
        ?.items
        ?.firstOrNull()
        ?.coverUrl
}

private fun DailyRecommendedSongsUiState.stateHeroSummaryText(): String {
    return when {
        !isLoggedIn -> "登录后解锁今日为你挑选的歌曲清单。"
        contentState is DailyRecommendedSongsContentState.Loading ||
            contentState is DailyRecommendedSongsContentState.Idle -> {
            "正在根据当前账号偏好整理今日推荐。"
        }

        contentState is DailyRecommendedSongsContentState.Error -> {
            "推荐列表加载失败，稍后刷新即可重新拉取。"
        }

        contentState is DailyRecommendedSongsContentState.Empty -> {
            "当前账号今日暂时没有可展示的推荐歌曲。"
        }

        else -> "根据你的听歌偏好每日更新。"
    }
}

@Composable
private fun rememberDailyRecommendedCompactTopBarHeight(): Dp {
    return rememberDailyRecommendedSafeTopPadding() + DAILY_RECOMMENDED_COMPACT_TOP_BAR_CONTENT_HEIGHT
}

@Composable
private fun rememberDailyRecommendedSafeTopPadding(): Dp {
    return WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
}

private fun LazyListState.dailyRecommendedHeaderConsumedHeightPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == DAILY_RECOMMENDED_HERO_LIST_INDEX }
    return if (heroItemInfo == null) {
        layoutInfo.viewportSize.height.toFloat()
    } else {
        (-heroItemInfo.offset).coerceAtLeast(0).toFloat()
    }
}
