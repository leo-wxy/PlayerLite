package com.wxy.playerlite.feature.song

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.R
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.feature.player.PlayerActivity
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.playlist.PlaylistDetailActivity
import com.wxy.playerlite.feature.player.SongWikiRepository
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

private const val EXTRA_SOURCE = "song_detail_source"
private const val EXTRA_ONLINE_SONG_ID = "song_detail_online_song_id"
private const val EXTRA_LOCAL_URI = "song_detail_local_uri"
private const val EXTRA_LOCAL_TITLE = "song_detail_local_title"
private const val EXTRA_LOCAL_ARTIST = "song_detail_local_artist"
private const val EXTRA_LOCAL_ALBUM = "song_detail_local_album"
private const val EXTRA_LOCAL_DURATION = "song_detail_local_duration"
private const val EXTRA_LOCAL_COVER = "song_detail_local_cover"
private const val SOURCE_ONLINE = "online"
private const val SOURCE_LOCAL = "local"

class SongDetailActivity : ComponentActivity() {
    private val viewModel: SongDetailViewModel by viewModels {
        SongDetailViewModel.factory(
            ref = songRefFrom(intent),
            repository = AppSongDetailFeatureRepository(
                songDetailRepository = AppContainer.songDetailRepository(this),
                songWikiRepository = AppContainer.songWikiRepository(this),
                favoriteRepository = AppContainer.songFavoriteRepository(this)
            ),
            actionGateway = AppSongDetailActionGateway(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(
                R.anim.song_detail_enter_from_bottom,
                R.anim.song_detail_exit_to_top
            )
        }
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = false
                }
                BackHandler(onBack = ::finish)
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            SongDetailEvent.OpenPlayer -> openPlayerAfterQueueReplacement()
                            SongDetailEvent.OpenLandscapePlayer -> {
                                AppPlaybackGraph.runtime(this@SongDetailActivity)
                                    .setOrientationMode(PlayerOrientationMode.LANDSCAPE_LOCKED)
                                startActivity(
                                    PlayerActivity.createIntent(
                                        context = this@SongDetailActivity
                                    )
                                )
                            }
                            is SongDetailEvent.OpenAlbum -> {
                                startActivity(
                                    AlbumDetailActivity.createIntent(
                                        context = this@SongDetailActivity,
                                        albumId = event.albumId
                                    )
                                )
                            }

                            is SongDetailEvent.OpenArtist -> {
                                startActivity(
                                    ArtistDetailActivity.createIntent(
                                        context = this@SongDetailActivity,
                                        artistId = event.artistId
                                    )
                                )
                            }

                            is SongDetailEvent.OpenSong -> {
                                startActivity(
                                    createOnlineIntent(
                                        context = this@SongDetailActivity,
                                        songId = event.songId
                                    )
                                )
                            }

                            is SongDetailEvent.OpenPlaylist -> {
                                startActivity(
                                    PlaylistDetailActivity.createIntent(
                                        context = this@SongDetailActivity,
                                        playlistId = event.playlistId
                                    )
                                )
                            }

                            is SongDetailEvent.Share -> {
                                startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, event.text),
                                        "分享歌曲"
                                    )
                                )
                            }

                            is SongDetailEvent.ShowMessage -> {
                                Toast.makeText(
                                    this@SongDetailActivity,
                                    event.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                SongDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::retry,
                    onPlayClick = viewModel::play,
                    onPlayNextClick = viewModel::playNext,
                    onOpenLandscapeClick = viewModel::openLandscapePlayer,
                    onOpenArtistClick = viewModel::openArtist,
                    onOpenAlbumClick = viewModel::openAlbum,
                    onOpenSongClick = viewModel::openSong,
                    onOpenPlaylistClick = viewModel::openPlaylist,
                    bottomOverlayPadding = 0.dp,
                )
            }
        }
    }

    private fun openPlayerAfterQueueReplacement() {
        startActivity(
            PlayerActivity.createIntent(
                context = this,
                startPlayback = true
            )
        )
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(
            R.anim.song_detail_enter_from_top,
            R.anim.song_detail_exit_to_bottom
        )
    }

    companion object {
        fun createIntent(context: Context, ref: SongRef): Intent {
            return when (ref) {
                is SongRef.Online -> createOnlineIntent(context, ref.songId)
                is SongRef.Local -> createLocalIntent(
                    context = context,
                    playbackUri = ref.playbackUri,
                    title = ref.title,
                    artistText = ref.artistText,
                    albumTitle = ref.albumTitle,
                    durationMs = ref.durationMs,
                    coverUrl = ref.coverUrl
                )
            }
        }

        fun createOnlineIntent(context: Context, songId: String): Intent {
            return Intent(context, SongDetailActivity::class.java)
                .putExtra(EXTRA_SOURCE, SOURCE_ONLINE)
                .putExtra(EXTRA_ONLINE_SONG_ID, songId)
        }

        fun createLocalIntent(
            context: Context,
            playbackUri: String,
            title: String,
            artistText: String,
            albumTitle: String?,
            durationMs: Long,
            coverUrl: String? = null
        ): Intent {
            return Intent(context, SongDetailActivity::class.java)
                .putExtra(EXTRA_SOURCE, SOURCE_LOCAL)
                .putExtra(EXTRA_LOCAL_URI, playbackUri)
                .putExtra(EXTRA_LOCAL_TITLE, title)
                .putExtra(EXTRA_LOCAL_ARTIST, artistText)
                .putExtra(EXTRA_LOCAL_ALBUM, albumTitle)
                .putExtra(EXTRA_LOCAL_DURATION, durationMs)
                .putExtra(EXTRA_LOCAL_COVER, coverUrl)
        }

        fun songRefFrom(intent: Intent): SongRef {
            return when (intent.getStringExtra(EXTRA_SOURCE)) {
                SOURCE_ONLINE -> SongRef.Online(
                    songId = requireNotNull(intent.getStringExtra(EXTRA_ONLINE_SONG_ID)) {
                        "Song detail requires online song id"
                    }
                )

                SOURCE_LOCAL -> SongRef.Local(
                    playbackUri = requireNotNull(intent.getStringExtra(EXTRA_LOCAL_URI)) {
                        "Song detail requires local playback uri"
                    },
                    title = intent.getStringExtra(EXTRA_LOCAL_TITLE).orEmpty(),
                    artistText = intent.getStringExtra(EXTRA_LOCAL_ARTIST).orEmpty(),
                    albumTitle = intent.getStringExtra(EXTRA_LOCAL_ALBUM),
                    durationMs = intent.getLongExtra(EXTRA_LOCAL_DURATION, 0L),
                    coverUrl = intent.getStringExtra(EXTRA_LOCAL_COVER)
                )

                else -> error("Song detail requires valid source")
            }
        }
    }
}

private class AppSongDetailActionGateway(
    context: Context
) : SongDetailActionGateway {
    private val playbackGateway = AppPlaybackGraph.detailPlaybackGateway(context)
    private val runtime = AppPlaybackGraph.runtime(context)

    override fun play(item: com.wxy.playerlite.core.playlist.PlaylistItem): Boolean {
        return playbackGateway.play(
            com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest(
                items = listOf(item),
                activeIndex = 0
            )
        )
    }

    override fun playNext(item: com.wxy.playerlite.core.playlist.PlaylistItem): Boolean {
        return runtime.insertPlaylistItemNext(item)
    }
}

private class AppSongDetailFeatureRepository(
    private val songDetailRepository: com.wxy.playerlite.core.playback.SongDetailRepository,
    private val songWikiRepository: SongWikiRepository,
    private val favoriteRepository: SongFavoriteRepository
) : SongDetailFeatureRepository {
    override suspend fun loadSongDetail(ref: SongRef): SongDetailContent {
        return when (ref) {
            is SongRef.Local -> {
                SongDetailContent(
                    ref = ref,
                    source = SongDetailSource.LOCAL,
                    title = ref.title,
                    artistText = ref.artistText,
                    albumTitle = ref.albumTitle,
                    coverUrl = ref.coverUrl,
                    durationMs = ref.durationMs,
                    playlistItem = com.wxy.playerlite.core.playlist.PlaylistItem(
                        id = ref.playbackUri,
                        uri = ref.playbackUri,
                        displayName = ref.title,
                        title = ref.title,
                        artistText = ref.artistText,
                        albumTitle = ref.albumTitle,
                        coverUrl = ref.coverUrl,
                        durationMs = ref.durationMs,
                        itemType = com.wxy.playerlite.core.playlist.PlaylistItemType.LOCAL,
                        contextType = "local-song",
                        contextId = ref.playbackUri,
                        contextTitle = ref.title
                    )
                )
            }

            is SongRef.Online -> {
                val song = songDetailRepository.fetchSongs(listOf(ref.songId)).firstOrNull()
                    ?: error("歌曲详情不存在")
                val wiki = runCatching {
                    songWikiRepository.fetchSongWiki(ref.songId)
                }.getOrNull()
                SongDetailContent(
                    ref = ref,
                    source = SongDetailSource.ONLINE,
                    title = song.title,
                    artistText = song.artistText.orEmpty(),
                    primaryArtistId = song.artistIds.firstOrNull(),
                    albumTitle = song.albumTitle,
                    albumId = song.albumId,
                    coverUrl = song.coverUrl,
                    durationMs = song.durationMs,
                    playlistItem = com.wxy.playerlite.core.playlist.PlaylistItem(
                        id = "song-detail:${song.songId ?: song.id}",
                        displayName = song.title,
                        songId = song.songId,
                        title = song.title,
                        artistText = song.artistText,
                        primaryArtistId = song.artistIds.firstOrNull(),
                        albumTitle = song.albumTitle,
                        coverUrl = song.coverUrl,
                        durationMs = song.durationMs,
                        itemType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                        contextType = "song",
                        contextId = song.songId ?: song.id,
                        contextTitle = song.title
                    ),
                    wiki = wiki?.let {
                        SongDetailWikiUi(
                            title = it.title,
                            contributionText = it.contributionText,
                            sections = it.sections.map { section ->
                                SongDetailWikiSectionUi(
                                    title = section.title,
                                    values = section.values
                                )
                            },
                            similarSongs = it.similarSongs.map { song ->
                                SongDetailRelatedSongUi(
                                    songId = song.songId,
                                    title = song.title,
                                    subtitle = song.subtitle,
                                    coverUrl = song.coverUrl
                                )
                            },
                            relatedPlaylists = it.relatedPlaylists.map { playlist ->
                                SongDetailRelatedPlaylistUi(
                                    playlistId = playlist.playlistId,
                                    title = playlist.title,
                                    subtitle = playlist.subtitle,
                                    coverUrl = playlist.coverUrl
                                )
                            }
                        )
                    },
                    canFavorite = true
                )
            }
        }
    }

    override suspend fun favoriteSong(songId: String): Result<Unit> {
        return favoriteRepository.favoriteSong(songId)
    }
}
