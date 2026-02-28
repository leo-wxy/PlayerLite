package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.player.source.IPlaysource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedSourceSessionTest {
    @Test
    fun markPrepared_andRelease_updatesStateAndClosesSource() {
        val session = PreparedSourceSession()
        val source = FakePlaySource()

        session.markPrepared(itemId = "track-1", source = source)

        assertTrue(session.isPreparedFor("track-1"))
        assertEquals(source, session.currentSource())
        assertEquals("track-1", session.preparedItemId())

        session.stopCurrent()
        session.release()

        assertEquals(1, source.stopCount)
        assertEquals(1, source.abortCount)
        assertEquals(1, source.closeCount)
        assertFalse(session.isPreparedFor("track-1"))
        assertNull(session.currentSource())
        assertNull(session.preparedItemId())
    }

    @Test
    fun consumeAutoPlayIfPrepared_onlySucceedsForMatchedPreparedItem() {
        val session = PreparedSourceSession()
        val source = FakePlaySource()

        session.markPrepared(itemId = "track-1", source = source)
        session.setAutoPlayWhenPrepared(true)

        assertFalse(session.consumeAutoPlayIfPrepared("track-2"))
        assertTrue(session.consumeAutoPlayIfPrepared("track-1"))
        assertFalse(session.consumeAutoPlayIfPrepared("track-1"))
    }

    private class FakePlaySource : IPlaysource {
        var stopCount: Int = 0
        var abortCount: Int = 0
        var closeCount: Int = 0

        override val sourceId: String = "fake"

        override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

        override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

        override fun stop() {
            stopCount += 1
        }

        override fun abort() {
            abortCount += 1
        }

        override fun close() {
            closeCount += 1
        }

        override fun size(): Long = 0L

        override fun cacheSize(): Long = 0L

        override fun supportFastSeek(): Boolean = true

        override fun read(buffer: ByteArray, size: Int): Int = 0

        override fun seek(offset: Long, whence: Int): Long = 0L
    }
}
