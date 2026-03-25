package com.wxy.playerlite.core

import android.content.Context
import com.wxy.playerlite.core.playback.DefaultSongDetailRepository
import com.wxy.playerlite.core.playback.NeteaseSongDetailRemoteDataSource
import com.wxy.playerlite.core.playback.SongDetailRepository
import com.wxy.playerlite.feature.album.AlbumDetailRepository
import com.wxy.playerlite.feature.album.DefaultAlbumDetailRepository
import com.wxy.playerlite.feature.album.NeteaseAlbumDetailRemoteDataSource
import com.wxy.playerlite.feature.artist.ArtistDetailRepository
import com.wxy.playerlite.feature.artist.DefaultArtistDetailRepository
import com.wxy.playerlite.feature.artist.NeteaseArtistDetailRemoteDataSource
import com.wxy.playerlite.feature.main.DefaultHomeDiscoveryRepository
import com.wxy.playerlite.feature.main.HomeDiscoveryRepository
import com.wxy.playerlite.feature.main.NeteaseHomeDiscoveryRemoteDataSource
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

    @Volatile
    private var services: Services? = null

    fun userRepository(context: Context): UserRepository {
        return getServices(context).userRepository
    }

    fun homeDiscoveryRepository(context: Context): HomeDiscoveryRepository {
        return getServices(context).homeDiscoveryRepository
    }

    fun searchRepository(context: Context): SearchRepository {
        return getServices(context).searchRepository
    }

    fun userCenterRepository(context: Context): UserCenterRepository {
        return getServices(context).userCenterRepository
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
        return Services(
            userRepository = DefaultUserRepository(
                storage = storage,
                remoteDataSource = remoteDataSource
            ),
            homeDiscoveryRepository = DefaultHomeDiscoveryRepository(
                remoteDataSource = NeteaseHomeDiscoveryRemoteDataSource(httpClient)
            ),
            searchRepository = SearchFeatureServiceFactory.createRepository(
                httpClient = httpClient,
                historyPreferences = context.getSharedPreferences(
                    SEARCH_HISTORY_PREFS,
                    Context.MODE_PRIVATE
                )
            ),
            userCenterRepository = DefaultUserCenterRepository(
                remoteDataSource = NeteaseUserCenterRemoteDataSource(httpClient)
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
                )
            ),
            albumDetailRepository = DefaultAlbumDetailRepository(
                remoteDataSource = NeteaseAlbumDetailRemoteDataSource(httpClient)
            ),
            songDetailRepository = DefaultSongDetailRepository(
                remoteDataSource = NeteaseSongDetailRemoteDataSource(httpClient)
            ),
            songWikiRepository = DefaultSongWikiRepository(
                remoteDataSource = NeteaseSongWikiRemoteDataSource(httpClient)
            ),
            lyricRepository = DefaultLyricRepository(
                remoteDataSource = NeteaseLyricRemoteDataSource(httpClient),
                localStore = LyricLocalStore(
                    directory = context.filesDir.resolve("lyrics")
                )
            )
        )
    }

    private data class Services(
        val userRepository: UserRepository,
        val homeDiscoveryRepository: HomeDiscoveryRepository,
        val searchRepository: SearchRepository,
        val userCenterRepository: UserCenterRepository,
        val artistDetailRepository: ArtistDetailRepository,
        val playlistDetailRepository: PlaylistDetailRepository,
        val webPlaylistImportRepository: WebPlaylistImportRepository,
        val albumDetailRepository: AlbumDetailRepository,
        val songDetailRepository: SongDetailRepository,
        val songWikiRepository: SongWikiRepository,
        val lyricRepository: LyricRepository
    )
}
