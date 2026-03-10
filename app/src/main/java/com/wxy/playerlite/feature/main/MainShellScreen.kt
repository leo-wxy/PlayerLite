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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.R
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.user.model.UserSessionUiState

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
    userState: UserSessionUiState,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFBF6),
                        Color(0xFFFFF3E4),
                        Color(0xFFFFF8F1)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(176.dp)
                        .testTag("home_overview_cover"),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.92f),
                    tonalElevation = 8.dp,
                    shadowElevation = 18.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playerlite_brand),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(132.dp)
                        )
                    }
                }

                Text(
                    text = "PLAYER LITE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = if (userState.isLoggedIn) "已登录 · ${userState.title}" else "本地播放仍可直接使用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = playerState.selectedFileName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = playerState.statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "首页先保留轻量概览，点击下方进入播放展开态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onOpenPlayer,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF8E4B),
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
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "进入播放页",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
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
