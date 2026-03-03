package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

object CacheCore {
    @Volatile
    private var initialized: Boolean = false
    private var config: CacheCoreConfig? = null
    private val nextSessionId: AtomicLong = AtomicLong(1L)
    private val sessions: MutableMap<Long, CacheSessionImpl> = ConcurrentHashMap()

    fun init(config: CacheCoreConfig): Result<Unit> {
        val rootPath = config.cacheRootDirPath.trim()
        if (rootPath.isEmpty()) {
            return Result.failure(IllegalArgumentException("cacheRootDirPath cannot be blank"))
        }
        val rootDir = File(rootPath)
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            return Result.failure(IllegalStateException("failed to create cache root: $rootPath"))
        }
        this.config = config
        initialized = true
        return Result.success(Unit)
    }

    fun shutdown() {
        val snapshot = sessions.values.toList()
        snapshot.forEach { it.closeInternal(removeSession = false) }
        sessions.clear()
        config = null
        initialized = false
    }

    fun isInitialized(): Boolean = initialized

    fun openSession(params: OpenSessionParams): Result<CacheSession> {
        if (!initialized) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (params.resourceKey.isBlank()) {
            return Result.failure(IllegalArgumentException("resourceKey cannot be blank"))
        }
        val storage = ensureStorageFiles(params).getOrElse { error ->
            return Result.failure(error)
        }
        val sessionId = nextSessionId.getAndIncrement()
        val session = CacheSessionImpl(
            sessionId = sessionId,
            resourceKey = params.resourceKey,
            provider = params.provider,
            onClose = { closedSessionId -> sessions.remove(closedSessionId) },
            storage = storage
        )
        sessions[sessionId] = session
        return Result.success(session)
    }

    private fun ensureStorageFiles(params: OpenSessionParams): Result<SessionStorageFiles> {
        val currentConfig = config ?: return Result.failure(IllegalStateException("cache core config missing"))
        val rootDir = File(currentConfig.cacheRootDirPath)
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            return Result.failure(IllegalStateException("failed to create cache root: ${rootDir.absolutePath}"))
        }
        val dataFile = File(rootDir, "${params.resourceKey}.data")
        val configFile = File(rootDir, "${params.resourceKey}_config.json")
        val extraFile = File(rootDir, "${params.resourceKey}_extra.json")

        return runCatching {
            if (!dataFile.exists() && !dataFile.createNewFile()) {
                error("failed to create data file: ${dataFile.absolutePath}")
            }

            if (configFile.exists()) {
                val existing = configFile.readText()
                val hasResourceField = existing.contains("\"resourceKey\"")
                val hasExpectedResourceValue = existing.contains("\"${escapeJson(params.resourceKey)}\"")
                if (!hasResourceField || !hasExpectedResourceValue) {
                    error("config resourceKey mismatch for ${params.resourceKey}")
                }
            } else {
                configFile.writeText(
                    buildConfigJson(
                        resourceKey = params.resourceKey,
                        blockSizeBytes = params.config.blockSizeBytes,
                        contentLength = params.contentLengthHint ?: -1L,
                        durationMs = params.durationMsHint ?: -1L,
                        blockIndexes = emptySet(),
                        lastAccessEpochMs = System.currentTimeMillis()
                    )
                )
            }

            if (!extraFile.exists()) {
                extraFile.writeText("{\n}\n")
            }
            val snapshot = readStorageSnapshot(
                configFile = configFile,
                fallbackBlockSizeBytes = params.config.blockSizeBytes,
                fallbackContentLength = params.contentLengthHint ?: -1L,
                fallbackDurationMs = params.durationMsHint ?: -1L
            )
            SessionStorageFiles(
                dataFile = dataFile,
                configFile = configFile,
                extraFile = extraFile,
                blockSizeBytes = snapshot.blockSizeBytes,
                contentLength = snapshot.contentLength,
                durationMs = snapshot.durationMs,
                cachedBlocks = snapshot.blockIndexes.toMutableSet()
            )
        }
    }

    private fun readStorageSnapshot(
        configFile: File,
        fallbackBlockSizeBytes: Int,
        fallbackContentLength: Long,
        fallbackDurationMs: Long
    ): StorageSnapshot {
        val json = configFile.readText()
        val blockSize = parseLongField(json, "blockSizeBytes")?.toInt()
            ?.takeIf { it > 0 }
            ?: fallbackBlockSizeBytes
        val contentLength = parseLongField(json, "contentLength") ?: fallbackContentLength
        val durationMs = parseLongField(json, "durationMs") ?: fallbackDurationMs
        val blocks = parseLongArrayField(json, "blocks")
            .filter { it >= 0L }
            .toSet()
        return StorageSnapshot(
            blockSizeBytes = blockSize,
            contentLength = contentLength,
            durationMs = durationMs,
            blockIndexes = blocks
        )
    }

    private fun buildConfigJson(
        resourceKey: String,
        blockSizeBytes: Int,
        contentLength: Long,
        durationMs: Long,
        blockIndexes: Set<Long>,
        lastAccessEpochMs: Long
    ): String {
        val blocks = blockIndexes.sorted().joinToString(separator = ", ")
        return buildString {
            append("{\n")
            append("  \"version\": 1,\n")
            append("  \"resourceKey\": \"${escapeJson(resourceKey)}\",\n")
            append("  \"contentLength\": $contentLength,\n")
            append("  \"durationMs\": $durationMs,\n")
            append("  \"blockSizeBytes\": $blockSizeBytes,\n")
            append("  \"blocks\": [$blocks],\n")
            append("  \"completedRanges\": [],\n")
            append("  \"lastAccessEpochMs\": $lastAccessEpochMs\n")
            append("}\n")
        }
    }

    private fun parseLongField(json: String, field: String): Long? {
        val pattern = Pattern.compile("\"$field\"\\s*:\\s*(-?\\d+)")
        val matcher = pattern.matcher(json)
        if (!matcher.find()) {
            return null
        }
        return matcher.group(1)?.toLongOrNull()
    }

    private fun parseLongArrayField(json: String, field: String): List<Long> {
        val pattern = Pattern.compile("\"$field\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
        val matcher = pattern.matcher(json)
        if (!matcher.find()) {
            return emptyList()
        }
        val body = matcher.group(1) ?: return emptyList()
        if (body.isBlank()) {
            return emptyList()
        }
        return body.split(",")
            .mapNotNull { token -> token.trim().toLongOrNull() }
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private class CacheSessionImpl(
        override val sessionId: Long,
        override val resourceKey: String,
        private val provider: RangeDataProvider,
        private val onClose: (Long) -> Unit,
        private val storage: SessionStorageFiles
    ) : CacheSession {
        private val lock = Any()
        private var closed = false
        private var currentOffset: Long = 0L
        private val memoryBlocks: MutableMap<Long, ByteArray> = linkedMapOf()

        override fun read(size: Int): Result<ByteArray> {
            val offset = synchronized(lock) { currentOffset }
            val result = readAt(offset, size)
            result.getOrNull()?.let { bytes ->
                synchronized(lock) {
                    currentOffset = offset + bytes.size
                }
            }
            return result
        }

        override fun readAt(offset: Long, size: Int): Result<ByteArray> {
            if (size <= 0) {
                return Result.success(ByteArray(0))
            }
            if (offset < 0L) {
                return Result.failure(IllegalArgumentException("offset must be >= 0"))
            }
            if (closed) {
                return Result.failure(IllegalStateException("session already closed"))
            }
            return runCatching {
                synchronized(lock) {
                    val blockSize = storage.blockSizeBytes
                    var current = offset
                    var remaining = size
                    val output = ByteArray(size)
                    var written = 0

                    while (remaining > 0) {
                        if (storage.contentLength > 0L && current >= storage.contentLength) {
                            break
                        }
                        val blockIndex = current / blockSize
                        val inBlockOffset = (current % blockSize).toInt()

                        val blockBytes = loadOrFetchBlockLocked(blockIndex) ?: break
                        if (inBlockOffset >= blockBytes.size) {
                            break
                        }
                        val copied = min(remaining, blockBytes.size - inBlockOffset)
                        blockBytes.copyInto(
                            destination = output,
                            destinationOffset = written,
                            startIndex = inBlockOffset,
                            endIndex = inBlockOffset + copied
                        )
                        written += copied
                        remaining -= copied
                        current += copied

                        if (copied <= 0) {
                            break
                        }
                    }
                    if (written == output.size) {
                        output
                    } else {
                        output.copyOf(written)
                    }
                }
            }
        }

        override fun seek(offset: Long, whence: Int): Result<Long> {
            if (closed) {
                return Result.failure(IllegalStateException("session already closed"))
            }
            return runCatching {
                synchronized(lock) {
                    val base = when (whence) {
                        SEEK_SET -> 0L
                        SEEK_CUR -> currentOffset
                        SEEK_END -> {
                            val known = storage.contentLength
                            if (known >= 0L) known else (provider.queryContentLength() ?: 0L)
                        }
                        else -> throw IllegalArgumentException("unsupported whence: $whence")
                    }
                    currentOffset = (base + offset).coerceAtLeast(0L)
                    currentOffset
                }
            }
        }

        override fun cancelPendingRead() {
            if (closed) {
                return
            }
            provider.cancelInFlightRead()
        }

        override fun close() {
            closeInternal(removeSession = true)
        }

        fun closeInternal(removeSession: Boolean) {
            if (closed) {
                return
            }
            closed = true
            synchronized(lock) {
                runCatching { persistConfigLocked() }
                memoryBlocks.clear()
            }
            runCatching { provider.close() }
            if (removeSession) {
                onClose(sessionId)
            }
        }

        private fun loadOrFetchBlockLocked(blockIndex: Long): ByteArray? {
            memoryBlocks[blockIndex]?.let { return it }

            if (storage.cachedBlocks.contains(blockIndex)) {
                val diskBytes = readBlockFromDisk(blockIndex)
                if (diskBytes.isNotEmpty()) {
                    memoryBlocks[blockIndex] = diskBytes
                    return diskBytes
                }
                storage.cachedBlocks.remove(blockIndex)
                persistConfigLocked()
            }

            val blockStart = blockIndex * storage.blockSizeBytes.toLong()
            val fetched = provider.readAt(blockStart, storage.blockSizeBytes)
            if (fetched.isEmpty()) {
                return null
            }
            writeBlockToDisk(blockIndex, fetched)
            storage.cachedBlocks.add(blockIndex)
            memoryBlocks[blockIndex] = fetched
            if (storage.contentLength < 0L) {
                storage.contentLength = provider.queryContentLength() ?: -1L
            }
            persistConfigLocked()
            return fetched
        }

        private fun readBlockFromDisk(blockIndex: Long): ByteArray {
            if (!storage.dataFile.exists()) {
                return ByteArray(0)
            }
            RandomAccessFile(storage.dataFile, "r").use { raf ->
                val blockStart = blockIndex * storage.blockSizeBytes.toLong()
                if (blockStart >= raf.length()) {
                    return ByteArray(0)
                }
                val readable = min(storage.blockSizeBytes.toLong(), raf.length() - blockStart).toInt()
                if (readable <= 0) {
                    return ByteArray(0)
                }
                val buffer = ByteArray(readable)
                raf.seek(blockStart)
                val read = raf.read(buffer, 0, readable)
                if (read <= 0) {
                    return ByteArray(0)
                }
                return if (read == readable) buffer else buffer.copyOf(read)
            }
        }

        private fun writeBlockToDisk(blockIndex: Long, bytes: ByteArray) {
            if (bytes.isEmpty()) {
                return
            }
            RandomAccessFile(storage.dataFile, "rw").use { raf ->
                val blockStart = blockIndex * storage.blockSizeBytes.toLong()
                raf.seek(blockStart)
                raf.write(bytes)
            }
        }

        private fun persistConfigLocked() {
            storage.configFile.writeText(
                buildConfigJson(
                    resourceKey = resourceKey,
                    blockSizeBytes = storage.blockSizeBytes,
                    contentLength = storage.contentLength,
                    durationMs = storage.durationMs,
                    blockIndexes = storage.cachedBlocks,
                    lastAccessEpochMs = System.currentTimeMillis()
                )
            )
        }

        private companion object {
            private const val SEEK_SET = 0
            private const val SEEK_CUR = 1
            private const val SEEK_END = 2
        }
    }

    private data class SessionStorageFiles(
        val dataFile: File,
        val configFile: File,
        val extraFile: File,
        val blockSizeBytes: Int,
        var contentLength: Long,
        var durationMs: Long,
        val cachedBlocks: MutableSet<Long>
    )

    private data class StorageSnapshot(
        val blockSizeBytes: Int,
        val contentLength: Long,
        val durationMs: Long,
        val blockIndexes: Set<Long>
    )
}
