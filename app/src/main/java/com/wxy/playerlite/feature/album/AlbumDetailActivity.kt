package com.wxy.playerlite.feature.album

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.feature.detail.BasePlaybackDetailActivity
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent

const val EXTRA_ALBUM_ID = "album_id"

class AlbumDetailActivity : BasePlaybackDetailActivity() {
    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(
            albumId = albumIdFrom(intent),
            repository = AppContainer.albumDetailRepository(this),
            playbackGateway = AppPlaybackGraph.detailPlaybackGateway(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPlaybackDetailContent { bottomOverlayPadding ->
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val headerChromeProgressState = remember { mutableFloatStateOf(0f) }
            val statusBarReferenceColor = when (val contentState = state.contentState) {
                is AlbumContentUiState.Content -> rememberDynamicHeroAccentColor(
                    imageUrl = contentState.content.coverUrl
                )

                else -> MaterialTheme.colorScheme.primary
            }
            val statusBarBlendColor = lerp(
                start = statusBarReferenceColor,
                stop = MaterialTheme.colorScheme.background,
                fraction = headerChromeProgressState.floatValue.coerceIn(0f, 1f)
            )
            val useLightStatusBarContent = shouldUseLightStatusBarContent(statusBarBlendColor)
            val topBarContentColor = if (useLightStatusBarContent) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !useLightStatusBarContent
            }
            BackHandler(onBack = ::finish)
            val headerCommentCountText = when (val dynamicState = state.dynamicState) {
                is AlbumDynamicUiState.Content -> dynamicState.content.commentCount.toString()
                else -> null
            }
            AlbumDetailScreen(
                state = state,
                heroAccentColor = statusBarReferenceColor,
                topBarContentColor = topBarContentColor,
                headerCommentCountText = headerCommentCountText,
                onBack = ::finish,
                onRetry = viewModel::retry,
                onHeaderChromeProgressChange = {
                    headerChromeProgressState.floatValue = it
                },
                onLoadMore = viewModel::loadMoreTracks,
                onPlayAll = {
                    if (viewModel.playAll()) {
                        openPlayerAfterQueueReplacement()
                    }
                },
                onTrackClick = { index ->
                    if (viewModel.playTrack(index)) {
                        openPlayerAfterQueueReplacement()
                    }
                },
                bottomOverlayPadding = bottomOverlayPadding
            )
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            albumId: String
        ): Intent {
            return Intent(context, AlbumDetailActivity::class.java)
                .putExtra(EXTRA_ALBUM_ID, albumId)
        }

        fun albumIdFrom(intent: Intent): String {
            return requireNotNull(intent.getStringExtra(EXTRA_ALBUM_ID)) {
                "Album detail requires album id"
            }
        }
    }
}
