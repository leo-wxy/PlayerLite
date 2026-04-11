package com.wxy.playerlite.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeOverviewScreen(
    overviewState: HomeOverviewUiState,
    bottomContentPadding: Dp,
    onSearchClick: () -> Unit,
    onRetry: () -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = homeOverviewBackgroundBrush
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_discovery_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 88.dp,
                end = 20.dp,
                bottom = bottomContentPadding + navigationBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(HomeDiscoveryLayoutSpec.sectionSpacing)
        ) {
            if (overviewState.errorMessage != null && overviewState.sections.isNotEmpty()) {
                item {
                    HomeOverviewInlineError(
                        message = overviewState.errorMessage,
                        onRetry = onRetry
                    )
                }
            }

            when {
                overviewState.isLoading && overviewState.sections.isEmpty() -> {
                    item {
                        HomeOverviewStatusCard(
                            title = "发现内容加载中",
                            subtitle = "正在同步首页推荐内容，请稍候。"
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                !overviewState.isLoading && overviewState.sections.isEmpty() && overviewState.errorMessage != null -> {
                    item {
                        HomeOverviewStatusCard(
                            title = "首页加载失败",
                            subtitle = overviewState.errorMessage
                        ) {
                            OutlinedButton(onClick = onRetry) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重新加载")
                            }
                        }
                    }
                }

                !overviewState.isLoading && overviewState.sections.isEmpty() -> {
                    item {
                        HomeOverviewStatusCard(
                            title = "首页暂无发现内容",
                            subtitle = "稍后再来看看新的推荐内容。"
                        )
                    }
                }

                else -> {
                    items(
                        items = overviewState.sections,
                        key = { section -> section.code }
                    ) { section ->
                        HomeDiscoverySection(
                            section = section,
                            onAction = onAction
                        )
                    }
                }
            }
        }

        HomeSearchBox(
            keyword = overviewState.currentSearchKeyword,
            onClick = onSearchClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun HomeSearchBox(
    keyword: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeDiscoveryLayoutSpec.searchBoxHeight)
            .testTag("home_search_box_container")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.searchBoxCornerRadius),
        color = homeSearchSurfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = HomeDiscoveryLayoutSpec.searchBoxShadowElevation,
        border = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.testTag("home_search_box"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = homeTextSecondary.copy(alpha = 0.9f)
                )
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.bodyMedium,
                    color = homeTextSecondary.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeOverviewInlineError(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.standardCardCornerRadius),
        color = homeMutedCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = homeDividerColor.copy(alpha = 0.72f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun HomeOverviewStatusCard(
    title: String,
    subtitle: String,
    actionContent: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.standardCardCornerRadius),
        color = homeCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = homeDividerColor.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            actionContent?.invoke()
        }
    }
}

@Composable
private fun HomeDiscoverySection(
    section: HomeSectionUiModel,
    onAction: (HomeAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_section_${section.code}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (section.title.isNotBlank()) {
            HomeSectionTitle(title = section.title)
        }

        if (HomeDiscoveryLayoutSpec.usesCarousel(section.layout)) {
            HomeBannerCarousel(
                items = section.items,
                onItemClick = onAction
            )
        } else {
            val songColumns = if (section.usesSongCardLayout()) {
                section.items.chunked(HomeDiscoveryLayoutSpec.songColumnItemCount)
            } else {
                emptyList()
            }
            if (section.usesSongCardLayout()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = HomeDiscoveryLayoutSpec.songSectionContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(HomeDiscoveryLayoutSpec.songCardSpacing)
                ) {
                    itemsIndexed(
                        items = songColumns,
                        key = { columnIndex, items ->
                            items.firstOrNull()?.id ?: "home-song-column-$columnIndex"
                        }
                    ) { columnIndex, items ->
                        HomeSongColumn(
                            columnIndex = columnIndex,
                            items = items,
                            onAction = onAction
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = HomeDiscoveryLayoutSpec.rowContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(HomeDiscoveryLayoutSpec.itemSpacing)
                ) {
                    items(
                        items = section.items,
                        key = { item -> item.id }
                    ) { item ->
                        when {
                            section.layout == HomeSectionLayout.BANNER -> BannerSectionCard(
                                item = item,
                                onClick = { onAction(item.action) }
                            )

                            section.layout == HomeSectionLayout.ICON_GRID -> CompactSectionCard(
                                item = item,
                                onClick = { onAction(item.action) }
                            )

                            else -> DiscoverySectionCard(
                                item = item,
                                onClick = { onAction(item.action) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBannerCarousel(
    items: List<HomeSectionItemUiModel>,
    onItemClick: (HomeAction) -> Unit
) {
    val actualCount = items.size
    val pageCount = if (actualCount > 1 && HomeDiscoveryLayoutSpec.bannerUsesInfiniteLoop) {
        HomeDiscoveryLayoutSpec.virtualBannerPageCount
    } else {
        actualCount
    }
    val initialPage = HomeDiscoveryLayoutSpec.initialBannerPage(actualCount)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )
    LaunchedEffect(actualCount, pagerState) {
        if (actualCount <= 1 || !HomeDiscoveryLayoutSpec.bannerUsesInfiniteLoop) {
            return@LaunchedEffect
        }
        snapshotFlow { pagerState.settledPage }
            .collectLatest { page ->
                val recenteredPage = HomeDiscoveryLayoutSpec.recenterBannerPage(
                    currentPage = page,
                    itemCount = actualCount
                )
                if (recenteredPage != page) {
                    pagerState.scrollToPage(recenteredPage)
                }
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeDiscoveryLayoutSpec.bannerHeight),
            contentPadding = HomeDiscoveryLayoutSpec.bannerContentPadding,
            pageSpacing = HomeDiscoveryLayoutSpec.itemSpacing
        ) { page ->
            val itemIndex = if (actualCount == 0) 0 else page % actualCount
            BannerSectionCard(
                item = items[itemIndex],
                onClick = { onItemClick(items[itemIndex].action) },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (actualCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val selectedIndex = pagerState.currentPage % actualCount
                repeat(actualCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(
                                width = if (selectedIndex == index) 14.dp else 4.dp,
                                height = 4.dp
                            )
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selectedIndex == index) {
                                    homeAccentColor
                                } else {
                                    homeDividerColor
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerSectionCard(
    item: HomeSectionItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("home_banner_card_${item.id}"),
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.bannerCardCornerRadius),
        color = homeCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = homeDividerColor.copy(alpha = 0.72f)
        )
    ) {
        Box {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.46f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.badge.isNullOrBlank() && item.badge != item.title) {
                    Text(
                        text = item.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_banner_badge_${item.id}")
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = HomeDiscoveryLayoutSpec.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = HomeDiscoveryLayoutSpec.subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySectionCard(
    item: HomeSectionItemUiModel,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .testTag("home_discovery_card_${item.id}")
            .width(HomeDiscoveryLayoutSpec.discoveryCardWidth)
            .height(HomeDiscoveryLayoutSpec.discoveryCardHeight),
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.standardCardCornerRadius),
        color = homeCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = homeDividerColor.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDiscoveryLayoutSpec.discoveryCardWidth),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = HomeDiscoveryLayoutSpec.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        maxLines = HomeDiscoveryLayoutSpec.subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSectionCard(
    item: HomeSectionItemUiModel,
    onClick: () -> Unit
) {
    val icon = resolveCompactSectionCardIcon(item)
    val isPrimary = item.title.contains("每日推荐")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .testTag("home_compact_card_${item.id}")
            .width(HomeDiscoveryLayoutSpec.compactCardWidth)
            .height(HomeDiscoveryLayoutSpec.compactCardHeight),
        shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.compactCardCornerRadius),
        color = homeMutedCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = homeDividerColor.copy(alpha = 0.52f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Surface(
                modifier = Modifier
                    .size(HomeDiscoveryLayoutSpec.compactImageSize)
                    .testTag("home_compact_card_icon_${item.id}"),
                shape = RoundedCornerShape(18.dp),
                color = if (isPrimary) homeAccentColor else homeCardColor,
                tonalElevation = 0.dp,
                shadowElevation = if (isPrimary) 6.dp else 2.dp,
                border = if (isPrimary) {
                    null
                } else {
                    BorderStroke(
                        width = 1.dp,
                        color = homeDividerColor.copy(alpha = 0.36f)
                    )
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPrimary) Color.White else homeAccentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = homeTitleColor,
                maxLines = HomeDiscoveryLayoutSpec.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeSongColumn(
    columnIndex: Int,
    items: List<HomeSectionItemUiModel>,
    onAction: (HomeAction) -> Unit
) {
    val columnWidth = LocalConfiguration.current.screenWidthDp.dp * HomeDiscoveryLayoutSpec.songCardWidthFraction

    Box(
        modifier = Modifier
            .width(columnWidth)
            .testTag("home_song_column_$columnIndex")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HomeDiscoveryLayoutSpec.songColumnItemSpacing)
        ) {
            items.forEachIndexed { itemIndex, item ->
                HomeSongRow(
                    item = item,
                    onAction = onAction
                )
                if (itemIndex != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 70.dp, end = 2.dp, top = 9.dp, bottom = 9.dp)
                            .height(1.dp)
                            .background(homeDividerColor.copy(alpha = 0.42f))
                            .testTag("home_song_divider_${columnIndex}_$itemIndex")
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSongRow(
    item: HomeSectionItemUiModel,
    onAction: (HomeAction) -> Unit
) {
    val songCard = item.songCard ?: return
    var menuExpanded by remember(item.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeDiscoveryLayoutSpec.songCardHeight)
            .testTag("home_song_row_${item.id}")
            .clickable { onAction(item.action) }
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(HomeDiscoveryLayoutSpec.songCardCoverSize)
                .testTag("home_song_row_cover_${item.id}"),
            shape = RoundedCornerShape(HomeDiscoveryLayoutSpec.songCardCoverCornerRadius),
            color = homeMutedCardColor
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = homeAccentColor
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = songCard.metadataLine,
                style = MaterialTheme.typography.bodyMedium,
                color = homeTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = songCard.recommendReason.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = homeAccentColor.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier.width(26.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(HomeDiscoveryLayoutSpec.songCardMenuButtonSize)
                        .testTag("home_song_row_more_${item.id}"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = homeTextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    songCard.menuActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuExpanded = false
                                onAction(action.action)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun HomeSectionUiModel.usesSongCardLayout(): Boolean {
    if (layout != HomeSectionLayout.HORIZONTAL_LIST || items.isEmpty()) {
        return false
    }
    return items.all { it.songCard != null }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = homeTitleColor
    )
}

private fun resolveCompactSectionCardIcon(
    item: HomeSectionItemUiModel
): androidx.compose.ui.graphics.vector.ImageVector {
    val normalizedTitle = item.title.lowercase()
    return when {
        normalizedTitle.contains("搜索") -> Icons.Rounded.Search
        normalizedTitle.contains("歌单") -> Icons.Rounded.LibraryMusic
        normalizedTitle.contains("推荐") || normalizedTitle.contains("私人") -> Icons.Rounded.Home
        item.id.hashCode().mod(2) == 0 -> Icons.Rounded.LibraryMusic
        else -> Icons.Rounded.Album
    }
}

private val homeOverviewBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFCFCFE),
        Color(0xFFF7F7FA),
        Color(0xFFF4F4F8)
    )
)

private val homeCardColor = Color(0xFFFFFFFF)
private val homeMutedCardColor = Color(0xFFF7F7FA)
private val homeSearchSurfaceColor = Color(0xFFF5F4F6)
private val homeBrandColor = Color(0xFFDA2C21)
private val homeTitleColor = Color(0xFF171312)
private val homeAccentColor = homeBrandColor
private val homeDividerColor = Color(0xFFE9E5E1)
private val homeTextSecondary = Color(0xFF7C726C)
