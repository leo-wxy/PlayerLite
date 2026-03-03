package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.source.IDirectReadableSource
import com.wxy.playerlite.player.source.IPlaysource
import java.nio.ByteBuffer

internal class CachedNetworkSource(
    private val resourceKey: String,
    private val provider: RangeDataProvider,
    private val sessionConfig: SessionCacheConfig
) : IPlaysource, IDirectReadableSource {
    private var sourceMode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var opened = false
    private var aborted = false
    private var position = 0L
    private var contentLength: Long = -1L
    private var session: CacheSession? = null

    override val sourceId: String
        get() = resourceKey

    override fun setSourceMode(mode: IPlaysource.SourceMode) {
        sourceMode = mode
    }

    override fun open(): IPlaysource.AudioSourceCode {
        synchronized(this) {
            if (aborted) {
                return IPlaysource.AudioSourceCode.ASC_ABORT
            }
            if (opened && session != null) {
                return IPlaysource.AudioSourceCode.ASC_SUCCESS
            }
            val openResult = CacheCore.openSession(
                OpenSessionParams(
                    resourceKey = resourceKey,
                    provider = provider,
                    config = sessionConfig,
                    contentLengthHint = provider.queryContentLength()
                )
            )
            if (openResult.isFailure) {
                return IPlaysource.AudioSourceCode.ASC_IO_EXCEPTION
            }
            session = openResult.getOrNull()
            contentLength = provider.queryContentLength() ?: -1L
            opened = true
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }
    }

    override fun stop() = Unit

    override fun abort() {
        synchronized(this) {
            aborted = true
            close()
        }
    }

    override fun close() {
        synchronized(this) {
            opened = false
            session?.close()
            session = null
            provider.close()
        }
    }

    override fun size(): Long = if (contentLength > 0L) contentLength else 0L

    override fun cacheSize(): Long = 0L

    override fun supportFastSeek(): Boolean = sourceMode == IPlaysource.SourceMode.NORMAL

    override fun read(buffer: ByteArray, size: Int): Int {
        synchronized(this) {
            if (!opened) {
                val openCode = open()
                if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                    return -1
                }
            }
            if (aborted || size <= 0 || buffer.isEmpty()) {
                return 0
            }
            val maxRead = size.coerceAtMost(buffer.size)
            val currentSession = session ?: return -1
            val result = currentSession.readAt(position, maxRead)
            if (result.isFailure) {
                return -1
            }
            val bytes = result.getOrThrow()
            val copySize = bytes.size.coerceAtMost(maxRead)
            if (copySize <= 0) {
                return 0
            }
            bytes.copyInto(buffer, destinationOffset = 0, startIndex = 0, endIndex = copySize)
            position += copySize
            return copySize
        }
    }

    override fun readDirect(buffer: ByteBuffer, size: Int): Int {
        if (size <= 0 || !buffer.hasRemaining()) {
            return 0
        }
        val temp = ByteArray(size.coerceAtMost(buffer.remaining()))
        val read = read(temp, temp.size)
        if (read > 0) {
            buffer.put(temp, 0, read)
        }
        return read
    }

    override fun seek(offset: Long, whence: Int): Long {
        synchronized(this) {
            if (aborted) {
                return -1L
            }
            if ((whence and IPlaysource.SEEK_SIZE) != 0) {
                return size()
            }
            val base = when (whence and 0x3) {
                IPlaysource.SEEK_SET -> 0L
                IPlaysource.SEEK_CUR -> position
                IPlaysource.SEEK_END -> size()
                else -> return -1L
            }
            val target = (base + offset).coerceAtLeast(0L)
            val bounded = if (size() > 0L) target.coerceAtMost(size()) else target
            session?.seek(bounded, IPlaysource.SEEK_SET)
            position = bounded
            return position
        }
    }
}
