package com.wxy.playerlite.core

import android.content.Context
import com.wxy.playerlite.core.playback.DefaultSongDetailRepository
import com.wxy.playerlite.core.playback.DefaultSongAudioQualityRepository
import com.wxy.playerlite.core.playback.NeteaseSongAudioQualityRemoteDataSource
import com.wxy.playerlite.core.playback.NeteaseSongDetailRemoteDataSource
import com.wxy.playerlite.core.playback.SongAudioQualityRepository
import com.wxy.playerlite.core.playback.SongDetailRepository
import com.wxy.playerlite.core.recentplayback.LocalRecentPlaybackStore
import com.wxy.playerlite.core.recentplayback.SharedPreferencesLocalRecentPlaybackStore
import com.wxy.playerlite.feature.album.AlbumDetailRepository
import com.wxy.playerlite.feature.album.DefaultAlbumDetailRepository
import com.wxy.playerlite.feature.album.NeteaseAlbumDetailRemoteDataSource
import com.wxy.playerlite.feature.artist.ArtistDetailRepository
import com.wxy.playerlite.feature.artist.DefaultArtistDetailRepository
import com.wxy.playerlite.feature.artist.NeteaseArtistDetailRemoteDataSource
import com.wxy.playerlite.feature.home.HomeFeatureServiceFactory
import com.wxy.playerlite.feature.home.HomeHostDependencies
import com.wxy.playerlite.feature.main.DefaultDailyRecommendedSongsRepository
import com.wxy.playerlite.feature.main.DailyRecommendedSongsRepository
import com.wxy.playerlite.feature.main.NeteaseDailyRecommendedSongsRemoteDataSource
import com.wxy.playerlite.feature.main.DefaultUserCenterRepository
import com.wxy.playerlite.feature.main.NeteaseUserCenterRemoteDataSource
import com.wxy.playerlite.feature.main.UserCenterRepository
import com.wxy.playerlite.feature.playlist.DefaultPlaylistDetailRepository
import com.wxy.playerlite.feature.playlist.NeteasePlaylistDetailRemoteDataSource
import com.wxy.playerlite.feature.playlist.PlaylistDetailRepository
import com.wxy.playerlite.feature.player.DefaultSongWikiRepository
import com.wxy.playerlite.feature.player.DefaultLyricRepository
import com.wxy.playerlite.feature.player.LyricLocalStore
import com.wxy.playerlite.feature.player.LyricRepository
import com.wxy.playerlite.feature.player.NeteaseLyricRemoteDataSource
import com.wxy.playerlite.feature.player.NeteaseSongWikiRemoteDataSource
import com.wxy.playerlite.feature.player.SongWikiRepository
import com.wxy.playerlite.feature.song.DefaultSongFavoriteRepository
import com.wxy.playerlite.feature.song.NeteaseSongFavoriteRemoteDataSource
import com.wxy.playerlite.feature.song.SongFavoriteRepository
import com.wxy.playerlite.feature.search.SearchRepository
import com.wxy.playerlite.feature.search.SearchFeatureServiceFactory
import com.wxy.playerlite.feature.webplaylistimport.DefaultQqMusicPlaylistRemoteDataSource
import com.wxy.playerlite.feature.webplaylistimport.DefaultWebPlaylistImportRepository
import com.wxy.playerlite.feature.webplaylistimport.QQ_MUSIC_API_BASE_URL
import com.wxy.playerlite.feature.webplaylistimport.WebPlaylistImportRepository
import com.wxy.playerlite.feature.webplaylistimport.WebPlaylistImportUrlParser
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.DefaultUserRepository
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.toAuthHeaders
import com.wxy.playerlite.user.remote.NeteaseUserRemoteDataSource
import com.wxy.playerlite.user.storage.SharedPreferencesUserSessionStorage

internal object AppContainer {
    private const val USER_SESSION_PREFS = "user_session"
    private const val SEARCH_HISTORY_PREFS = "search_history"
    private const val LOCAL_RECENT_PLAYBACK_PREFS = "local_recent_playback"

    @Volatile
    private var services: Services? = null

    fun userRepository(context: Context): UserRepository {
        return getServices(context).userRepository
    }

    fun homeHostDependencies(context: Context): HomeHostDependencies {
        return getServices(context).homeHostDependencies
    }

    fun dailyRecommendedSongsRepository(context: Context): DailyRecommendedSongsRepository {
        return getServices(context).dailyRecommendedSongsRepository
    }

    fun searchRepository(context: Context): SearchRepository {
        return getServices(context).searchRepository
    }

    fun userCenterRepository(context: Context): UserCenterRepository {
        return getServices(context).userCenterRepository
    }

    fun localRecentPlaybackStore(context: Context): LocalRecentPlaybackStore {
        return getServices(context).localRecentPlaybackStore
    }

    fun artistDetailRepository(context: Context): ArtistDetailRepository {
        return getServices(context).artistDetailRepository
    }

    fun playlistDetailRepository(context: Context): PlaylistDetailRepository {
        return getServices(context).playlistDetailRepository
    }

    fun albumDetailRepository(context: Context): AlbumDetailRepository {
        return getServices(context).albumDetailRepository
    }

    fun songWikiRepository(context: Context): SongWikiRepository {
        return getServices(context).songWikiRepository
    }

    fun lyricRepository(context: Context): LyricRepository {
        return getServices(context).lyricRepository
    }

    fun songDetailRepository(context: Context): SongDetailRepository {
        return getServices(context).songDetailRepository
    }

    fun songAudioQualityRepository(context: Context): SongAudioQualityRepository {
        return getServices(context).songAudioQualityRepository
    }

    fun songFavoriteRepository(context: Context): SongFavoriteRepository {
        return getServices(context).songFavoriteRepository
    }

    fun webPlaylistImportRepository(context: Context): WebPlaylistImportRepository {
        return getServices(context).webPlaylistImportRepository
    }

    private fun getServices(context: Context): Services {
        val existing = services
        if (existing != null) {
            return existing
        }
        return synchronized(this) {
            services ?: buildServices(context.applicationContext).also {
                services = it
            }
        }
    }

    private fun buildServices(context: Context): Services {
        val preferences = context.getSharedPreferences(USER_SESSION_PREFS, Context.MODE_PRIVATE)
        val storage = SharedPreferencesUserSessionStorage(preferences)
        val localRecentPlaybackStore = SharedPreferencesLocalRecentPlaybackStore(
            preferences = context.getSharedPreferences(
                LOCAL_RECENT_PLAYBACK_PREFS,
                Context.MODE_PRIVATE
            )
        )
        val authHeaderProvider = AuthHeaderProvider {
            storage.read()?.toAuthHeaders() ?: emptyMap()
        }
        val httpClient = JsonHttpClient(
            baseUrl = AppEnvironmentConfig.apiBaseUrl,
            authHeaderProvider = authHeaderProvider
        )
        val qqMusicHttpClient = JsonHttpClient(
            baseUrl = QQ_MUSIC_API_BASE_URL
        )
        val remoteDataSource = NeteaseUserRemoteDataSource(httpClient)
        val playlistDetailRepository = DefaultPlaylistDetailRepository(
            remoteDataSource = NeteasePlaylistDetailRemoteDataSource(httpClient)
        )
        val searchRepository = SearchFeatureServiceFactory.createRepository(
            httpClient = httpClient,
            historyPreferences = context.getSharedPreferences(
                SEARCH_HISTORY_PREFS,
                Context.MODE_PRIVATE
            )
        )
        return Services(
            userRepository = DefaultUserRepository(
                storage = storage,
                remoteDataSource = remoteDataSource
            ),
            homeHostDependencies = HomeHostDependencies(
                repository = HomeFeatureServiceFactory.createRepository(httpClient)
            ),
            dailyRecommendedSongsRepository = DefaultDailyRecommendedSongsRepository(
                remoteDataSource = NeteaseDailyRecommendedSongsRemoteDataSource(httpClient)
            ),
            searchRepository = searchRepository,
            userCenterRepository = DefaultUserCenterRepository(
                remoteDataSource = NeteaseUserCenterRemoteDataSource(httpClient),
                localRecentPlaybackStore = localRecentPlaybackStore
            ),
            artistDetailRepository = DefaultArtistDetailRepository(
                remoteDataSource = NeteaseArtistDetailRemoteDataSource(httpClient)
            ),
            playlistDetailRepository = playlistDetailRepository,
            webPlaylistImportRepository = DefaultWebPlaylistImportRepository(
                urlParser = WebPlaylistImportUrlParser(),
                playlistDetailRepository = playlistDetailRepository,
                qqMusicRemoteDataSource = DefaultQqMusicPlaylistRemoteDataSource(
                    httpClient = qqMusicHttpClient
                ),
                searchRepository = searchRepository
            ),
            albumDetailRepository = DefaultAlbumDetailRepository(
                remoteDataSource = NeteaseAlbumDetailRemoteDataSource(httpClient)
            ),
            songDetailRepository = DefaultSongDetailRepository(
                remoteDataSource = NeteaseSongDetailRemoteDataSource(httpClient)
            ),
            songAudioQualityRepository = DefaultSongAudioQualityRepository(
                remoteDataSource = NeteaseSongAudioQualityRemoteDataSource(httpClient)
            ),
            songFavoriteRepository = DefaultSongFavoriteRepository(
                remoteDataSource = NeteaseSongFavoriteRemoteDataSource(httpClient)
            ),
            songWikiRepository = DefaultSongWikiRepository(
                remoteDataSource = NeteaseSongWikiRemoteDataSource(httpClient)
            ),
            lyricRepository = DefaultLyricRepository(
                remoteDataSource = NeteaseLyricRemoteDataSource(httpClient),
                localStore = LyricLocalStore(
                    directory = context.filesDir.resolve("lyrics")
                )
            ),
            localRecentPlaybackStore = localRecentPlaybackStore
        )
    }

    private data class Services(
        val userRepository: UserRepository,
        val homeHostDependencies: HomeHostDependencies,
        val dailyRecommendedSongsRepository: DailyRecommendedSongsRepository,
        val searchRepository: SearchRepository,
        val userCenterRepository: UserCenterRepository,
        val artistDetailRepository: ArtistDetailRepository,
        val playlistDetailRepository: PlaylistDetailRepository,
        val webPlaylistImportRepository: WebPlaylistImportRepository,
        val albumDetailRepository: AlbumDetailRepository,
        val songDetailRepository: SongDetailRepository,
        val songAudioQualityRepository: SongAudioQualityRepository,
        val songFavoriteRepository: SongFavoriteRepository,
        val songWikiRepository: SongWikiRepository,
        val lyricRepository: LyricRepository,
        val localRecentPlaybackStore: LocalRecentPlaybackStore
    )
}
