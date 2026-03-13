package com.wxy.playerlite.feature.search

import android.app.Application
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.playlist.PlaylistDetailActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchRouteNavigationTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun searchRouteIntent_shouldCreateArtistDetailIntent() {
        val intent = searchRouteIntent(
            context = context,
            target = SearchRouteTarget.Artist(artistId = "135")
        )

        requireNotNull(intent)
        assertEquals(ArtistDetailActivity::class.java.name, intent.component?.className)
        assertEquals("135", ArtistDetailActivity.artistIdFrom(intent))
    }

    @Test
    fun searchRouteIntent_shouldCreatePlaylistDetailIntent() {
        val intent = searchRouteIntent(
            context = context,
            target = SearchRouteTarget.Playlist(playlistId = "3778678")
        )

        requireNotNull(intent)
        assertEquals(PlaylistDetailActivity::class.java.name, intent.component?.className)
        assertEquals("3778678", PlaylistDetailActivity.playlistIdFrom(intent))
    }

    @Test
    fun searchRouteIntent_shouldCreateAlbumDetailIntent() {
        val intent = searchRouteIntent(
            context = context,
            target = SearchRouteTarget.Album(albumId = "32311")
        )

        requireNotNull(intent)
        assertEquals(
            "com.wxy.playerlite.feature.album.AlbumDetailActivity",
            intent.component?.className
        )
        assertEquals("32311", intent.getStringExtra("album_id"))
    }

    @Test
    fun searchRouteIntent_shouldReturnNullForUnsupportedTargets() {
        val songIntent = searchRouteIntent(
            context = context,
            target = SearchRouteTarget.Song(songId = "1")
        )
        val genericIntent = searchRouteIntent(
            context = context,
            target = SearchRouteTarget.Generic(
                resultType = SearchResultType.USER,
                targetId = "3"
            )
        )

        assertNull(songIntent)
        assertNull(genericIntent)
    }
}
