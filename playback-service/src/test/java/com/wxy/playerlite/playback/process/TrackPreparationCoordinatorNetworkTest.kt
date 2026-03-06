package com.wxy.playerlite.playback.process

import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPreparationCoordinatorNetworkTest {
    @Test
    fun prepareNetworkSourceReturnsLoadedDuration() = runBlocking {
        val source = FakePlaySource()
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                music = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-1",
                    title = "Test",
                    playbackUri = "https://example.com/test.mp3"
                )
            ),
            createSource = { source },
            loadAudioMeta = {
                AudioMetaDisplay(
                    codec = "mp3",
                    sampleRate = "44100 Hz",
                    channels = "2",
                    bitRate = "128 kbps",
                    durationMs = 12_345L
                )
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertEquals(12_345L, ready.mediaMeta.durationMs)
        assertEquals(1, source.seekCalls.size)
        assertEquals(0L, source.seekCalls.single().first)
        assertEquals(IPlaysource.SEEK_SET, source.seekCalls.single().second)
    }


    @Test
    fun prepareNetworkSourceUsesDedicatedMetadataProbe() = runBlocking {
        val playbackSource = FakePlaySource(name = "playback")
        val metadataProbeSource = FakePlaySource(name = "probe")
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                music = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-3",
                    title = "Test",
                    playbackUri = "https://example.com/test.mp3"
                )
            ),
            createSource = { playbackSource },
            createMetadataProbeSource = { metadataProbeSource },
            loadAudioMeta = { source ->
                if (source === metadataProbeSource) {
                    source.close()
                }
                AudioMetaDisplay(
                    codec = "aac",
                    sampleRate = "48000 Hz",
                    channels = "2",
                    bitRate = "192 kbps",
                    durationMs = 54_321L
                )
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertTrue(ready.source === playbackSource)
        assertEquals(54_321L, ready.mediaMeta.durationMs)
        assertEquals(0, playbackSource.closeCalls)
        assertEquals(1, playbackSource.seekCalls.size)
        assertTrue(metadataProbeSource.closeCalls >= 1)
        assertTrue(metadataProbeSource.seekCalls.isEmpty())
    }

    @Test
    fun prepareNetworkSourceFallsBackWhenMetadataLoadFails() = runBlocking {
        val source = FakePlaySource()
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                music = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-2",
                    title = "Test",
                    playbackUri = "https://example.com/test.mp3"
                )
            ),
            createSource = { source },
            loadAudioMeta = {
                error("metadata failed")
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertEquals(0L, ready.mediaMeta.durationMs)
        assertEquals("-", ready.mediaMeta.codec)
        assertEquals(1, source.seekCalls.size)
    }

    private class FakePlaySource(
        private val name: String = "fake"
    ) : IPlaysource {
        val seekCalls = mutableListOf<Pair<Long, Int>>()
        var closeCalls: Int = 0

        override val sourceId: String = name

        override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

        override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

        override fun stop() = Unit

        override fun abort() = Unit

        override fun close() {
            closeCalls += 1
        }

        override fun size(): Long = 0L

        override fun cacheSize(): Long = 0L

        override fun supportFastSeek(): Boolean = true

        override fun read(buffer: ByteArray, size: Int): Int = 0

        override fun seek(offset: Long, whence: Int): Long {
            seekCalls += offset to whence
            return offset
        }
    }
}
