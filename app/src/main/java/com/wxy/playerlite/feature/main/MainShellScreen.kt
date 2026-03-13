package com.wxy.playerlite.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.user.AccountCardSurface
import com.wxy.playerlite.feature.user.AccountPageBackground
import com.wxy.playerlite.feature.user.AccountPrimaryButton
import com.wxy.playerlite.feature.user.AccountStatusChip
import com.wxy.playerlite.feature.user.AccountVisualStyle
import com.wxy.playerlite.feature.user.accountHeroBrush
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import kotlinx.coroutines.flow.collectLatest

private val UserCenterCompactHorizontalPadding = 12.dp

@Composable
internal fun MainBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        NavigationBarItem(
            selected = selectedTab == MainTab.HOME,
            onClick = { onTabSelected(MainTab.HOME) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.USER_CENTER,
            onClick = { onTabSelected(MainTab.USER_CENTER) },
            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            label = { Text("我的") }
        )
    }
}

@Composable
internal fun HomeOverviewScreen(
    playerState: PlayerUiState,
    overviewState: HomeOverviewUiState,
    onSearchClick: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (ContentEntryAction) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = HOME_OVERVIEW_BACKGROUND_BRUSH
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_discovery_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 96.dp,
                end = 20.dp,
                bottom = 148.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            onItemClick = onItemClick
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

        HomePlayEntryCard(
            playerState = playerState,
            onOpenPlayer = onOpenPlayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp)
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Box(
            modifier = Modifier
                .testTag("home_search_box_container")
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            RowLikeSearchContent(
                keyword = keyword,
                modifier = Modifier.testTag("home_search_box")
            )
        }
    }
}

@Composable
private fun RowLikeSearchContent(
    keyword: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = HOME_ACCENT_RED.copy(alpha = 0.86f)
        )
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeOverviewInlineError(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f)
        )
    ) {
        androidx.compose.foundation.layout.Row(
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
        shape = RoundedCornerShape(28.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f)
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
    onItemClick: (ContentEntryAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_section_${section.code}"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (section.title.isNotBlank()) {
            HomeSectionTitle(title = section.title)
        }

        if (HomeDiscoveryLayoutSpec.usesCarousel(section.layout)) {
            HomeBannerCarousel(
                items = section.items,
                onItemClick = onItemClick
            )
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
                    when (section.layout) {
                        HomeSectionLayout.BANNER -> BannerSectionCard(
                            item = item,
                            onClick = { onItemClick(item.action) }
                        )

                        HomeSectionLayout.ICON_GRID -> CompactSectionCard(
                            item = item,
                            onClick = { onItemClick(item.action) }
                        )

                        HomeSectionLayout.HORIZONTAL_LIST -> DiscoverySectionCard(
                            item = item,
                            onClick = { onItemClick(item.action) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBannerCarousel(
    items: List<HomeSectionItemUiModel>,
    onItemClick: (ContentEntryAction) -> Unit
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
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val selectedIndex = pagerState.currentPage % actualCount
                repeat(actualCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(
                                width = if (selectedIndex == index) 18.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selectedIndex == index) {
                                    HOME_ACCENT_RED
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
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
        shape = RoundedCornerShape(26.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        )
    ) {
        Box {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.42f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                        ,
                        maxLines = HomeDiscoveryLayoutSpec.titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.badge.isNullOrBlank() && item.badge != item.title) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = HOME_ACCENT_RED.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = item.badge,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = HomeDiscoveryLayoutSpec.subtitleMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
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
        shape = RoundedCornerShape(22.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
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
                    .aspectRatio(HomeDiscoveryLayoutSpec.discoveryImageAspectRatio)
                    .clip(
                        RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp
                        )
                    ),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
    val backgroundColor = HomeDiscoveryLayoutSpec.dailyShortcutBackgroundColor(
        seed = item.id.ifBlank { item.title }
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .testTag("home_compact_card_${item.id}")
            .width(HomeDiscoveryLayoutSpec.compactCardWidth)
            .height(HomeDiscoveryLayoutSpec.compactCardHeight),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.55f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4E342E),
                maxLines = HomeDiscoveryLayoutSpec.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun HomePlayEntryCard(
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_play_entry_card"),
        shape = RoundedCornerShape(28.dp),
        color = HOME_PANEL_COLOR.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = HOME_ACCENT_RED.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "当前播放",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HOME_ACCENT_RED
                )
            }
            Text(
                text = playerState.selectedFileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playerState.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onOpenPlayer,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HOME_ACCENT_RED,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_overview_play_entry")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "进入播放页",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HOME_ACCENT_RED)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun PlayerExpandedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    topEndContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            content()
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 220,
                    delayMillis = 90,
                    easing = LinearOutSlowInEasing
                )
            ) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(durationMillis = 220, delayMillis = 90)
            ),
            exit = fadeOut(animationSpec = tween(durationMillis = 120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .testTag("player_expanded_back_button"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回首页"
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp)
                ) {
                    topEndContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserCenterScreen(
    userState: UserSessionUiState,
    contentState: UserCenterUiState,
    onTabSelected: (UserCenterTab) -> Unit,
    onRetryCurrentTab: () -> Unit,
    onContentClick: (ContentEntryAction) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AccountPageBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .testTag("user_center_scroll_content")
                .padding(horizontal = UserCenterCompactHorizontalPadding),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                UserCenterProfileHeader(
                    userState = userState,
                    modifier = Modifier.padding(bottom = if (userState.isLoggedIn) 12.dp else 18.dp)
                )
            }

            if (userState.isLoggedIn) {
                userCenterLoggedInItems(
                    contentState = contentState,
                    onTabSelected = onTabSelected,
                    onRetryCurrentTab = onRetryCurrentTab,
                    onContentClick = onContentClick,
                    onLogoutClick = onLogoutClick
                )
            } else {
                userCenterLoggedOutItems(
                    onLoginClick = onLoginClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.userCenterLoggedInItems(
    contentState: UserCenterUiState,
    onTabSelected: (UserCenterTab) -> Unit,
    onRetryCurrentTab: () -> Unit,
    onContentClick: (ContentEntryAction) -> Unit,
    onLogoutClick: () -> Unit
) {
    stickyHeader(key = "user-center-tabs-header") {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_sticky_tabs_header"),
                shape = UserCenterPanelHeaderShape
            ) {
                Box(modifier = Modifier.testTag("user_center_content_panel_header")) {
                    UserCenterTabs(
                        selectedTab = contentState.selectedTab,
                        onTabSelected = onTabSelected
                    )
                }
            }
        }
    }

    when (val currentState = contentState.currentTabState) {
        UserCenterTabContentState.Idle,
        UserCenterTabContentState.Loading -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_content_panel_body"),
                shape = UserCenterPanelBodyShape
            ) {
                UserCenterStatusPanel(
                    title = "正在加载${contentState.selectedTab.label}",
                    subtitle = "正在同步当前账号的个人内容，请稍候。",
                    tag = "user_center_content_loading"
                ) {
                    CircularProgressIndicator(color = AccountVisualStyle.accentColor)
                }
            }
        }

        UserCenterTabContentState.Empty -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_content_panel_body"),
                shape = UserCenterPanelBodyShape
            ) {
                UserCenterStatusPanel(
                    title = "${contentState.selectedTab.label}暂时为空",
                    subtitle = "当前账号下还没有可展示的内容，稍后再来看看。",
                    tag = "user_center_content_empty"
                )
            }
        }

        is UserCenterTabContentState.Error -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_content_panel_body"),
                shape = UserCenterPanelBodyShape
            ) {
                UserCenterStatusPanel(
                    title = "${contentState.selectedTab.label}加载失败",
                    subtitle = currentState.message,
                    tag = "user_center_content_error"
                ) {
                    OutlinedButton(
                        onClick = onRetryCurrentTab,
                        modifier = Modifier.testTag("user_center_content_retry")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重试")
                    }
                }
            }
        }

        is UserCenterTabContentState.Content -> itemsIndexed(
            items = currentState.items,
            key = { _, item -> item.id }
        ) { index, item ->
            UserCenterCollectionCard(
                item = item,
                selectedTab = contentState.selectedTab,
                shape = userCenterContentItemShape(
                    index = index,
                    total = currentState.items.size
                ),
                showTopDivider = true,
                onClick = { onContentClick(item.action) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == 0) {
                            Modifier.testTag("user_center_content_panel_body")
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }

    item {
        OutlinedButton(
            onClick = onLogoutClick,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                width = 1.dp,
                color = AccountVisualStyle.accentColor.copy(alpha = 0.32f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .testTag("user_center_secondary_action")
        ) {
            Text(
                text = "退出登录",
                color = AccountVisualStyle.accentColor
            )
        }
    }
}

private fun LazyListScope.userCenterLoggedOutItems(
    onLoginClick: () -> Unit
) {
    item {
        AccountCardSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .testTag("user_center_info_card")
        ) {
            Text(
                text = "当前为游客浏览模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "登录后可同步在线身份、推荐上下文以及后续扩展的个人中心能力。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "收藏歌手、专栏和用户歌单会在登录后通过 Tab 分区展示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("user_center_future_hint")
            )
        }
    }

    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_center_primary_action")
        ) {
            AccountPrimaryButton(
                text = "去登录",
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_login_button")
            )
        }
    }
}

@Composable
private fun UserCenterTabs(
    selectedTab: UserCenterTab,
    onTabSelected: (UserCenterTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_center_tabs"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        UserCenterTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onTabSelected(tab) }
                    .testTag("user_center_tab_${tab.tagSuffix}"),
                color = if (selected) {
                    AccountVisualStyle.accentColor
                } else {
                    AccountVisualStyle.accentSoftColor
                }
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else AccountVisualStyle.accentTextColor,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private val UserCenterPanelHeaderShape = RoundedCornerShape(
    topStart = AccountVisualStyle.cardCorner,
    topEnd = AccountVisualStyle.cardCorner,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)

private val UserCenterPanelBodyShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomStart = AccountVisualStyle.cardCorner,
    bottomEnd = AccountVisualStyle.cardCorner
)

private fun userCenterContentItemShape(
    index: Int,
    total: Int
): RoundedCornerShape {
    return when {
        total <= 0 -> RoundedCornerShape(AccountVisualStyle.cardCorner)
        index == total - 1 -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = AccountVisualStyle.cardCorner,
            bottomEnd = AccountVisualStyle.cardCorner
        )

        else -> RoundedCornerShape(0.dp)
    }
}

@Composable
private fun UserCenterPanelSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun UserCenterStatusPanel(
    title: String,
    subtitle: String,
    tag: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

@Composable
private fun UserCenterCollectionCard(
    item: UserCenterCollectionItemUiModel,
    selectedTab: UserCenterTab,
    shape: RoundedCornerShape,
    showTopDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("user_center_content_item_${item.id}")
        ) {
            if (showTopDivider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = AccountVisualStyle.accentSoftColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = when (selectedTab) {
                                    UserCenterTab.ARTISTS -> Icons.Rounded.Person
                                    UserCenterTab.COLUMNS -> Icons.Rounded.Album
                                    UserCenterTab.PLAYLISTS -> Icons.Rounded.Album
                                },
                                contentDescription = null,
                                tint = AccountVisualStyle.accentColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.meta?.let { meta ->
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = AccountVisualStyle.accentTextColor
                        )
                    }
                }

                item.badge?.let { badge ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AccountVisualStyle.accentSoftColor
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = AccountVisualStyle.accentTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCenterProfileHeader(
    userState: UserSessionUiState,
    modifier: Modifier = Modifier
) {
    val headerShape = RoundedCornerShape(AccountVisualStyle.heroCorner)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_center_profile_header")
            .shadow(18.dp, headerShape, clip = false)
            .clip(headerShape)
            .background(accountHeroBrush())
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountStatusChip(
                text = if (userState.isLoggedIn) "个人主页" else "欢迎来到个人主页"
            )
            Surface(
                modifier = Modifier
                    .size(112.dp)
                    .testTag("user_center_avatar"),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 14.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!userState.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userState.avatarUrl,
                            contentDescription = "用户头像",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                    shape = CircleShape
                                ),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = AccountVisualStyle.accentColor,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            Text(
                text = userState.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("user_center_title")
            )

            Text(
                text = userState.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("user_center_summary")
            )
        }
    }
}

private val HOME_OVERVIEW_BACKGROUND_BRUSH = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFF8F5),
        Color(0xFFFFF4EE),
        Color(0xFFFFFFFF)
    )
)

private val HOME_PANEL_COLOR = Color.White
private val HOME_ACCENT_RED = Color(0xFFD33A31)

@Composable
internal fun HomeContent(
    homeSurfaceMode: HomeSurfaceMode,
    overviewContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = homeSurfaceMode,
        transitionSpec = {
            val enteringScale = if (targetState == HomeSurfaceMode.PLAYER_EXPANDED) {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.OVERVIEW).playerScale
            } else {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.PLAYER_EXPANDED).overviewScale
            }
            val enteringOffsetFraction = if (targetState == HomeSurfaceMode.PLAYER_EXPANDED) {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.OVERVIEW).playerOffsetFraction
            } else {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.PLAYER_EXPANDED).overviewOffsetFraction
            }
            val exitingScale = if (targetState == HomeSurfaceMode.PLAYER_EXPANDED) {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.PLAYER_EXPANDED).overviewScale
            } else {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.OVERVIEW).playerScale
            }
            val exitingOffsetFraction = if (targetState == HomeSurfaceMode.PLAYER_EXPANDED) {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.PLAYER_EXPANDED).overviewOffsetFraction
            } else {
                HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.OVERVIEW).playerOffsetFraction
            }
            (
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 380,
                        delayMillis = 40,
                        easing = LinearOutSlowInEasing
                    )
                ) + scaleIn(
                    initialScale = enteringScale,
                    animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing)
                ) + slideInVertically(
                    initialOffsetY = { fullHeight ->
                        (fullHeight * enteringOffsetFraction).toInt()
                    },
                    animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing)
                )
                ) togetherWith (
                fadeOut(
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutLinearInEasing
                    )
                ) + scaleOut(
                    targetScale = exitingScale,
                    animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing)
                ) + slideOutVertically(
                    targetOffsetY = { fullHeight ->
                        (fullHeight * exitingOffsetFraction).toInt()
                    },
                    animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing)
                )
                )
        },
        label = "home_surface_mode"
    ) { mode ->
        when (mode) {
            HomeSurfaceMode.OVERVIEW -> {
                val targets = HomeSurfaceMotionSpec.targetsFor(mode)
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = targets.overviewAlpha
                        scaleX = targets.overviewScale
                        scaleY = targets.overviewScale
                    }
                ) {
                    overviewContent()
                }
            }

            HomeSurfaceMode.PLAYER_EXPANDED -> {
                val targets = HomeSurfaceMotionSpec.targetsFor(mode)
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = targets.playerAlpha
                        scaleX = targets.playerScale
                        scaleY = targets.playerScale
                    }
                ) {
                    expandedContent()
                }
            }
        }
    }
}
