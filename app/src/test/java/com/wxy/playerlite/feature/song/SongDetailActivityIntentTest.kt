package com.wxy.playerlite.feature.song

import android.content.Context
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SongDetailActivityIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun songDetailActivity_shouldNotUsePlaybackDetailBaseHost() {
        assertEquals(
            ComponentActivity::class.java,
            SongDetailActivity::class.java.superclass
        )
    }

    @Test
    fun createOnlineIntent_shouldRoundTripSongRef() {
        val intent = SongDetailActivity.createOnlineIntent(
            context = context,
            songId = "1973665667"
        )

        assertEquals(
            SongRef.Online(songId = "1973665667"),
            SongDetailActivity.songRefFrom(intent)
        )
    }

    @Test
    fun createLocalIntent_shouldRoundTripSongRef() {
        val intent = SongDetailActivity.createLocalIntent(
            context = context,
            playbackUri = "content://media/external/audio/media/42",
            title = "本地测试歌曲",
            artistText = "本地歌手",
            albumTitle = "本地专辑",
            durationMs = 245000L,
            coverUrl = "content://media/external/audio/albumart/7"
        )

        assertEquals(
            SongRef.Local(
                playbackUri = "content://media/external/audio/media/42",
                title = "本地测试歌曲",
                artistText = "本地歌手",
                albumTitle = "本地专辑",
                durationMs = 245000L,
                coverUrl = "content://media/external/audio/albumart/7"
            ),
            SongDetailActivity.songRefFrom(intent)
        )
    }
}
