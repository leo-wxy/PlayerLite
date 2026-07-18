package com.wxy.playerlite.feature.album

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.detail.DetailBottomScrim
import com.wxy.playerlite.feature.detail.DetailErrorCard
import com.wxy.playerlite.feature.detail.DetailLoadingCard
import com.wxy.playerlite.feature.detail.DetailPagingFooter
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.rememberDetailVerticalScrollHandoffConnection
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class AlbumDetailTab(
    val title: String,
    val testTag: String
) {
    TRACKS("歌曲", "album_tab_tracks"),
    DESCRIPTION("简介", "album_tab_description")
}

private const val ALBUM_HERO_LIST_INDEX = 0
private val ALBUM_COMPACT_TOP_BAR_CONTENT_HEIGHT = 56.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    heroAccentColor: Color,
    topBarContentColor: Color,
    headerCommentCountText: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onHeaderChromeProgressChange: ((Float) -> Unit)? = null,
    onLoadMore: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    bottomOverlayPadding: Dp = 0.dp
) {
    val selectedTabIndexState = rememberSaveable { mutableIntStateOf(AlbumDetailTab.TRACKS.ordinal) }
    val bodyListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndexState.intValue,
        pageCount = { AlbumDetailTab.entries.size }
    )
    val tracksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val descriptionListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = AlbumDetailTab.entries[pagerState.currentPage]
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
                (bodyListState.albumHeaderConsumedHeightPx() / (heroHeightPx * 0.72f))
                    .coerceIn(0f, 1f)
            }
        }
    }
    val animatedCollapseProgress by animateFloatAsState(
        targetValue = headerCollapseProgress,
        animationSpec = tween(durationMillis = 180),
        label = "albumHeaderCollapseProgress"
    )
    val compactTopBarHeight = rememberAlbumCompactTopBarHeight()
    val compactTopBarHeightPx = with(density) { compactTopBarHeight.toPx() }
    val stickyHeaderTopInsetPx by remember(bodyListState, compactTopBarHeightPx) {
        derivedStateOf {
            if (compactTopBarHeightPx <= 0f) {
                0f
            } else {
                (compactTopBarHeightPx - bodyListState.albumHeroBottomPx())
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

    when (val contentState = state.contentState) {
        AlbumContentUiState.Loading -> {
            AlbumDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                scaffoldBackButtonTint = topBarContentColor,
                heroContent = {
                    AlbumDetailHeroSkeleton()
                }
            ) {
                item {
                    DetailLoadingCard(text = "专辑详情加载中")
                }
            }
        }

        is AlbumContentUiState.Error -> {
            AlbumDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                scaffoldBackButtonTint = topBarContentColor,
                heroContent = {
                    AlbumDetailHeroSkeleton()
                }
            ) {
                item {
                    DetailErrorCard(
                        message = contentState.message,
                        onRetry = onRetry,
                        testTag = "album_detail_error"
                    )
                }
            }
        }

        is AlbumContentUiState.Content -> {
            val content = contentState.content
            val descriptionBody = content.description.ifBlank { "暂时没有更多专辑简介。" }
            AlbumDetailShell(
                onBack = onBack,
                listState = bodyListState,
                bottomOverlayPadding = bottomOverlayPadding,
                showScaffoldBackButton = false,
                scaffoldBackButtonTint = Color.Transparent,
                heroBrush = rememberDynamicHeroBrush(
                    imageUrl = content.coverUrl
                ),
                overlayContent = {
                    AlbumDetailCollapsingTopBar(
                        albumTitle = content.title,
                        accentColor = heroAccentColor,
                        topBarContentColor = topBarContentColor,
                        collapseProgress = animatedCollapseProgress,
                        stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                        compactTopBarHeight = compactTopBarHeight,
                        onBack = onBack
                    )
                },
                heroContent = {
                    AlbumDetailHero(
                        content = content,
                        collapseProgress = animatedCollapseProgress,
                        commentCountText = headerCommentCountText,
                        onPlayAll = onPlayAll
                    )
                    AlbumHeaderDynamicSection(
                        dynamicState = state.dynamicState,
                        onRetry = onRetry
                    )
                }
            ) {
                albumDetailBodyContent(
                    stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                    stickyHeaderTopInset = stickyHeaderTopInset,
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    outerListState = bodyListState,
                    tracksListState = tracksListState,
                    descriptionListState = descriptionListState,
                    content = content,
                    descriptionBody = descriptionBody,
                    isLoadingMore = contentState.isLoadingMore,
                    loadMoreErrorMessage = contentState.loadMoreErrorMessage,
                    endReached = contentState.endReached,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onTabSelected = { tab ->
                        if (selectedTab != tab) {
                            coroutineScope.launch {
                                pagerState.scrollToPage(tab.ordinal)
                            }
                        }
                    },
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onTrackClick = onTrackClick
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailShell(
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
            heroTestTag = "album_detail_hero_panel",
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
private fun LazyListScope.albumDetailBodyContent(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: AlbumDetailTab,
    pagerState: PagerState,
    outerListState: LazyListState,
    tracksListState: LazyListState,
    descriptionListState: LazyListState,
    content: AlbumDetailContent,
    descriptionBody: String,
    isLoadingMore: Boolean,
    loadMoreErrorMessage: String?,
    endReached: Boolean,
    bottomOverlayPadding: Dp,
    onTabSelected: (AlbumDetailTab) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    stickyHeader {
        AlbumDetailStickyTabsHeader(
            stickyHeaderInsetProgress = stickyHeaderInsetProgress,
            stickyHeaderTopInset = stickyHeaderTopInset,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
    item {
        AlbumDetailTabPager(
            modifier = Modifier.fillParentMaxHeight(),
            pagerState = pagerState,
            outerListState = outerListState,
            tracksListState = tracksListState,
            descriptionListState = descriptionListState,
            content = content,
            descriptionBody = descriptionBody,
            isLoadingMore = isLoadingMore,
            loadMoreErrorMessage = loadMoreErrorMessage,
            endReached = endReached,
            bottomOverlayPadding = bottomOverlayPadding,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onTrackClick = onTrackClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumDetailTabPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    outerListState: LazyListState,
    tracksListState: LazyListState,
    descriptionListState: LazyListState,
    content: AlbumDetailContent,
    descriptionBody: String,
    isLoadingMore: Boolean,
    loadMoreErrorMessage: String?,
    endReached: Boolean,
    bottomOverlayPadding: Dp,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
            state = pagerState,
            orientation = Orientation.Horizontal
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("album_tab_pager")
    ) { page ->
        when (AlbumDetailTab.entries[page]) {
            AlbumDetailTab.TRACKS -> {
                AlbumTracksTabPage(
                    outerListState = outerListState,
                    listState = tracksListState,
                    content = content,
                    isLoadingMore = isLoadingMore,
                    loadMoreErrorMessage = loadMoreErrorMessage,
                    endReached = endReached,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onTrackClick = onTrackClick
                )
            }

            AlbumDetailTab.DESCRIPTION -> {
                AlbumDescriptionTabPage(
                    outerListState = outerListState,
                    listState = descriptionListState,
                    descriptionBody = descriptionBody,
                    bottomOverlayPadding = bottomOverlayPadding
                )
            }
        }
    }
}

@Composable
private fun AlbumTracksTabPage(
    outerListState: LazyListState,
    listState: LazyListState,
    content: AlbumDetailContent,
    isLoadingMore: Boolean,
    loadMoreErrorMessage: String?,
    endReached: Boolean,
    bottomOverlayPadding: Dp,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
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
        canOuterConsumeUpward = outerListState::canAlbumHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canAlbumHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::albumHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("album_tracks_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        if (content.tracks.isEmpty()) {
            item {
                DetailLoadingCard(text = "暂时没有可展示的专辑歌曲")
            }
        } else {
            items(
                count = content.tracks.size,
                key = { index -> content.tracks[index].trackId.ifBlank { "album_track_$index" } }
            ) { index ->
                AlbumTrackRowCard(
                    item = content.tracks[index],
                    order = index + 1,
                    onClick = {
                        onTrackClick(index)
                    }
                )
            }
            item {
                DetailPagingFooter(
                    footerTagPrefix = "album_tracks",
                    loadTriggerKey = content.tracks.size,
                    isLoadingMore = isLoadingMore,
                    loadMoreErrorMessage = loadMoreErrorMessage,
                    endReached = endReached,
                    loadingText = "正在加载更多歌曲",
                    endText = "专辑曲目已全部展示",
                    onLoadMore = onLoadMore,
                    onRetry = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun AlbumDescriptionTabPage(
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
        canOuterConsumeUpward = outerListState::canAlbumHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canAlbumHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::albumHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("album_description_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        item {
            AlbumDescriptionPanel(body = descriptionBody)
        }
    }
}

private fun LazyListState.albumHeroBottomPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == ALBUM_HERO_LIST_INDEX }
        ?: return 0f
    return (heroItemInfo.offset + heroItemInfo.size).toFloat()
}

private fun LazyListState.albumHeaderConsumedHeightPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == ALBUM_HERO_LIST_INDEX }
    return if (heroItemInfo == null) {
        layoutInfo.viewportSize.height.toFloat()
    } else {
        (-heroItemInfo.offset).coerceAtLeast(0).toFloat()
    }
}

private fun LazyListState.albumHeaderRemainingUpwardScrollPx(): Float {
    return albumHeroBottomPx().coerceAtLeast(0f)
}

private fun LazyListState.canAlbumHeaderConsumeUpward(): Boolean {
    return albumHeaderRemainingUpwardScrollPx() > 0.5f
}

private fun LazyListState.canAlbumHeaderConsumeDownward(): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}

@Composable
private fun AlbumHeaderDynamicSection(
    dynamicState: AlbumDynamicUiState,
    onRetry: () -> Unit
) {
    when (dynamicState) {
        AlbumDynamicUiState.Loading -> {
            DetailLoadingCard(
                text = "专辑动态信息加载中",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        is AlbumDynamicUiState.Error -> {
            DetailErrorCard(
                message = dynamicState.message,
                onRetry = onRetry,
                modifier = Modifier.padding(top = 8.dp),
                testTag = "album_dynamic_error"
            )
        }

        AlbumDynamicUiState.Empty -> Unit

        is AlbumDynamicUiState.Content -> {
            AlbumDynamicMetaSection(
                content = dynamicState.content,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AlbumDetailHero(
    content: AlbumDetailContent,
    collapseProgress: Float,
    commentCountText: String?,
    onPlayAll: () -> Unit
) {
    val secondaryTextColor = Color.White.copy(alpha = 0.78f)
    val statsText = buildList {
        add("${content.trackCount} 首歌曲")
        if (content.company.isNotBlank()) {
            add(content.company)
        }
        if (content.publishTimeText.isNotBlank()) {
            add(content.publishTimeText)
        }
    }.joinToString(separator = " · ")
    val heroIdentityFadeProgress = ((collapseProgress - 0.04f) / 0.56f).coerceIn(0f, 1f)
    val collapsingContentAlpha = 1f - heroIdentityFadeProgress
    val collapsingTranslationY = with(LocalDensity.current) { 24.dp.toPx() } * heroIdentityFadeProgress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("album_detail_cover")
        ) {
            AlbumHeroArt(
                imageUrl = content.coverUrl,
                title = content.title
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
                    text = content.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (content.artistText.isNotBlank()) {
                    Text(
                        text = content.artistText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (statsText.isNotBlank()) {
                    Text(
                        text = statsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("album_hero_play_all_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Text("播放全部")
                    }
                }
                if (!commentCountText.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "评论",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                text = commentCountText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .testTag("album_hero_comment_count")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailHeroSkeleton() {
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
private fun AlbumDetailStickyTabsHeader(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: AlbumDetailTab,
    onTabSelected: (AlbumDetailTab) -> Unit
) {
    val topCornerRadius = 20.dp * (1f - stickyHeaderInsetProgress.coerceIn(0f, 1f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = stickyHeaderTopInset)
            .background(MaterialTheme.colorScheme.background)
            .zIndex(1f)
            .testTag("album_sticky_tabs_header")
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
                AlbumDetailTab.entries.forEach { tab ->
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
private fun AlbumDetailCollapsingTopBar(
    albumTitle: String,
    accentColor: Color,
    topBarContentColor: Color,
    collapseProgress: Float,
    stickyHeaderInsetProgress: Float,
    compactTopBarHeight: Dp,
    onBack: () -> Unit
) {
    val safeTopPadding = rememberAlbumSafeTopPadding()
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
            .testTag("album_collapsing_top_bar")
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
                .height(ALBUM_COMPACT_TOP_BAR_CONTENT_HEIGHT)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumDetailTopChromeAction(
                    testTag = "detail_back_button",
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = topBarContentColor
                    )
                }
                AlbumDetailTopChromeAction(
                    testTag = "album_detail_more_button",
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
                text = albumTitle,
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
                    .testTag("album_collapsing_top_bar_title")
            )
        }
    }
}

@Composable
private fun AlbumDetailTopChromeAction(
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
private fun AlbumDynamicMetaSection(
    content: AlbumDynamicInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("album_dynamic_meta_section"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
private fun AlbumTrackRowCard(
    item: AlbumTrackRow,
    order: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("album_track_${item.trackId}"),
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

@Composable
private fun AlbumDescriptionPanel(body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("album_description_panel"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "简介",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumHeroArt(
    imageUrl: String?,
    title: String
) {
    val heroPlaceholderColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(heroPlaceholderColor.copy(alpha = 0.14f))
            .testTag("album_detail_cover_image"),
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
    }
}

@Composable
private fun rememberAlbumCompactTopBarHeight(): Dp {
    return rememberAlbumSafeTopPadding() + ALBUM_COMPACT_TOP_BAR_CONTENT_HEIGHT
}

@Composable
private fun rememberAlbumSafeTopPadding(): Dp {
    return WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
}
