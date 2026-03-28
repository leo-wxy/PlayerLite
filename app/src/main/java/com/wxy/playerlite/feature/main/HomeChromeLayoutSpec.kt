package com.wxy.playerlite.feature.main

import androidx.compose.ui.unit.dp

internal object HomeChromeLayoutSpec {
    val bottomBarCornerRadius = 24.dp
    val bottomBarItemCornerRadius = 18.dp
    val bottomBarMinHeight = 72.dp
    val bottomBarShadowElevation = 6.dp
    val bottomBarOuterVerticalPadding = 4.dp
    val bottomBarOuterHorizontalPadding = 26.dp
    val bottomBarBottomClearance = 4.dp
    const val bottomBarWidthFraction = 0.76f
    val bottomBarMaxWidth = 296.dp
    val bottomBarOverlayHeight =
        bottomBarMinHeight + bottomBarOuterVerticalPadding * 2 + bottomBarBottomClearance

    val miniPlayerCornerRadius = 14.dp
    val miniPlayerBodyCornerRadius = 10.dp
    val miniPlayerActionCornerRadius = 10.dp
    val miniPlayerShadowElevation = 6.dp
    val miniPlayerMinHeight = 60.dp
    const val miniPlayerWidthFraction = 1f
    val miniPlayerMaxWidth = 420.dp
    val miniPlayerArtworkSize = miniPlayerMinHeight
    val miniPlayerPrimaryButtonSize = 34.dp
    val miniPlayerPrimaryIconSize = 18.dp
    val miniPlayerPlaylistButtonSize = 28.dp
    val miniPlayerProgressTrackHeight = 4.dp
    val miniPlayerProgressTrackOverlap = 0.dp
    val miniPlayerProgressTrackHorizontalPadding = 1.dp
    val miniPlayerProgressTrackVerticalPadding = 0.dp
    const val miniPlayerProgressTrackAlpha = 0.1f
    val homeMiniPlayerBottomSpacing = bottomBarOverlayHeight + 4.dp
    val homeOverviewScrollBottomPadding = homeMiniPlayerBottomSpacing + 66.dp
    val userCenterScrollBottomPadding = bottomBarOverlayHeight + 28.dp
}
