package com.wxy.playerlite.player.source

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class LocalFileSource(
    private val file: File,
    override val sourceId: String = file.nameWithoutExtension.ifBlank { file.name }
) : IPlaysource, IDirectReadableSource {
    private var mode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var isAborted = false
    private var randomAccessFile: RandomAccessFile? = null

    override fun setSourceMode(mode: IPlaysource.SourceMode) {
        this.mode = mode
    }

    override fun open(): IPlaysource.AudioSourceCode {
        synchronized(this) {
            if (isAborted) {
                return IPlaysource.AudioSourceCode.ASC_ABORT
            }
            if (!file.exists()) {
                return IPlaysource.AudioSourceCode.ASC_OPEN_NOT_EXIST
            }
            if (!file.canRead()) {
                return IPlaysource.AudioSourceCode.ASC_OPEN_NOT_READ_ERROR
            }

            if (randomAccessFile == null) {
                try {
                    randomAccessFile = RandomAccessFile(file, "r")
                } catch (_: IOException) {
                    return IPlaysource.AudioSourceCode.ASC_OPEN_NOT_READ_ERROR
                }
            }
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }
    }

    override fun stop() {
        // No-op for file source.
    }

    override fun abort() {
        synchronized(this) {
            isAborted = true
            try {
                randomAccessFile?.close()
            } catch (_: IOException) {
                // Ignore close error in demo.
            } finally {
                randomAccessFile = null
            }
        }
    }

    override fun close() {
        synchronized(this) {
            try {
                randomAccessFile?.close()
            } catch (_: IOException) {
                // Ignore close error in demo.
            } finally {
                randomAccessFile = null
            }
        }
    }

    override fun size(): Long {
        return if (file.exists()) file.length() else 0L
    }

    override fun cacheSize(): Long {
        return size()
    }

    override fun supportFastSeek(): Boolean {
        return mode == IPlaysource.SourceMode.NORMAL
    }

    override fun read(buffer: ByteArray, size: Int): Int {
        synchronized(this) {
            if (isAborted) {
                return 0
            }
            if (size <= 0 || buffer.isEmpty()) {
                return 0
            }

            val openCode = open()
            if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return 0
            }

            val raf = randomAccessFile ?: return 0
            val maxRead = size.coerceAtMost(buffer.size)
            if (maxRead <= 0) {
                return 0
            }

            return try {
                val read = raf.read(buffer, 0, maxRead)
                if (read < 0) 0 else read
            } catch (_: IOException) {
                -1
            }
        }
    }

    override fun readDirect(buffer: ByteBuffer, size: Int): Int {
        synchronized(this) {
            if (isAborted) {
                return 0
            }
            if (size <= 0) {
                return 0
            }

            val openCode = open()
            if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return 0
            }

            val raf = randomAccessFile ?: return 0
            val maxRead = size.coerceAtMost(buffer.remaining())
            if (maxRead <= 0) {
                return 0
            }

            val originalLimit = buffer.limit()
            return try {
                if (buffer.remaining() > maxRead) {
                    buffer.limit(buffer.position() + maxRead)
                }
                val read = raf.channel.read(buffer)
                if (read < 0) 0 else read
            } catch (_: IOException) {
                -1
            } finally {
                buffer.limit(originalLimit)
            }
        }
    }

    override fun seek(offset: Long, whence: Int): Long {
        synchronized(this) {
            if (isAborted) {
                return -1L
            }

            val openCode = open()
            if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                return -1L
            }

            val raf = randomAccessFile ?: return -1L
            return try {
                if ((whence and IPlaysource.SEEK_SIZE) != 0) {
                    return raf.length()
                }

                val seekBaseType = whence and 0x3
                val base = when (seekBaseType) {
                    IPlaysource.SEEK_SET -> 0L
                    IPlaysource.SEEK_CUR -> raf.filePointer
                    IPlaysource.SEEK_END -> raf.length()
                    else -> return -1L
                }

                val target = (base + offset).coerceAtLeast(0L)
                raf.seek(target)
                raf.filePointer
            } catch (_: IOException) {
                -1L
            }
        }
    }

}
