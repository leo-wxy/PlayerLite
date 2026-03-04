package com.wxy.playerlite.player.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ContentUriSource(
    context: Context,
    private val uri: Uri,
    override val sourceId: String = uri.lastPathSegment?.ifBlank { uri.toString() } ?: uri.toString()
) : IPlaysource, IDirectReadableSource {
    private val appContext = context.applicationContext
    private var mode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var isAborted = false

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var fileInputStream: FileInputStream? = null
    private var fileChannel: FileChannel? = null
    private var inputStream: InputStream? = null

    private var seekSupported = false
    private var knownSizeBytes: Long = -1L
    private var sequentialPositionBytes = 0L
    private var sequentialScratch: ByteArray = ByteArray(16 * 1024)

    override fun setSourceMode(mode: IPlaysource.SourceMode) {
        this.mode = mode
    }

    override fun open(): IPlaysource.AudioSourceCode {
        synchronized(this) {
            if (isAborted) {
                return IPlaysource.AudioSourceCode.ASC_ABORT
            }
            if (fileChannel != null || inputStream != null) {
                return IPlaysource.AudioSourceCode.ASC_SUCCESS
            }
            return tryOpenDirect()
        }
    }

    override fun stop() {
        // No-op for URI source.
    }

    override fun abort() {
        synchronized(this) {
            isAborted = true
            closeLocked()
        }
    }

    override fun close() {
        synchronized(this) {
            closeLocked()
        }
    }

    override fun size(): Long {
        synchronized(this) {
            val channel = fileChannel
            if (channel != null) {
                return runCatching { channel.size() }.getOrElse { knownSizeBytes.takeIf { it >= 0L } ?: 0L }
            }
            if (knownSizeBytes >= 0L) {
                return knownSizeBytes
            }
            return sequentialPositionBytes.coerceAtLeast(0L)
        }
    }

    override fun cacheSize(): Long = size()

    override fun supportFastSeek(): Boolean {
        synchronized(this) {
            return mode == IPlaysource.SourceMode.NORMAL && seekSupported
        }
    }

    override fun read(buffer: ByteArray, size: Int): Int {
        synchronized(this) {
            if (isAborted || size <= 0 || buffer.isEmpty()) {
                return 0
            }
            if (open() != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return 0
            }
            val maxRead = size.coerceAtMost(buffer.size)
            if (maxRead <= 0) {
                return 0
            }

            val channel = fileChannel
            if (channel != null) {
                return try {
                    val read = channel.read(ByteBuffer.wrap(buffer, 0, maxRead))
                    if (read < 0) 0 else read
                } catch (_: IOException) {
                    -1
                }
            }

            val stream = inputStream ?: return 0
            return try {
                val read = stream.read(buffer, 0, maxRead)
                if (read <= 0) {
                    0
                } else {
                    sequentialPositionBytes += read.toLong()
                    read
                }
            } catch (_: IOException) {
                -1
            }
        }
    }

    override fun readDirect(buffer: ByteBuffer, size: Int): Int {
        synchronized(this) {
            if (isAborted || size <= 0) {
                return 0
            }
            if (open() != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return 0
            }
            val maxRead = size.coerceAtMost(buffer.remaining())
            if (maxRead <= 0) {
                return 0
            }

            val channel = fileChannel
            if (channel != null) {
                val originalLimit = buffer.limit()
                return try {
                    if (buffer.remaining() > maxRead) {
                        buffer.limit(buffer.position() + maxRead)
                    }
                    val read = channel.read(buffer)
                    if (read < 0) 0 else read
                } catch (_: IOException) {
                    -1
                } finally {
                    buffer.limit(originalLimit)
                }
            }

            ensureSequentialScratchCapacity(maxRead)
            val stream = inputStream ?: return 0
            return try {
                val read = stream.read(sequentialScratch, 0, maxRead)
                if (read <= 0) {
                    0
                } else {
                    buffer.put(sequentialScratch, 0, read)
                    sequentialPositionBytes += read.toLong()
                    read
                }
            } catch (_: IOException) {
                -1
            }
        }
    }

    override fun seek(offset: Long, whence: Int): Long {
        synchronized(this) {
            if (isAborted) {
                return -1L
            }
            if (open() != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return -1L
            }

            if ((whence and IPlaysource.SEEK_SIZE) != 0) {
                return sizeForSeekQuery()
            }

            if (!supportFastSeek()) {
                return -1L
            }

            val channel = fileChannel ?: return -1L
            return try {
                val base = when (whence and 0x3) {
                    IPlaysource.SEEK_SET -> 0L
                    IPlaysource.SEEK_CUR -> channel.position()
                    IPlaysource.SEEK_END -> channel.size()
                    else -> return -1L
                }
                val target = (base + offset).coerceAtLeast(0L)
                channel.position(target)
                channel.position()
            } catch (_: IOException) {
                -1L
            }
        }
    }

    private fun tryOpenDirect(): IPlaysource.AudioSourceCode {
        try {
            val candidate = appContext.contentResolver.openFileDescriptor(uri, "r")
            if (candidate != null) {
                val stream = FileInputStream(candidate.fileDescriptor)
                val channel = stream.channel
                if (isSeekable(candidate, channel)) {
                    parcelFileDescriptor = candidate
                    fileInputStream = stream
                    fileChannel = channel
                    seekSupported = true
                    knownSizeBytes = runCatching { channel.size() }.getOrElse { candidate.statSize }
                    sequentialPositionBytes = 0L
                    return IPlaysource.AudioSourceCode.ASC_SUCCESS
                }
                runCatching { stream.close() }
                runCatching { candidate.close() }
            }
        } catch (_: SecurityException) {
            // Fall through to stream mode.
        } catch (_: IOException) {
            // Fallback to sequential stream.
        } catch (_: UnsupportedOperationException) {
            // Provider may not expose file descriptors, fallback to stream.
        }

        val stream = try {
            appContext.contentResolver.openInputStream(uri)
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
        if (stream == null) {
            return IPlaysource.AudioSourceCode.ASC_OPEN_NOT_READ_ERROR
        }

        inputStream = if (stream is BufferedInputStream) {
            stream
        } else {
            BufferedInputStream(stream, 64 * 1024)
        }
        seekSupported = false
        knownSizeBytes = queryContentLengthFromUri()
        sequentialPositionBytes = 0L
        return IPlaysource.AudioSourceCode.ASC_SUCCESS
    }

    private fun sizeForSeekQuery(): Long {
        val channel = fileChannel
        if (channel != null) {
            return runCatching { channel.size() }.getOrElse { -1L }
        }
        return if (knownSizeBytes >= 0L) knownSizeBytes else -1L
    }

    private fun queryContentLengthFromUri(): Long {
        if (uri.scheme == "file") {
            val path = uri.path ?: return -1L
            val file = File(path)
            return if (file.exists()) file.length() else -1L
        }
        return runCatching {
            appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L } ?: descriptor.declaredLength
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun closeLocked() {
        runCatching { fileChannel?.close() }
        fileChannel = null
        runCatching { fileInputStream?.close() }
        fileInputStream = null
        runCatching { parcelFileDescriptor?.close() }
        parcelFileDescriptor = null
        runCatching { inputStream?.close() }
        inputStream = null

        seekSupported = false
        knownSizeBytes = -1L
        sequentialPositionBytes = 0L
    }

    private fun isSeekable(
        descriptor: ParcelFileDescriptor,
        channel: FileChannel
    ): Boolean {
        if (descriptor.statSize >= 0L) {
            return true
        }
        return runCatching {
            val current = channel.position()
            channel.position(current)
            true
        }.getOrDefault(false)
    }

    private fun ensureSequentialScratchCapacity(targetSize: Int) {
        if (sequentialScratch.size >= targetSize) {
            return
        }
        var next = sequentialScratch.size
        while (next < targetSize) {
            next = (next * 2).coerceAtMost(256 * 1024)
            if (next < targetSize && next == 256 * 1024) {
                next = targetSize
                break
            }
        }
        sequentialScratch = ByteArray(next)
    }
}
