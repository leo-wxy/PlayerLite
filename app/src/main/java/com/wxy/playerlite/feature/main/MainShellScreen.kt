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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import kotlinx.coroutines.flow.collectLatest

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
                        HomeDiscoverySection(section = section)
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
    section: HomeSectionUiModel
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
            HomeBannerCarousel(items = section.items)
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
                        HomeSectionLayout.BANNER -> BannerSectionCard(item)
                        HomeSectionLayout.ICON_GRID -> CompactSectionCard(item)
                        HomeSectionLayout.HORIZONTAL_LIST -> DiscoverySectionCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBannerCarousel(
    items: List<HomeSectionItemUiModel>
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
    modifier: Modifier = Modifier
) {
    Surface(
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
    item: HomeSectionItemUiModel
) {
    Surface(
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
    item: HomeSectionItemUiModel
) {
    val backgroundColor = HomeDiscoveryLayoutSpec.dailyShortcutBackgroundColor(
        seed = item.id.ifBlank { item.title }
    )
    Surface(
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
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

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
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
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
        }
    }
}

@Composable
internal fun UserCenterScreen(
    userState: UserSessionUiState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFBF7),
                        Color(0xFFFFF2E5),
                        Color(0xFFFFF7F0)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(112.dp)
                    .testTag("user_center_avatar"),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
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
                            tint = Color(0xFFEF8E4B),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            Text(
                text = userState.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("user_center_title")
            )

            Text(
                text = userState.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("user_center_summary")
            )

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "当前先展示头像、昵称与基础信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "收藏歌单等内容后续继续补充",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("user_center_future_hint")
                    )
                }
            }

            if (userState.isLoggedIn) {
                OutlinedButton(
                    onClick = onLogoutClick,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("退出登录")
                }
            } else {
                Button(
                    onClick = onLoginClick,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF8E4B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("user_center_login_button")
                ) {
                    Text("去登录")
                }
            }
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
