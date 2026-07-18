package com.wxy.playerlite.feature.playlist

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.wxy.playerlite.feature.detail.DetailSectionPlayAllButton
import com.wxy.playerlite.feature.detail.MusicDetailScaffold
import com.wxy.playerlite.feature.detail.formatTrackDuration
import com.wxy.playerlite.feature.detail.rememberDetailVerticalScrollHandoffConnection
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.rememberDynamicHeroBrush
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent
import kotlin.math.max
import kotlinx.coroutines.launch
import java.util.Locale

private enum class PlaylistDetailTab(
    val title: String,
    val testTag: String
) {
    TRACKS("歌曲", "playlist_tab_tracks"),
    DESCRIPTION("简介", "playlist_tab_description")
}

private const val PLAYLIST_HERO_LIST_INDEX = 0

fun formatPlaylistPlayCount(playCount: Long): String {
    val normalized = playCount.coerceAtLeast(0L)
    if (normalized < 10_000L) {
        return normalized.toString()
    }
    return "${String.format(Locale.US, "%.1f", normalized / 10_000.0)}w"
}

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    heroAccentColor: Color = MaterialTheme.colorScheme.primary,
    topBarContentColor: Color = MaterialTheme.colorScheme.surface,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onHeaderChromeProgressChange: ((Float) -> Unit)? = null,
    onLoadMore: () -> Unit,
    onPlayAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    bottomOverlayPadding: Dp = 0.dp
) {
    val selectedTabIndexState = rememberSaveable { mutableIntStateOf(PlaylistDetailTab.TRACKS.ordinal) }
    val outerListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndexState.intValue,
        pageCount = { PlaylistDetailTab.entries.size }
    )
    val tracksListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val descriptionListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = PlaylistDetailTab.entries[pagerState.currentPage]
    val density = LocalDensity.current
    val heroHeightPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    LaunchedEffect(pagerState.settledPage) {
        selectedTabIndexState.intValue = pagerState.settledPage
    }

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
            val descriptionBody = headerState.content.description.ifBlank { "暂时没有更多歌单简介。" }
            val headerCollapseProgress by remember(outerListState, heroHeightPx) {
                derivedStateOf {
                    if (heroHeightPx <= 0f) {
                        0f
                    } else {
                        (outerListState.playlistHeaderConsumedHeightPx() / (heroHeightPx * 0.72f))
                            .coerceIn(0f, 1f)
                    }
                }
            }
            val animatedCollapseProgress by animateFloatAsState(
                targetValue = headerCollapseProgress,
                animationSpec = tween(durationMillis = 180),
                label = "playlistHeaderCollapseProgress"
            )
            val compactTopBarHeight = rememberPlaylistCompactTopBarHeight()
            val compactTopBarHeightPx = with(density) { compactTopBarHeight.toPx() }
            val stickyHeaderTopInsetPx by remember(outerListState, compactTopBarHeightPx) {
                derivedStateOf {
                    if (compactTopBarHeightPx <= 0f) {
                        0f
                    } else {
                        (compactTopBarHeightPx - outerListState.playlistHeroBottomPx())
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
            SideEffect {
                onHeaderChromeProgressChange?.invoke(headerChromeProgress)
            }

            PlaylistDetailShell(
                onBack = onBack,
                listState = outerListState,
                bottomOverlayPadding = bottomOverlayPadding,
                showScaffoldBackButton = false,
                scaffoldBackButtonTint = Color.Transparent,
                heroBrush = rememberDynamicHeroBrush(imageUrl = headerState.content.coverUrl),
                overlayContent = {
                    PlaylistDetailCollapsingTopBar(
                        playlistTitle = headerState.content.title,
                        accentColor = heroAccentColor,
                        topBarContentColor = topBarContentColor,
                        collapseProgress = animatedCollapseProgress,
                        stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                        compactTopBarHeight = compactTopBarHeight,
                        onBack = onBack
                    )
                },
                heroContent = {
                    PlaylistDetailHero(
                        content = headerState.content,
                        collapseProgress = animatedCollapseProgress,
                        onPlayAll = onPlayAll
                    )
                    PlaylistHeaderDynamicSection(
                        dynamicState = state.dynamicState,
                        onRetry = onRetry
                    )
                }
            ) {
                playlistDetailBodyContent(
                    stickyHeaderInsetProgress = stickyHeaderInsetProgress,
                    stickyHeaderTopInset = stickyHeaderTopInset,
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    outerListState = outerListState,
                    tracksListState = tracksListState,
                    descriptionListState = descriptionListState,
                    tracksState = state.tracksState,
                    descriptionBody = descriptionBody,
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

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.playlistDetailBodyContent(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: PlaylistDetailTab,
    pagerState: PagerState,
    outerListState: LazyListState,
    tracksListState: LazyListState,
    descriptionListState: LazyListState,
    tracksState: PlaylistTracksUiState,
    descriptionBody: String,
    bottomOverlayPadding: Dp,
    onTabSelected: (PlaylistDetailTab) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    stickyHeader {
        PlaylistDetailStickyTabsHeader(
            stickyHeaderInsetProgress = stickyHeaderInsetProgress,
            stickyHeaderTopInset = stickyHeaderTopInset,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
    item {
        PlaylistDetailTabPager(
            modifier = Modifier.fillParentMaxHeight(),
            pagerState = pagerState,
            outerListState = outerListState,
            tracksListState = tracksListState,
            descriptionListState = descriptionListState,
            tracksState = tracksState,
            descriptionBody = descriptionBody,
            bottomOverlayPadding = bottomOverlayPadding,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onTrackClick = onTrackClick
        )
    }
}

@Composable
private fun PlaylistDetailShell(
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
            heroTestTag = "playlist_detail_hero_panel",
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

@Composable
private fun PlaylistDetailCollapsingTopBar(
    playlistTitle: String,
    accentColor: Color,
    topBarContentColor: Color,
    collapseProgress: Float,
    stickyHeaderInsetProgress: Float,
    compactTopBarHeight: Dp,
    onBack: () -> Unit
) {
    val safeTopPadding = rememberPlaylistSafeTopPadding()
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
            .testTag("playlist_collapsing_top_bar")
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
                .height(PLAYLIST_COMPACT_TOP_BAR_CONTENT_HEIGHT)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistDetailTopChromeAction(
                    testTag = "detail_back_button",
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = topBarContentColor
                    )
                }
                PlaylistDetailTopChromeAction(
                    testTag = "playlist_detail_more_button",
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
                text = playlistTitle,
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
                    .testTag("playlist_collapsing_top_bar_title")
            )
        }
    }
}

@Composable
private fun PlaylistDetailTopChromeAction(
    testTag: String,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
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
private fun PlaylistDetailHero(
    content: PlaylistHeaderContent,
    collapseProgress: Float,
    onPlayAll: () -> Unit
) {
    val secondaryTextColor = Color.White.copy(alpha = 0.78f)
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
                .testTag("playlist_detail_cover")
        ) {
            PlaylistHeroArt(
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
                if (content.creatorName.isNotBlank()) {
                    Text(
                        text = content.creatorName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("playlist_creator_meta")
                    )
                } else {
                    Text(
                        text = "未知作者",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("playlist_creator_meta")
                    )
                }
                Text(
                    text = "${content.trackCount} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("playlist_track_count_meta")
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailSectionPlayAllButton(
                    onClick = onPlayAll,
                    testTag = "playlist_play_all_button"
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeaderDynamicSection(
    dynamicState: PlaylistDynamicUiState,
    onRetry: () -> Unit
) {
    when (dynamicState) {
        PlaylistDynamicUiState.Loading -> {
            DetailLoadingCard(
                text = "歌单动态信息加载中",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        is PlaylistDynamicUiState.Error -> {
            DetailErrorCard(
                message = dynamicState.message,
                onRetry = onRetry,
                modifier = Modifier.padding(top = 8.dp),
                testTag = "playlist_dynamic_error"
            )
        }

        PlaylistDynamicUiState.Empty -> Unit

        is PlaylistDynamicUiState.Content -> {
            PlaylistDynamicMetaSection(
                content = dynamicState.content,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaylistTracksSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "歌曲列表",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("playlist_tracks_section")
        )
    }
}

@Composable
private fun PlaylistHeroArt(
    imageUrl: String?,
    title: String
) {
    val heroPlaceholderColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(heroPlaceholderColor.copy(alpha = 0.14f))
            .testTag("playlist_detail_cover_image"),
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
private fun PlaylistDetailStickyTabsHeader(
    stickyHeaderInsetProgress: Float,
    stickyHeaderTopInset: Dp,
    selectedTab: PlaylistDetailTab,
    onTabSelected: (PlaylistDetailTab) -> Unit
) {
    val topCornerRadius = 20.dp * (1f - stickyHeaderInsetProgress.coerceIn(0f, 1f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = stickyHeaderTopInset)
            .background(MaterialTheme.colorScheme.background)
            .zIndex(1f)
            .testTag("playlist_sticky_tabs_header")
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
                PlaylistDetailTab.entries.forEach { tab ->
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
private fun PlaylistDetailTabPager(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    outerListState: LazyListState,
    tracksListState: LazyListState,
    descriptionListState: LazyListState,
    tracksState: PlaylistTracksUiState,
    descriptionBody: String,
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
            .testTag("playlist_tab_pager")
    ) { page ->
        when (PlaylistDetailTab.entries[page]) {
            PlaylistDetailTab.TRACKS -> {
                PlaylistTracksTabPage(
                    outerListState = outerListState,
                    listState = tracksListState,
                    tracksState = tracksState,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onTrackClick = onTrackClick
                )
            }

            PlaylistDetailTab.DESCRIPTION -> {
                PlaylistDescriptionTabPage(
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
private fun PlaylistTracksTabPage(
    outerListState: LazyListState,
    listState: LazyListState,
    tracksState: PlaylistTracksUiState,
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
        canOuterConsumeUpward = outerListState::canPlaylistHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canPlaylistHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::playlistHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("playlist_tracks_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        item {
            PlaylistTracksSectionHeader()
        }
        when (tracksState) {
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
                    PlaylistTrackRowCard(
                        item = tracksState.items[index],
                        order = index + 1,
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
}

@Composable
private fun PlaylistDescriptionTabPage(
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
        canOuterConsumeUpward = outerListState::canPlaylistHeaderConsumeUpward,
        canOuterConsumeDownward = outerListState::canPlaylistHeaderConsumeDownward,
        remainingOuterUpwardDistancePx = outerListState::playlistHeaderRemainingUpwardScrollPx
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("playlist_description_list"),
        contentPadding = PaddingValues(bottom = contentBottomPadding)
    ) {
        item {
            PlaylistDescriptionPanel(body = descriptionBody)
        }
    }
}

@Composable
private fun PlaylistDescriptionPanel(body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("playlist_description_panel"),
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
private fun PlaylistTracksErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
    content: PlaylistDynamicInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("playlist_dynamic_meta_section"),
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
            PlaylistMetricText(label = "评论", value = content.commentCount.toString())
            PlaylistMetricText(
                label = "收藏",
                value = if (content.isSubscribed) "已收藏" else "未收藏"
            )
            PlaylistMetricText(label = "播放", value = formatPlaylistPlayCount(content.playCount))
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
private fun PlaylistTrackRowCard(
    item: PlaylistTrackRow,
    order: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("playlist_track_${item.trackId}"),
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

private val PLAYLIST_COMPACT_TOP_BAR_CONTENT_HEIGHT = 56.dp

@Composable
private fun rememberPlaylistCompactTopBarHeight(): Dp {
    return rememberPlaylistSafeTopPadding() + PLAYLIST_COMPACT_TOP_BAR_CONTENT_HEIGHT
}

@Composable
private fun rememberPlaylistSafeTopPadding(): Dp {
    return WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
}

private fun LazyListState.playlistHeroBottomPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == PLAYLIST_HERO_LIST_INDEX }
        ?: return 0f
    return (heroItemInfo.offset + heroItemInfo.size).toFloat()
}

private fun LazyListState.playlistHeaderConsumedHeightPx(): Float {
    val heroItemInfo = layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == PLAYLIST_HERO_LIST_INDEX }
    return if (heroItemInfo == null) {
        layoutInfo.viewportSize.height.toFloat()
    } else {
        (-heroItemInfo.offset).coerceAtLeast(0).toFloat()
    }
}

private fun LazyListState.playlistHeaderRemainingUpwardScrollPx(): Float {
    return playlistHeroBottomPx().coerceAtLeast(0f)
}

private fun LazyListState.canPlaylistHeaderConsumeUpward(): Boolean {
    return playlistHeaderRemainingUpwardScrollPx() > 0.5f
}

private fun LazyListState.canPlaylistHeaderConsumeDownward(): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}
