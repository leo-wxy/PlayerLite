package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import android.util.Log
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.playback.process.OnlineCacheMetadata
import com.wxy.playerlite.player.source.IDirectReadableSource
import com.wxy.playerlite.player.source.IPlaysource
import java.nio.ByteBuffer

internal class CachedNetworkSource(
    private val resourceKey: String,
    private val provider: RangeDataProvider,
    private val sessionConfig: SessionCacheConfig,
    private val contentLengthHint: Long? = null,
    private val durationMsHint: Long? = null,
    private val extraMetadata: Map<String, String> = emptyMap()
) : IPlaysource, IDirectReadableSource {
    private var sourceMode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var opened = false
    private var aborted = false
    private var position = 0L
    private var seekSerial = 0L
    private var contentLength: Long = contentLengthHint?.takeIf { it > 0L } ?: -1L
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
            val cachedLengthHint = contentLength.takeIf { it > 0L }
            val openResult = CacheCore.openSession(
                OpenSessionParams(
                    resourceKey = resourceKey,
                    provider = provider,
                    config = sessionConfig,
                    contentLengthHint = cachedLengthHint,
                    durationMsHint = durationMsHint?.takeIf { it > 0L }
                )
            )
            if (openResult.isFailure) {
                safeLogE("openSession failed: key=$resourceKey, error=${openResult.exceptionOrNull()?.message}")
                return IPlaysource.AudioSourceCode.ASC_IO_EXCEPTION
            }
            session = openResult.getOrNull()
            opened = true
            if (extraMetadata.isNotEmpty()) {
                CacheCore.lookup(resourceKey)
                    .getOrNull()
                    ?.let { snapshot: CacheLookupSnapshot ->
                        OnlineCacheMetadata.persist(snapshot.extraFilePath, extraMetadata)
                    }
            }
            safeLogI("open success: key=$resourceKey, cachedLengthHint=$cachedLengthHint")
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }
    }

    override fun stop() = Unit

    override fun abort() {
        synchronized(this) {
            aborted = true
            safeLogI("abort: key=$resourceKey")
            close()
        }
    }

    override fun close() {
        synchronized(this) {
            safeLogI("close: key=$resourceKey")
            opened = false
            session?.close()
            session = null
            provider.close()
        }
    }

    override fun size(): Long {
        return refreshContentLength().coerceAtLeast(0L)
    }

    override fun cacheSize(): Long = 0L

    override fun supportFastSeek(): Boolean = sourceMode == IPlaysource.SourceMode.NORMAL

    override fun read(buffer: ByteArray, size: Int): Int {
        while (true) {
            val currentSession: CacheSession
            val readOffset: Long
            val maxRead: Int
            val readSeekSerial: Long
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
                maxRead = size.coerceAtMost(buffer.size)
                currentSession = session ?: return -1
                readOffset = position
                readSeekSerial = seekSerial
                if (contentLength > 0L && readOffset >= contentLength) {
                    refreshContentLength()
                }
            }
            safeLogI("read begin: key=$resourceKey, offset=$readOffset, request=$maxRead")

            val result = currentSession.readAt(readOffset, maxRead)
            if (result.isFailure) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                safeLogE(
                    "readAt failed: key=$resourceKey, pos=$readOffset, size=$maxRead, error=${result.exceptionOrNull()?.message}"
                )
                return -1
            }

            val bytes = result.getOrThrow()
            val copySize = bytes.size.coerceAtMost(maxRead)
            if (copySize <= 0) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                val resolvedLength = refreshContentLength()
                val beforeKnownEof = resolvedLength <= 0L || readOffset < resolvedLength
                if (beforeKnownEof) {
                    safeLogE(
                        "read empty before eof: key=$resourceKey, offset=$readOffset, request=$maxRead, contentLength=$resolvedLength"
                    )
                    return -1
                }
                safeLogI("read empty: key=$resourceKey, offset=$readOffset, request=$maxRead")
                return 0
            }

            refreshContentLength()

            var nextOffset = readOffset
            val accepted = synchronized(this) {
                if (aborted || session !== currentSession) {
                    false
                } else if (seekSerial != readSeekSerial || position != readOffset) {
                    false
                } else {
                    bytes.copyInto(buffer, destinationOffset = 0, startIndex = 0, endIndex = copySize)
                    position += copySize
                    nextOffset = position
                    true
                }
            }
            if (!accepted) {
                continue
            }

            safeLogI(
                "read success: key=$resourceKey, offset=$readOffset, request=$maxRead, read=$copySize, next=$nextOffset"
            )
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
            val currentSession = session ?: return -1L
            seekSerial += 1
            currentSession.cancelPendingRead()
            val seekResult = currentSession.seek(bounded, IPlaysource.SEEK_SET)
            if (seekResult.isFailure) {
                safeLogE(
                    "seek failed: key=$resourceKey, target=$bounded, error=${seekResult.exceptionOrNull()?.message}"
                )
                return -1L
            }
            val newOffset = seekResult.getOrThrow()
            safeLogI("seek success: key=$resourceKey, from=$position, target=$bounded, actual=$newOffset")
            position = newOffset
            return newOffset
        }
    }

    private companion object {
        private const val TAG = "CachedNetworkSource"
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun safeLogE(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    private fun refreshContentLength(): Long {
        val resolved = provider.queryContentLength()
        if (resolved != null && resolved > 0L && (contentLength <= 0L || resolved > contentLength)) {
            contentLength = resolved
        }
        return contentLength
    }
}
