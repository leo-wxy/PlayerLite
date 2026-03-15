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
                playable = com.wxy.playerlite.playback.model.MusicInfo(
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
                playable = com.wxy.playerlite.playback.model.MusicInfo(
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
        assertTrue(playbackSource.seekCalls.isEmpty())
        assertTrue(metadataProbeSource.closeCalls >= 1)
        assertTrue(metadataProbeSource.seekCalls.isEmpty())
    }

    @Test
    fun prepareNetworkSourceFallsBackWhenMetadataLoadFails() = runBlocking {
        val source = FakePlaySource()
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
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

    @Test
    fun prepareNetworkSourceUsesDurationHintWithoutMetadataProbe() = runBlocking {
        val source = FakePlaySource()
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-4",
                    songId = "1969519579",
                    title = "Hinted",
                    durationMs = 219_893L,
                    playbackUri = ""
                )
            ),
            durationHintMs = 219_893L,
            createSource = { source },
            createMetadataProbeSource = {
                error("metadata probe should be skipped when duration hint is present")
            },
            loadAudioMeta = {
                error("metadata load should be skipped when duration hint is present")
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertEquals(219_893L, ready.mediaMeta.durationMs)
        assertEquals("-", ready.mediaMeta.codec)
        assertTrue(source.seekCalls.isEmpty())
    }

    @Test
    fun prepareNetworkSourceCanValidateActualMetadataEvenWhenDurationHintExists() = runBlocking {
        val source = FakePlaySource()
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-5",
                    songId = "1299550532",
                    title = "倒数",
                    durationMs = 229_333L,
                    playbackUri = ""
                )
            ),
            durationHintMs = 229_333L,
            preferActualMetadataWhenHintPresent = true,
            createSource = { source },
            loadAudioMeta = {
                AudioMetaDisplay(
                    codec = "mp3",
                    sampleRate = "44100 Hz",
                    channels = "2",
                    bitRate = "320 kbps",
                    durationMs = 90_000L
                )
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertEquals(90_000L, ready.mediaMeta.durationMs)
        assertEquals("mp3", ready.mediaMeta.codec)
        assertEquals(1, source.seekCalls.size)
    }

    @Test
    fun prepareNetworkSourceRetriesWithFreshSourceWhenInitialRewindFails() = runBlocking {
        val first = FakePlaySource(name = "first", seekResult = -1L)
        val second = FakePlaySource(name = "second")
        val createdSources = ArrayDeque(listOf(first, second))
        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-rewind-retry",
                    title = "Retry",
                    playbackUri = "https://example.com/retry.mp3"
                )
            ),
            createSource = { createdSources.removeFirst() },
            loadAudioMeta = {
                AudioMetaDisplay(
                    codec = "aac",
                    sampleRate = "48000 Hz",
                    channels = "2",
                    bitRate = "192 kbps",
                    durationMs = 87_000L
                )
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertTrue(ready.source === second)
        assertEquals(1, first.closeCalls)
        assertTrue(second.seekCalls.isEmpty())
        assertEquals(87_000L, ready.mediaMeta.durationMs)
    }

    @Test
    fun prepareNetworkSource_shouldReopenFreshPlaybackSourceWhenMetadataReadConsumesOriginalSource() = runBlocking {
        val first = FakePlaySource(
            name = "first",
            seekResultProvider = { source -> if (source.closeCalls > 0) -1L else 0L }
        )
        val second = FakePlaySource(name = "second")
        val createdSources = ArrayDeque(listOf(first, second))
        var metadataLoadCount = 0

        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-reopen-after-metadata",
                    title = "Reopen",
                    playbackUri = "https://example.com/reopen.mp3"
                )
            ),
            createSource = { createdSources.removeFirst() },
            loadAudioMeta = { source ->
                metadataLoadCount += 1
                source.close()
                AudioMetaDisplay(
                    codec = "aac",
                    sampleRate = "48000 Hz",
                    channels = "2",
                    bitRate = "192 kbps",
                    durationMs = 101_000L
                )
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertTrue(ready.source === second)
        assertEquals(1, metadataLoadCount)
        assertTrue(second.seekCalls.isEmpty())
        assertEquals(101_000L, ready.mediaMeta.durationMs)
    }

    @Test
    fun prepareNetworkSourceWithDedicatedMetadataProbe_shouldNotRequirePlaybackSourceRewind() = runBlocking {
        val playbackSource = FakePlaySource(name = "playback", seekResult = -1L)
        val metadataProbeSource = FakePlaySource(name = "probe")

        val result = prepareNetworkSourceInternal(
            item = PlaybackTrack(
                playable = com.wxy.playerlite.playback.model.MusicInfo(
                    id = "net-probe-no-rewind",
                    songId = "1299550532",
                    title = "倒数",
                    durationMs = 229_333L,
                    playbackUri = "https://example.com/retry.mp3"
                )
            ),
            durationHintMs = 229_333L,
            preferActualMetadataWhenHintPresent = true,
            createSource = { playbackSource },
            createMetadataProbeSource = { metadataProbeSource },
            loadAudioMeta = { source ->
                if (source === metadataProbeSource) {
                    AudioMetaDisplay(
                        codec = "aac",
                        sampleRate = "44100 Hz",
                        channels = "2",
                        bitRate = "192 kbps",
                        durationMs = 229_333L
                    )
                } else {
                    error("playback source should not be used for metadata probing")
                }
            }
        )

        assertTrue(result is PreparationResult.Ready)
        val ready = result as PreparationResult.Ready
        assertTrue(ready.source === playbackSource)
        assertTrue(
            "Expected playback source rewind to be skipped when metadata probe is separate",
            playbackSource.seekCalls.isEmpty()
        )
        assertTrue(metadataProbeSource.closeCalls >= 1)
    }

    private class FakePlaySource(
        private val name: String = "fake",
        private val seekResult: Long = 0L,
        private val seekResultProvider: ((FakePlaySource) -> Long)? = null
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
            return seekResultProvider?.invoke(this) ?: seekResult
        }
    }
}
