package com.wxy.playerlite.player.source

interface IPlaysource {
    companion object {
        const val SEEK_SET = 0
        const val SEEK_CUR = 1
        const val SEEK_END = 2
        const val SEEK_SIZE = 0x10000
    }

    enum class AudioSourceCode(val code: Int) {
        ASC_BUSY(-2),
        ASC_EOF(-1),
        ASC_SUCCESS(0),
        ASC_PARAM_ERROR(1),
        ASC_OPEN_NOT_EXIST(2),
        ASC_OPEN_NOT_READ_ERROR(3),
        ASC_READ_ERROR(4),
        ASC_IO_EXCEPTION(5),
        ASC_SEEK_ERROR(6),
        ASC_ABORT(7),
        ASC_CLOSE(8)
    }

    enum class SourceMode {
        NORMAL,
        PRELOAD
    }

    val sourceId: String

    fun setSourceMode(mode: SourceMode)

    fun open(): AudioSourceCode

    fun stop()

    fun abort()

    fun close()

    fun size(): Long

    fun cacheSize(): Long

    fun supportFastSeek(): Boolean

    fun read(buffer: ByteArray, size: Int): Int

    fun seek(offset: Long, whence: Int): Long
}
