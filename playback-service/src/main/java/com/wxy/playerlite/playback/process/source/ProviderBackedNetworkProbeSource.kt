package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.player.source.IPlaysource

internal class ProviderBackedNetworkProbeSource(
    private val id: String,
    private val provider: RangeDataProvider
) : IPlaysource {
    private var sourceMode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var opened = false
    private var aborted = false
    private var position = 0L
    private var contentLength = -1L

    override val sourceId: String
        get() = id

    override fun setSourceMode(mode: IPlaysource.SourceMode) {
        sourceMode = mode
    }

    override fun open(): IPlaysource.AudioSourceCode {
        synchronized(this) {
            if (aborted) {
                return IPlaysource.AudioSourceCode.ASC_ABORT
            }
            opened = true
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }
    }

    override fun stop() = Unit

    override fun abort() {
        synchronized(this) {
            aborted = true
            provider.cancelInFlightRead()
            close()
        }
    }

    override fun close() {
        synchronized(this) {
            opened = false
            provider.cancelInFlightRead()
            provider.close()
        }
    }

    override fun size(): Long {
        if (contentLength > 0L) {
            return contentLength
        }
        val resolved = provider.queryContentLength()
        if (resolved != null && resolved > 0L) {
            contentLength = resolved
            return resolved
        }
        return 0L
    }

    override fun cacheSize(): Long = 0L

    override fun supportFastSeek(): Boolean = sourceMode == IPlaysource.SourceMode.NORMAL

    override fun read(buffer: ByteArray, size: Int): Int {
        synchronized(this) {
            if (!opened) {
                if (open() != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                    return -1
                }
            }
            if (aborted || size <= 0 || buffer.isEmpty()) {
                return 0
            }
            val maxRead = size.coerceAtMost(buffer.size)
            val bytes = provider.readAtBytes(position, maxRead)
            if (bytes.isEmpty()) {
                return 0
            }
            val copySize = bytes.size.coerceAtMost(maxRead)
            bytes.copyInto(buffer, endIndex = copySize)
            position += copySize
            return copySize
        }
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
            position = if (size() > 0L) target.coerceAtMost(size()) else target
            return position
        }
    }
}
