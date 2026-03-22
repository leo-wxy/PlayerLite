package com.wxy.playerlite.feature.artist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.DetailBottomScrim
import com.wxy.playerlite.feature.detail.rememberDetailVerticalScrollHandoffConnection
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class ArtistDetailTab(
    val title: String,
    val testTag: String
) {
    HOT_SONGS("热门歌曲", "artist_tab_hot_songs"),
    ALBUMS("专辑", "artist_tab_albums"),
    ENCYCLOPEDIA("百科", "artist_tab_encyclopedia")
}

private const val ARTIST_HERO_LIST_INDEX = 0
private val ARTIST_COMPACT_TOP_BAR_CONTENT_HEIGHT = 56.dp

fun formatArtistFansCount(fansCount: Long): String {
    val normalized = fansCount.coerceAtLeast(0L)
    if (normalized < 10_000L) {
        return normalized.toString()
    }
    val formatted = String.format(Locale.US, "%.1f", normalized / 10_000.0)
        .removeSuffix(".0")
    return "${formatted}w"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    heroAccentColor: Color,
    topBarContentColor: Color,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onHeaderChromeProgressChange: ((Float) -> Unit)? = null,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onLoadMoreAlbums: () -> Unit = {},
    bottomOverlayPadding: Dp = 0.dp
) {
    val isAvatarPreviewVisibleState = rememberSaveable { mutableStateOf(false) }
    val selectedTabIndexState = rememberSaveable { mutableIntStateOf(ArtistDetailTab.HOT_SONGS.ordinal) }
    val bodyListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndexState.intValue,
        pageCount = { ArtistDetailTab.entries.size }
    )
    val hotSongsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val albumsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val encyclopediaListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = ArtistDetailTab.entries[pagerState.currentPage]
    val density = LocalDensity.current
    LaunchedEffect(pagerState.settledPage) {
        selectedTabIndexState.intValue = pagerState.settledPage
    }
    val heroHeightPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val headerCollapseProgress by remember(bodyListState, heroHeightPx) {
        derivedStateOf {
            if (heroHeightPx <= 0f) {
                0f
            } else {
                (bodyListState.artistHeaderConsumedHeightPx() / (heroHeightPx * 0.72f))
                    .coerceIn(0f, 1f)
            }
        }
    }
    val animatedCollapseProgress by animateFloatAsState(
        targetValue = headerCollapseProgress,
        animationSpec = tween(durationMillis = 180),
        label = "artistHeaderCollapseProgress"
    )
    val compactTopBarHeight = rememberArtistCompactTopBarHeight()
    val compactTopBarHeightPx = with(density) { compactTopBarHeight.toPx() }
    val stickyHeaderTopInsetPx by remember(bodyListState, compactTopBarHeightPx) {
        derivedStateOf {
            if (compactTopBarHeightPx <= 0f) {
                0f
            } else {
                (compactTopBarHeightPx - bodyListState.artistHeroBottomPx())
                    .coerceIn(0f, compactTopBarHeightPx)
            }
        }
    }
    val stickyHeaderTopInset = with(density) { stickyHeaderTopInsetPx.toDp() }
    val stickyHeaderInsetProgress = if (compactTopBarHeightPx <= 0f) {
        0f
    } else {
        (stickyHeaderTopInsetPx / compactTopBarHeightPx).coerceIn(0f, 1f)
    }
    val headerChromeProgress = max(animatedCollapseProgress, stickyHeaderInsetProgress)
        .coerceIn(0f, 1f)
    val stableHeaderChromeProgress = remember(headerChromeProgress) {
        ((headerChromeProgress * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
    }
    if (onHeaderChromeProgressChange != null) {
        LaunchedEffect(stableHeaderChromeProgress, onHeaderChromeProgressChange) {
            onHeaderChromeProgressChange(stableHeaderChromeProgress)
        }
    }

    when (val headerState = state.headerState) {
        ArtistDetailHeaderUiState.Loading -> {
            ArtistDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                scaffoldBackButtonTint = topBarContentColor,
                heroContent = {
                    ArtistDetailHeroSkeleton()
                }
            ) {
                item {
                    DetailLoadingCard(text = "歌手详情加载中")
                }
            }
        }

        is ArtistDetailHeaderUiState.Error -> {
            ArtistDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                scaffoldBackButtonTint = topBarContentColor,
                heroContent = {
                    ArtistDetailHeroSkeleton()
                }
            ) {
                item {
                    DetailErrorCard(
                        message = headerState.message,
                        onRetry = onRetry,
                        testTag = "artist_detail_error"
                    )
                }
            }
        }

        is ArtistDetailHeaderUiState.Content -> {
            val content = headerState.content
            val descriptionBody = ArtistDetailEncyclopediaTab.artistDescriptionBody(
                content = content,
                encyclopediaState = state.encyclopediaState
            )
            ArtistDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                showScaffoldBackButton = false,
                scaffoldBackButtonTint = Color.Transparent,
                heroBrush = rememberDynamicHeroBrush(
                    imageUrl = content.coverUrl ?: content.avatarUrl
                ),
                overlayContent = {
                    ArtistDetailCollapsingTopBar(
                        artistName = content.name,
                        accentColor = heroAccentColor,
                        topBarContentColor = topBarContentColor,
                        collapseProgress = animatedCollapseProgress,
                        stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                        compactTopBarHeight = compactTopBarHeight,
                        onBack = onBack
                    )
                },
                heroContent = {
                    ArtistDetailHero(
                        content = content,
                        accentColor = heroAccentColor,
                        collapseProgress = animatedCollapseProgress,
                        onPlayHotClick = onPlayAll,
                        onAvatarClick = {
                            isAvatarPreviewVisibleState.value = true
                        }
                    )
                }
            ) {
                artistDetailBodyContent(
                    stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                    stickyHeaderTopInset = stickyHeaderTopInset,
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    outerListState = bodyListState,
                    hotSongsListState = hotSongsListState,
                    albumsListState = albumsListState,
                    encyclopediaListState = encyclopediaListState,
                    descriptionBody = descriptionBody,
                    hotSongsState = state.hotSongsState,
                    albumsState = state.albumsState,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onTabSelected = { tab ->
                        if (selectedTab != tab) {
                            coroutineScope.launch {
                                pagerState.scrollToPage(tab.ordinal)
                            }
                        }
                    },
                    onRetry = onRetry,
                    onPlayAll = onPlayAll,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onLoadMoreAlbums = onLoadMoreAlbums
                )
            }

            if (isAvatarPreviewVisibleState.value) {
                ArtistCoverPreviewDialog(
                    imageUrl = content.coverUrl ?: content.avatarUrl,
                    name = content.name,
                    onDismiss = {
                        isAvatarPreviewVisibleState.value = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailShell(
    onBack: () -> Unit,
    listState: LazyListState,
    bottomOverlayPadding: Dp = 0.dp,
    showScaffoldBackButton: Boolean = true,
    scaffoldBackButtonTint: Color,
    heroBrush: Brush? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
    heroContent: @Composable ColumnScope.() -> Unit,
    bodyContent: LazyListScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MusicDetailScaffold(
            heroTestTag = "artist_detail_hero_panel",
            onBack = onBack,
            bottomOverlayPadding = bottomOverlayPadding,
            listState = listState,
            heroBrush = heroBrush,
            drawHeroBackground = false,
            heroHorizontalPadding = 0.dp,
            heroTopPadding = 0.dp,
            heroBottomPadding = 0.dp,
            drawHeroBehindStatusBar = true,
            showBackButton = showScaffoldBackButton,
            backButtonTint = scaffoldBackButtonTint,
            heroContent = heroContent,
            bodyContent = bodyContent
        )
        overlayContent()
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.artistDetailBodyContent(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: ArtistDetailTab,
    pagerState: PagerState,
    outerListState: LazyListState,
    hotSongsListState: LazyListState,
    albumsListState: LazyListState,
    encyclopediaListState: LazyListState,
    descriptionBody: String,
    hotSongsState: ArtistHotSongsUiState,
    albumsState: ArtistAlbumsUiState,
    bottomOverlayPadding: Dp,
    onTabSelected: (ArtistDetailTab) -> Unit,
    onRetry: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onAlbumClick: (String) -> Unit,
    onLoadMoreAlbums: () -> Unit
) {
    stickyHeader {
        ArtistDetailStickyTabsHeader(
            stickyHeaderInsetProgress = stickyHeaderInsetProgress,
            stickyHeaderTopInset = stickyHeaderTopInset,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
    item {
        ArtistDetailTabPager(
            modifier = Modifier.fillParentMaxHeight(),
            pagerState = pagerState,
            outerListState = outerListState,
            hotSongsListState = hotSongsListState,
            albumsListState = albumsListState,
            encyclopediaListState = encyclopediaListState,
            hotSongsState = hotSongsState,
            albumsState = albumsState,
            descriptionBody = descriptionBody,
            bottomOverlayPadding = bottomOverlayPadding,
            onRetry = onRetry,
            onTrackClick = onTrackClick,
            onAlbumClick = onAlbumClick,
            onLoadMoreAlbums = onLoadMoreAlbums
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistDetailTabPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    outerListState: LazyListState,
    hotSongsListState: LazyListState,
    albumsListState: LazyListState,
    encyclopediaListState: LazyListState,
    hotSongsState: ArtistHotSongsUiState,
    albumsState: ArtistAlbumsUiState,
    descriptionBody: String,
    bottomOverlayPadding: Dp,
    onRetry: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onAlbumClick: (String) -> Unit,
    onLoadMoreAlbums: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
            state = pagerState,
            orientation = Orientation.Horizontal
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("artist_tab_pager")
    ) { page ->
        when (ArtistDetailTab.entries[page]) {
            ArtistDetailTab.HOT_SONGS -> {
                ArtistHotSongsTabPage(
                    outerListState = outerListState,
                    listState = hotSongsListState,
                    hotSongsState = hotSongsState,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onRetry = onRetry,
                    onTrackClick = onTrackClick
                )
            }

            ArtistDetailTab.ALBUMS -> {
                ArtistAlbumsTabPage(
                    outerListState = outerListState,
                    listState = albumsListState,
                    albumsState = albumsState,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onRetry = onRetry,
                    onLoadMoreAlbums = onLoadMoreAlbums,
                    onAlbumClick = onAlbumClick
                )
            }

            ArtistDetailTab.ENCYCLOPEDIA -> {
                ArtistEncyclopediaTabPage(
                    outerListState = outerListState,
                    listState = encyclopediaListState,
                    descriptionBody = descriptionBody,
                    bottomOverlayPadding = bottomOverlayPadding
                )
            }
        }
    }
}

@Composable
private fun ArtistHotSongsTabPage(
    outerListState: LazyListState,
    listState: LazyListState,
    hotSongsState: ArtistHotSongsUiState,
    bottomOverlayPadding: Dp,
    onRetry: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    val contentBottomPadding = if (bottomOverlayPadding > 0.dp) {
        bottomOverlayPadding
    } else {
        28.dp
    }
    val nestedScrollConnection = rememberDetailVerticalScrollHandoffConnection(
        outerScrollableState = outerListState,
        innerScrollableState = listState,
        canOuterConsumeUpward = outerListState::canArtistHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canArtistHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::artistHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("artist_hot_songs_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        artistHotSongsTabPanel(
            hotSongsState = hotSongsState,
            onRetry = onRetry,
            onTrackClick = onTrackClick
        )
    }
}

@Composable
private fun ArtistAlbumsTabPage(
    outerListState: LazyListState,
    listState: LazyListState,
    albumsState: ArtistAlbumsUiState,
    bottomOverlayPadding: Dp,
    onRetry: () -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val contentBottomPadding = if (bottomOverlayPadding > 0.dp) {
        bottomOverlayPadding
    } else {
        28.dp
    }
    val nestedScrollConnection = rememberDetailVerticalScrollHandoffConnection(
        outerScrollableState = outerListState,
        innerScrollableState = listState,
        canOuterConsumeUpward = outerListState::canArtistHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canArtistHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::artistHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("artist_albums_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        artistAlbumsTabPanel(
            albumsState = albumsState,
            onRetry = onRetry,
            onLoadMoreAlbums = onLoadMoreAlbums,
            onAlbumClick = onAlbumClick
        )
    }
}

@Composable
private fun ArtistEncyclopediaTabPage(
    outerListState: LazyListState,
    listState: LazyListState,
    descriptionBody: String,
    bottomOverlayPadding: Dp
) {
    val contentBottomPadding = if (bottomOverlayPadding > 0.dp) {
        bottomOverlayPadding
    } else {
        28.dp
    }
    val nestedScrollConnection = rememberDetailVerticalScrollHandoffConnection(
        outerScrollableState = outerListState,
        innerScrollableState = listState,
        canOuterConsumeUpward = outerListState::canArtistHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canArtistHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::artistHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("artist_encyclopedia_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        item {
            ArtistDetailEncyclopediaTab.ArtistEncyclopediaCard(
                body = descriptionBody
            )
        }
    }
}

private fun LazyListState.artistHeroBottomPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == ARTIST_HERO_LIST_INDEX }
        ?: return 0f
    return (heroItemInfo.offset + heroItemInfo.size).toFloat()
}

private fun LazyListState.artistHeaderConsumedHeightPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == ARTIST_HERO_LIST_INDEX }
    return if (heroItemInfo == null) {
        layoutInfo.viewportSize.height.toFloat()
    } else {
        (-heroItemInfo.offset).coerceAtLeast(0).toFloat()
    }
}

private fun LazyListState.artistHeaderRemainingUpwardScrollPx(): Float {
    return artistHeroBottomPx().coerceAtLeast(0f)
}

private fun LazyListState.canArtistHeaderConsumeUpward(): Boolean {
    return artistHeaderRemainingUpwardScrollPx() > 0.5f
}

private fun LazyListState.canArtistHeaderConsumeDownward(): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}

@Composable
private fun ArtistDetailHero(
    content: ArtistDetailContent,
    accentColor: Color,
    collapseProgress: Float,
    onPlayHotClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val statsText = buildList {
        if (content.fansCount > 0L) {
            add("${formatArtistFansCount(content.fansCount)} 粉丝")
        }
        add("${content.musicCount} 首歌曲")
        add("${content.albumCount} 张专辑")
    }.joinToString(separator = " · ")
    val bottomTextColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = 0.78f)
    val followButtonLabel = if (content.isFollowed == true) "已关注" else "关注"
    val heroIdentityFadeProgress = ((collapseProgress - 0.04f) / 0.56f).coerceIn(0f, 1f)
    val collapsingContentAlpha = 1f - heroIdentityFadeProgress
    val collapsingTranslationY = with(LocalDensity.current) { 24.dp.toPx() } * heroIdentityFadeProgress
    val subtitle = buildList {
        if (content.aliases.isNotEmpty()) {
            add(content.aliases.joinToString(separator = " · "))
        }
        if (content.identities.isNotEmpty()) {
            add(content.identities.joinToString(separator = " / "))
        }
    }.joinToString(separator = "\n")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("artist_detail_cover")
                .clickable(onClick = onAvatarClick)
        ) {
            ArtistCoverArt(
                imageUrl = content.coverUrl ?: content.avatarUrl,
                name = content.name
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = content.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = bottomTextColor,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayHotClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("artist_hero_play_hot_button")
                ) {
                    Text("播放最热门")
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("artist_hero_follow_button")
                ) {
                    Text(followButtonLabel)
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailHeroSkeleton() {
    val skeletonColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(skeletonColor.copy(alpha = 0.18f))
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skeletonColor.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skeletonColor.copy(alpha = 0.14f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skeletonColor.copy(alpha = 0.14f))
            )
        }
    }
}

@Composable
private fun ArtistDetailStickyTabsHeader(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: ArtistDetailTab,
    onTabSelected: (ArtistDetailTab) -> Unit
) {
    val topCornerRadius = 20.dp * (1f - stickyHeaderInsetProgress.coerceIn(0f, 1f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = stickyHeaderTopInset)
            .background(MaterialTheme.colorScheme.background)
            .zIndex(1f)
            .testTag("artist_sticky_tabs_header")
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(
                topStart = topCornerRadius,
                topEnd = topCornerRadius,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArtistDetailTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onTabSelected(tab)
                            }
                            .testTag(tab.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailCollapsingTopBar(
    artistName: String,
    accentColor: Color,
    topBarContentColor: Color,
    collapseProgress: Float,
    stickyHeaderInsetProgress: Float,
    compactTopBarHeight: Dp,
    onBack: () -> Unit
) {
    val safeTopPadding = rememberArtistSafeTopPadding()
    val chromeProgress = max(collapseProgress, stickyHeaderInsetProgress).coerceIn(0f, 1f)
    val gradientAlpha = chromeProgress
    val titleRevealProgress = max(
        stickyHeaderInsetProgress.coerceIn(0f, 1f),
        (collapseProgress * 0.92f).coerceIn(0f, 1f)
    )
    val titleAlpha = ((titleRevealProgress - 0.22f) / 0.5f).coerceIn(0f, 1f)
    val titleTranslationY = with(LocalDensity.current) { 8.dp.toPx() } * (1f - titleAlpha)
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(compactTopBarHeight)
            .testTag("artist_collapsing_top_bar")
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
                            lerp(
                                start = accentColor,
                                stop = backgroundColor,
                                fraction = 0.16f
                            ),
                            lerp(
                                start = accentColor,
                                stop = backgroundColor,
                                fraction = 0.42f
                            ),
                            backgroundColor
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = safeTopPadding, end = 8.dp)
                .height(ARTIST_COMPACT_TOP_BAR_CONTENT_HEIGHT)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtistDetailTopChromeAction(
                    testTag = "detail_back_button",
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = topBarContentColor
                    )
                }
                ArtistDetailTopChromeAction(
                    testTag = "artist_detail_more_button",
                    onClick = {}
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "更多",
                        tint = topBarContentColor
                    )
                }
            }
            Text(
                text = artistName,
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
                    .testTag("artist_collapsing_top_bar_title")
            )
        }
    }
}

@Composable
private fun ArtistDetailTopChromeAction(
    testTag: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun rememberArtistCompactTopBarHeight(): Dp {
    return rememberArtistSafeTopPadding() + ARTIST_COMPACT_TOP_BAR_CONTENT_HEIGHT
}

@Composable
private fun rememberArtistSafeTopPadding(): Dp {
    return WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
}

@Composable
private fun ArtistCoverArt(
    imageUrl: String?,
    name: String
) {
    val heroPlaceholderColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(heroPlaceholderColor.copy(alpha = 0.14f))
            .testTag("artist_detail_cover_image"),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary
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
private fun ArtistCoverPreviewDialog(
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
            shape = RoundedCornerShape(28.dp),
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
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("artist_cover_preview_image"),
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
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .testTag("artist_cover_preview_image"),
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
