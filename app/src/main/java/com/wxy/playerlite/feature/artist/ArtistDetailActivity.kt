package com.wxy.playerlite.feature.artist

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.detail.BasePlaybackDetailActivity
import com.wxy.playerlite.feature.detail.rememberDynamicHeroAccentColor
import com.wxy.playerlite.feature.detail.shouldUseLightStatusBarContent
import com.wxy.playerlite.feature.player.runtime.RuntimeDetailPlaybackGateway

const val EXTRA_ARTIST_ID = "artist_id"

class ArtistDetailActivity : BasePlaybackDetailActivity() {
    private val viewModel: ArtistDetailViewModel by viewModels {
        ArtistDetailViewModel.factory(
            artistId = artistIdFrom(intent),
            repository = AppContainer.artistDetailRepository(this),
            playbackGateway = RuntimeDetailPlaybackGateway(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPlaybackDetailContent { bottomOverlayPadding ->
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val headerChromeProgressState = remember { mutableFloatStateOf(0f) }
            val statusBarReferenceColor = when (val headerState = state.headerState) {
                is ArtistDetailHeaderUiState.Content -> rememberDynamicHeroAccentColor(
                    imageUrl = headerState.content.coverUrl ?: headerState.content.avatarUrl
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
                window.statusBarColor = AndroidColor.TRANSPARENT
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !useLightStatusBarContent
            }
            BackHandler(onBack = ::finish)
            ArtistDetailScreen(
                state = state,
                heroAccentColor = statusBarReferenceColor,
                topBarContentColor = topBarContentColor,
                onBack = ::finish,
                onRetry = viewModel::retry,
                onHeaderChromeProgressChange = {
                    headerChromeProgressState.floatValue = it
                },
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
                onAlbumClick = { albumId ->
                    startActivity(AlbumDetailActivity.createIntent(this, albumId))
                },
                onLoadMoreAlbums = viewModel::loadMoreAlbums,
                bottomOverlayPadding = bottomOverlayPadding
            )
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            artistId: String
        ): Intent {
            return Intent(context, ArtistDetailActivity::class.java)
                .putExtra(EXTRA_ARTIST_ID, artistId)
        }

        fun artistIdFrom(intent: Intent): String {
            return requireNotNull(intent.getStringExtra(EXTRA_ARTIST_ID)) {
                "Artist detail requires artist id"
            }
        }
    }
}
