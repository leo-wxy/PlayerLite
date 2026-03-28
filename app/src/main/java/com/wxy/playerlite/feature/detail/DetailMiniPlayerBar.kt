package com.wxy.playerlite.feature.detail

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.feature.main.HomeChromeLayoutSpec
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerBar
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerBarTestTags
import com.wxy.playerlite.feature.player.ui.SharedMiniPlayerOpenPlayerClickTarget
import com.wxy.playerlite.feature.player.ui.resolveSharedMiniPlayerBarState

internal val DetailMiniPlayerBottomPadding = 0.dp
internal val DetailMiniPlayerContentPadding = HomeChromeLayoutSpec.miniPlayerMinHeight

@Composable
internal fun BoxScope.DetailMiniPlayerHost(
    bottomPadding: Dp,
    content: @Composable (Modifier) -> Unit
) {
    content(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPadding)
    )
}

@Composable
internal fun DetailMiniPlayerBar(
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
            cardTag = "detail_mini_player_card",
            prefix = "detail_mini_player"
        ),
        openPlayerClickTarget = SharedMiniPlayerOpenPlayerClickTarget.Card,
        canSkipPrevious = playerState.canSkipPrevious,
        canSkipNext = playerState.canSkipNext,
        onOpenPlayer = onOpenPlayer,
        onTogglePlayback = onTogglePlayback,
        onOpenPlaylist = onOpenPlaylist,
        onSkipPrevious = onSkipPrevious,
        onSkipNext = onSkipNext
    )
}
