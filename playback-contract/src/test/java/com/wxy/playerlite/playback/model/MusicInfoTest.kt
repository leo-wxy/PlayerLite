package com.wxy.playerlite.playback.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicInfoTest {
    @Test
    fun toPlayableItem_projectsSemanticMusicFields() {
        val musicInfo = MusicInfo(
            id = "queue-1",
            songId = "1969519579",
            title = "夜曲",
            artistNames = listOf("周杰伦", "杨瑞代"),
            albumTitle = "十一月的萧邦",
            coverUrl = "https://example.com/cover.jpg",
            durationMs = 213_000L,
            playbackUri = "https://example.com/song.mp3",
            playbackContext = PlaybackContext(
                sourceType = "playlist",
                sourceId = "24381616",
                sourceTitle = "深夜单曲循环"
            ),
            previewClip = PlaybackPreviewClip(
                startMs = 15_000L,
                endMs = 75_000L
            ),
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
            requiresAuthorization = true
        )

        val playable = musicInfo.toPlayableItem()

        assertEquals("queue-1", playable.id)
        assertEquals("1969519579", playable.songId)
        assertEquals("夜曲", playable.title)
        assertEquals("周杰伦 / 杨瑞代", playable.artistText)
        assertEquals("十一月的萧邦", playable.albumTitle)
        assertEquals("https://example.com/cover.jpg", playable.coverUrl)
        assertEquals(213_000L, playable.durationMs)
        assertEquals("playlist", playable.playbackContext?.sourceType)
        assertEquals(15_000L, playable.previewClip?.startMs)
        assertTrue(playable.requiresAuthorization)
    }

    @Test
    fun playableItemSnapshot_roundTripsTrackMetadataThroughMediaItem() {
        val playable = PlayableItemSnapshot(
            id = "queue-2",
            songId = "347230",
            title = "晴天",
            artistText = "周杰伦",
            albumTitle = "叶惠美",
            coverUrl = "https://example.com/sunny.jpg",
            durationMs = 269_000L,
            playbackUri = "https://example.com/sunny.mp3",
            playbackContext = PlaybackContext(
                sourceType = "playlist",
                sourceId = "99",
                sourceTitle = "午后练歌房"
            ),
            previewClip = PlaybackPreviewClip(
                startMs = 30_000L,
                endMs = 90_000L
            ),
            requestHeaders = mapOf("X-Test" to "1"),
            requiresAuthorization = true
        )

        val restored = PlayableItemSnapshot.fromMediaItem(
            playable.toMediaItem(statusText = "试听中")
        )

        assertNotNull(restored)
        assertEquals(playable, restored)
    }

    @Test
    fun playableItemSnapshot_fromMediaItemShouldPreferOriginalTrackMetadataWhenDisplayTitleOverridesSessionTitle() {
        val baseMediaItem = PlayableItemSnapshot(
            id = "queue-3",
            songId = "1969519579",
            title = "夜曲",
            artistText = "周杰伦",
            albumTitle = "十一月的萧邦",
            coverUrl = "https://example.com/night.jpg",
            durationMs = 213_000L,
            playbackUri = "https://example.com/night.mp3"
        ).toMediaItem(statusText = "正在播放")
        val mediaItem = baseMediaItem
            .buildUpon()
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("为你弹奏肖邦的夜曲")
                    .setArtist("夜曲 - 周杰伦")
                    .setAlbumTitle("十一月的萧邦")
                    .setArtworkUri(android.net.Uri.parse("https://example.com/night.jpg"))
                    .setExtras(
                        android.os.Bundle(baseMediaItem.mediaMetadata.extras ?: android.os.Bundle()).apply {
                            putString("original_title", "夜曲")
                            putString("original_artist_text", "周杰伦")
                        }
                    )
                    .build()
            )
            .build()

        val restored = PlayableItemSnapshot.fromMediaItem(mediaItem)

        assertNotNull(restored)
        assertEquals("夜曲", restored?.title)
        assertEquals("周杰伦", restored?.artistText)
    }
}
