package com.wxy.playerlite.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerBar
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerBarTestTags
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerOpenPlayerClickTarget
import com.wxy.playerlite.feature.player.ui.resolveSharedMiniPlayerBarState
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.feature.user.AccountCardSurface
import com.wxy.playerlite.feature.user.AccountPrimaryButton
import com.wxy.playerlite.feature.user.AccountVisualStyle
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val UserCenterCompactHorizontalPadding = 12.dp

@Composable
internal fun MainBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = HomeChromeLayoutSpec.bottomBarOuterHorizontalPadding,
                top = HomeChromeLayoutSpec.bottomBarOuterVerticalPadding,
                end = HomeChromeLayoutSpec.bottomBarOuterHorizontalPadding,
                bottom = HomeChromeLayoutSpec.bottomBarBottomClearance
            )
            .testTag("main_bottom_bar_root"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(HomeChromeLayoutSpec.bottomBarWidthFraction)
                .widthIn(max = HomeChromeLayoutSpec.bottomBarMaxWidth)
                .height(HomeChromeLayoutSpec.bottomBarMinHeight)
                .testTag("main_bottom_bar_container"),
            shape = RoundedCornerShape(HomeChromeLayoutSpec.bottomBarCornerRadius),
            color = visualTokens.surfacePrimary.copy(alpha = 0.97f),
            tonalElevation = 2.dp,
            shadowElevation = HomeChromeLayoutSpec.bottomBarShadowElevation,
            border = BorderStroke(
                width = 1.dp,
                color = visualTokens.dividerSubtle
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeBottomBarItem(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main_bottom_bar_home"),
                    selected = selectedTab == MainTab.HOME,
                    onClick = { onTabSelected(MainTab.HOME) },
                    icon = Icons.Rounded.Home,
                    label = "首页",
                    accentColor = visualTokens.accentStrong,
                    unselectedContentColor = visualTokens.textSecondary
                )
                HomeBottomBarItem(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("main_bottom_bar_user"),
                    selected = selectedTab == MainTab.USER_CENTER,
                    onClick = { onTabSelected(MainTab.USER_CENTER) },
                    icon = Icons.Rounded.Person,
                    label = "我的",
                    accentColor = visualTokens.accentStrong,
                    unselectedContentColor = visualTokens.textSecondary
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(HomeChromeLayoutSpec.bottomBarItemCornerRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else unselectedContentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accentColor else unselectedContentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun MainShellScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onDismissPlaylistSheet: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    onSelectPlaylistItem: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onRemovePlaylistItem: (Int) -> Unit,
    onMovePlaylistItem: (Int, Int) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 220),
                            initialOffsetY = { fullHeight -> fullHeight / 2 }
                        ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 180),
                            targetOffsetY = { fullHeight -> fullHeight / 2 }
                        )
                ) {
                    MainBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected
                    )
                }
            }
        ) { innerPadding ->
            content(innerPadding)
        }

        MainShellMiniPlayerChrome(
            playerState = playerState,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayback = onTogglePlayback,
            onTogglePlaylistSheet = onTogglePlaylistSheet,
            onDismissPlaylistSheet = onDismissPlaylistSheet,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onShowOriginalOrderInShuffleChange = onShowOriginalOrderInShuffleChange,
            onSelectPlaylistItem = onSelectPlaylistItem,
            onClearPlaylist = onClearPlaylist,
            onRemovePlaylistItem = onRemovePlaylistItem,
            onMovePlaylistItem = onMovePlaylistItem,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext
        )
    }
}

@Composable
internal fun BoxScope.MainShellMiniPlayerOverlay(
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    if (!playerState.hasSelection) {
        return
    }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    HomePlayEntryCard(
        playerState = playerState,
        onOpenPlayer = onOpenPlayer,
        onTogglePlayback = onTogglePlayback,
        onOpenPlaylist = onOpenPlaylist,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = HomeChromeLayoutSpec.homeMiniPlayerBottomSpacing + navigationBottomPadding
            )
    )
}

@Composable
internal fun BoxScope.MainShellMiniPlayerChrome(
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onDismissPlaylistSheet: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    onSelectPlaylistItem: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onRemovePlaylistItem: (Int) -> Unit,
    onMovePlaylistItem: (Int, Int) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    MainShellMiniPlayerOverlay(
        playerState = playerState,
        onOpenPlayer = onOpenPlayer,
        onTogglePlayback = onTogglePlayback,
        onOpenPlaylist = onTogglePlaylistSheet,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext
    )
    PlaylistBottomSheet(
        visible = playerState.showPlaylistSheet,
        items = playerState.playlistItems,
        activeIndex = playerState.activePlaylistIndex,
        playbackMode = playerState.playbackMode,
        showOriginalOrderInShuffle = playerState.showOriginalOrderInShuffle,
        canReorder = playerState.canReorderPlaylist,
        onDismiss = onDismissPlaylistSheet,
        onCyclePlaybackMode = onCyclePlaybackMode,
        onShowOriginalOrderInShuffleChange = onShowOriginalOrderInShuffleChange,
        onSelect = onSelectPlaylistItem,
        onClearAll = onClearPlaylist,
        onRemove = onRemovePlaylistItem,
        onMove = onMovePlaylistItem
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePlayEntryCard(
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val miniPlayerState = resolveSharedMiniPlayerBarState(playerState) ?: return

    SharedMiniPlayerBar(
        modifier = modifier,
        state = miniPlayerState,
        testTags = SharedMiniPlayerBarTestTags(
            cardTag = "home_play_entry_card",
            prefix = "home_mini_player"
        ),
        openPlayerClickTarget = SharedMiniPlayerOpenPlayerClickTarget.Body,
        canSkipPrevious = playerState.canSkipPrevious,
        canSkipNext = playerState.canSkipNext,
        onOpenPlayer = onOpenPlayer,
        onTogglePlayback = onTogglePlayback,
        onOpenPlaylist = onOpenPlaylist,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserCenterScreen(
    userState: UserSessionUiState,
    contentState: UserCenterUiState,
    onRetryPlaylists: () -> Unit,
    onContentClick: (ContentEntryAction) -> Unit,
    onOpenLikedSongs: (String?) -> Unit = {},
    onOpenRecentSongs: () -> Unit = {},
    onOpenLocalSongs: () -> Unit = {},
    onOpenPlaylistImport: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    bottomContentPadding: Dp = HomeChromeLayoutSpec.userCenterScrollBottomPadding,
    modifier: Modifier = Modifier
) {
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    UserCenterPageBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .testTag("user_center_scroll_content")
                .padding(horizontal = UserCenterCompactHorizontalPadding),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = bottomContentPadding + navigationBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                UserCenterProfileHeader(
                    userState = userState,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            item {
                UserCenterQuickEntryRow(
                    onOpenLiked = { onOpenLikedSongs(contentState.likedPlaylistId) },
                    onOpenRecent = onOpenRecentSongs,
                    onOpenLocal = onOpenLocalSongs,
                    onOpenImport = onOpenPlaylistImport,
                    modifier = Modifier.padding(bottom = if (userState.isLoggedIn) 18.dp else 14.dp)
                )
            }

            if (userState.isLoggedIn) {
                userCenterLoggedInItems(
                    contentState = contentState,
                    onRetryPlaylists = onRetryPlaylists,
                    onContentClick = onContentClick
                )
            } else {
                userCenterLoggedOutItems(
                    onLoginClick = onLoginClick
                )
            }
        }
    }
}

@Composable
private fun UserCenterPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val brush = remember(background, surface) {
        Brush.verticalGradient(
            colors = listOf(background, surface)
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
    ) {
        content()
    }
}

@Composable
private fun UserCenterQuickEntryRow(
    onOpenLiked: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_center_quick_entries"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserCenterQuickEntry(
            icon = Icons.Rounded.Favorite,
            label = "喜欢",
            onClick = onOpenLiked,
            modifier = Modifier.testTag("user_center_quick_entry_liked")
        )
        UserCenterQuickEntry(
            icon = Icons.Rounded.History,
            label = "最近",
            onClick = onOpenRecent,
            modifier = Modifier.testTag("user_center_quick_entry_recent")
        )
        UserCenterQuickEntry(
            icon = Icons.Rounded.DownloadForOffline,
            label = "本地",
            onClick = onOpenLocal,
            modifier = Modifier.testTag("user_center_quick_entry_local")
        )
        UserCenterQuickEntry(
            icon = Icons.Rounded.FileDownload,
            label = "导入",
            onClick = onOpenImport,
            modifier = Modifier.testTag("user_center_quick_entry_import")
        )
    }
}

@Composable
private fun UserCenterQuickEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(18.dp),
            color = AccountVisualStyle.accentColor.copy(alpha = 0.12f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccountVisualStyle.accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun LazyListScope.userCenterLoggedInItems(
    contentState: UserCenterUiState,
    onRetryPlaylists: () -> Unit,
    onContentClick: (ContentEntryAction) -> Unit
) {
    item {
        UserCenterPlaylistsSectionHeader(
            subtitle = (contentState.playlistsState as? UserCenterPlaylistsState.Content)
                ?.items
                ?.size
                ?.takeIf { it > 0 }
                ?.let { "共 $it 个歌单" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .testTag("user_center_playlists_section_header")
        )
    }

    when (val currentState = contentState.playlistsState) {
        UserCenterPlaylistsState.Idle,
        UserCenterPlaylistsState.Loading -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_playlists_panel"),
                shape = RoundedCornerShape(AccountVisualStyle.cardCorner)
            ) {
                UserCenterStatusPanel(
                    title = "正在加载歌单",
                    subtitle = "正在同步当前账号的歌单数据，请稍候。",
                    tag = "user_center_playlists_loading"
                ) {
                    CircularProgressIndicator(color = AccountVisualStyle.accentColor)
                }
            }
        }

        UserCenterPlaylistsState.Empty -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_playlists_panel"),
                shape = RoundedCornerShape(AccountVisualStyle.cardCorner)
            ) {
                UserCenterStatusPanel(
                    title = "还没有自建歌单",
                    subtitle = "自建歌单后，这里会展示你的歌单列表。",
                    tag = "user_center_playlists_empty"
                )
            }
        }

        is UserCenterPlaylistsState.Error -> item {
            UserCenterPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_center_playlists_panel"),
                shape = RoundedCornerShape(AccountVisualStyle.cardCorner)
            ) {
                UserCenterStatusPanel(
                    title = "歌单加载失败",
                    subtitle = currentState.message,
                    tag = "user_center_playlists_error"
                ) {
                    OutlinedButton(
                        onClick = onRetryPlaylists,
                        modifier = Modifier.testTag("user_center_playlists_retry")
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

        is UserCenterPlaylistsState.Content -> itemsIndexed(
            items = currentState.items,
            key = { _, item -> item.id }
        ) { index, item ->
            UserCenterCollectionCard(
                item = item,
                shape = userCenterContentItemShape(
                    index = index,
                    total = currentState.items.size
                ),
                showTopDivider = index > 0,
                onClick = { onContentClick(item.action) },
                modifier = Modifier.fillMaxWidth()
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
                text = "登录后可查看自己的歌单、最近播放等个人内容。",
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

private fun userCenterContentItemShape(
    index: Int,
    total: Int
): RoundedCornerShape {
    return when {
        total <= 0 -> RoundedCornerShape(AccountVisualStyle.cardCorner)
        index == 0 && total == 1 -> RoundedCornerShape(AccountVisualStyle.cardCorner)
        index == 0 -> RoundedCornerShape(
            topStart = AccountVisualStyle.cardCorner,
            topEnd = AccountVisualStyle.cardCorner,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )

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
                                imageVector = Icons.Rounded.LibraryMusic,
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

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun UserCenterProfileHeader(
    userState: UserSessionUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerShape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_center_profile_header"),
        shape = headerShape,
        color = Color.White.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("user_center_settings_entry"),
                onClick = onOpenSettings
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    tint = AccountVisualStyle.accentColor
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val levelBadge = remember(userState.summary) {
                    resolveUserLevelBadge(userState.summary)
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .testTag("user_center_avatar")
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
                                        width = 4.dp,
                                        color = Color.White.copy(alpha = 0.94f),
                                        shape = CircleShape
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = CircleShape,
                                color = AccountVisualStyle.accentSoftColor,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = null,
                                        tint = AccountVisualStyle.accentColor,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }
                    }

                    levelBadge?.let { badge ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp),
                            color = AccountVisualStyle.accentColor,
                            shape = RoundedCornerShape(999.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = userState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("user_center_title")
                )

                Text(
                    text = userState.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("user_center_summary")
                )
            }
        }
    }
}

private fun resolveUserLevelBadge(summary: String): String? {
    val match = Regex("""Lv\.?\s*(\d+)""").find(summary) ?: return null
    return "Lv${match.groupValues.getOrNull(1).orEmpty()}".takeIf { it.length > 2 }
}

@Composable
private fun UserCenterPlaylistsSectionHeader(
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "自建歌单",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}
