package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaSourceRepositoryTest {
    @Test
    fun isPlaylistItemReadable_allowsOnlineSongEntriesWithoutLocalUri() {
        val repository = MediaSourceRepository(RuntimeEnvironment.getApplication())
        val item = PlaylistItem(
            id = "queue-online-1",
            uri = "",
            displayName = "夜曲",
            songId = "1973665667",
            itemType = PlaylistItemType.ONLINE
        )

        assertTrue(repository.isPlaylistItemReadable(item))
    }
}
