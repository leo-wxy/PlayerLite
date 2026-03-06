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

internal class JvmFakeCacheCoreEngine : CacheCoreEngine {
    @Volatile
    private var initialized: Boolean = false
    private var config: CacheCoreConfig? = null
    private val nextSessionId: AtomicLong = AtomicLong(1L)
    private val sessions: MutableMap<Long, CacheSessionImpl> = ConcurrentHashMap()
    private val DATA_FILE_SUFFIX = ".data"
    private val CONFIG_FILE_SUFFIX = "_config.json"
    private val EXTRA_FILE_SUFFIX = "_extra.json"
    private val DEFAULT_BLOCK_SIZE_BYTES = 64 * 1024

    override fun init(config: CacheCoreConfig): Result<Unit> {
        val rootPath = config.cacheRootDirPath.trim()
        if (rootPath.isEmpty()) {
            return Result.failure(IllegalArgumentException("cacheRootDirPath cannot be blank"))
        }
        if (config.memoryCacheCapBytes <= 0L) {
            return Result.failure(IllegalArgumentException("memoryCacheCapBytes must be > 0"))
        }
        if (config.diskCacheMaxBytes <= 0L) {
            return Result.failure(IllegalArgumentException("diskCacheMaxBytes must be > 0"))
        }
        if (config.diskCacheCleanRangeMin <= 0.0 || config.diskCacheCleanRangeMin > 1.0) {
            return Result.failure(IllegalArgumentException("diskCacheCleanRangeMin must be in (0, 1]"))
        }
        if (config.diskCacheCleanRangeMax <= 0.0 || config.diskCacheCleanRangeMax > 1.0) {
            return Result.failure(IllegalArgumentException("diskCacheCleanRangeMax must be in (0, 1]"))
        }
        if (config.diskCacheCleanRangeMin > config.diskCacheCleanRangeMax) {
            return Result.failure(
                IllegalArgumentException("diskCacheCleanRangeMin cannot be greater than diskCacheCleanRangeMax")
            )
        }
        if (config.readRetryCount < 0) {
            return Result.failure(IllegalArgumentException("readRetryCount must be >= 0"))
        }
        val rootDir = File(rootPath)
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            return Result.failure(IllegalStateException("failed to create cache root: $rootPath"))
        }
        this.config = config
        initialized = true
        return Result.success(Unit)
    }

    override fun shutdown() {
        val snapshot = sessions.values.toList()
        snapshot.forEach { it.closeInternal(removeSession = false) }
        sessions.clear()
        config = null
        initialized = false
    }

    override fun isInitialized(): Boolean = initialized

    override fun clearAll(): Result<Unit> {
        if (!initialized) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        return runCatching {
            closeAllSessions()

            val rootDir = requireRootDir()
            rootDir.listFiles()?.forEach { entry ->
                if (!entry.deleteRecursively()) {
                    error("failed to delete cache entry: ${entry.absolutePath}")
                }
            }
            if (!rootDir.exists() && !rootDir.mkdirs()) {
                error("failed to recreate cache root: ${rootDir.absolutePath}")
            }
        }
    }

    override fun lookup(resourceKey: String): Result<CacheLookupSnapshot?> {
        if (!initialized) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (resourceKey.isBlank()) {
            return Result.failure(IllegalArgumentException("resourceKey cannot be blank"))
        }
        return runCatching {
            lookupInternal(
                rootDir = requireRootDir(),
                resourceKey = resourceKey
            )
        }
    }

    override fun lookupByPrefix(
        prefix: String,
        limit: Int
    ): Result<List<CacheLookupSnapshot>> {
        if (!initialized) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (limit <= 0) {
            return Result.success(emptyList())
        }
        return runCatching {
            val rootDir = requireRootDir()
            collectResourceKeys(rootDir)
                .asSequence()
                .filter { key -> key.startsWith(prefix) }
                .sorted()
                .take(limit)
                .mapNotNull { key -> lookupInternal(rootDir = rootDir, resourceKey = key) }
                .toList()
        }
    }

    override fun openSession(params: OpenSessionParams): Result<CacheSession> {
        if (!initialized) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (params.resourceKey.isBlank()) {
            return Result.failure(IllegalArgumentException("resourceKey cannot be blank"))
        }
        runCatching { cleanupDiskIfNeeded() }
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
        val rootDir = requireRootDirOrNull()
            ?: return Result.failure(IllegalStateException("cache core config missing"))
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
                cachedBlocks = snapshot.blockIndexes.toMutableSet(),
                completedRanges = snapshot.completedRanges.toMutableList()
            )
        }
    }

    private fun closeAllSessions() {
        val snapshot = sessions.values.toList()
        snapshot.forEach { session ->
            session.closeInternal(removeSession = false)
        }
        sessions.clear()
    }

    private fun requireRootDirOrNull(): File? {
        val currentConfig = config ?: return null
        val rootDir = File(currentConfig.cacheRootDirPath)
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            return null
        }
        return rootDir
    }

    private fun requireRootDir(): File {
        return requireRootDirOrNull() ?: error("failed to resolve cache root")
    }

    private fun cleanupDiskIfNeeded() {
        val currentConfig = config ?: return
        val rootDir = requireRootDirOrNull() ?: return
        val files = rootDir.listFiles().orEmpty().filter { it.isFile }
        if (files.isEmpty()) {
            return
        }

        data class Entry(
            val resourceKey: String,
            val files: MutableList<File> = mutableListOf(),
            var totalBytes: Long = 0L,
            var lastAccessEpochMs: Long = -1L
        )

        val grouped = linkedMapOf<String, Entry>()
        files.forEach { file ->
            val key = parseResourceKeyFromFileName(file.name) ?: return@forEach
            val entry = grouped.getOrPut(key) { Entry(resourceKey = key) }
            entry.files += file
            entry.totalBytes += file.length()
            if (file.name.endsWith(CONFIG_FILE_SUFFIX)) {
                entry.lastAccessEpochMs = maxOf(entry.lastAccessEpochMs, parseLastAccessEpochMs(file))
            }
        }

        var totalBytes = grouped.values.sumOf { it.totalBytes }
        val trigger = (currentConfig.diskCacheMaxBytes * currentConfig.diskCacheCleanRangeMax).toLong()
        if (totalBytes <= trigger) {
            return
        }

        val target = (currentConfig.diskCacheMaxBytes * currentConfig.diskCacheCleanRangeMin).toLong()
        val usingKeys = sessions.values.map { it.resourceKey }.toSet()
        val candidates = grouped.values
            .sortedWith(compareBy<Entry> { it.lastAccessEpochMs }.thenBy { it.resourceKey })

        candidates.forEach { entry ->
            if (totalBytes <= target) {
                return@forEach
            }
            if (entry.resourceKey in usingKeys) {
                return@forEach
            }
            entry.files.forEach { file -> file.deleteRecursively() }
            totalBytes -= entry.totalBytes
        }
    }

    private fun lookupInternal(
        rootDir: File,
        resourceKey: String
    ): CacheLookupSnapshot? {
        val dataFile = File(rootDir, "$resourceKey$DATA_FILE_SUFFIX")
        val configFile = File(rootDir, "$resourceKey$CONFIG_FILE_SUFFIX")
        val extraFile = File(rootDir, "$resourceKey$EXTRA_FILE_SUFFIX")
        if (!dataFile.exists() && !configFile.exists() && !extraFile.exists()) {
            return null
        }

        val storage = if (configFile.exists()) {
            readStorageSnapshot(
                configFile = configFile,
                fallbackBlockSizeBytes = DEFAULT_BLOCK_SIZE_BYTES,
                fallbackContentLength = -1L,
                fallbackDurationMs = -1L
            )
        } else {
            StorageSnapshot(
                blockSizeBytes = DEFAULT_BLOCK_SIZE_BYTES,
                contentLength = -1L,
                durationMs = -1L,
                blockIndexes = emptySet(),
                completedRanges = emptyList(),
                lastAccessEpochMs = -1L
            )
        }

        return CacheLookupSnapshot(
            resourceKey = resourceKey,
            dataFilePath = dataFile.absolutePath,
            configFilePath = configFile.absolutePath,
            extraFilePath = extraFile.absolutePath,
            dataFileSizeBytes = if (dataFile.exists()) dataFile.length() else 0L,
            blockSizeBytes = storage.blockSizeBytes,
            contentLength = storage.contentLength,
            durationMs = storage.durationMs,
            cachedBlocks = storage.blockIndexes,
            lastAccessEpochMs = storage.lastAccessEpochMs
        )
    }

    private fun collectResourceKeys(rootDir: File): Set<String> {
        val files = rootDir.listFiles() ?: return emptySet()
        return buildSet {
            files.forEach { file ->
                parseResourceKeyFromFileName(file.name)?.let { key ->
                    if (key.isNotBlank()) {
                        add(key)
                    }
                }
            }
        }
    }

    private fun parseResourceKeyFromFileName(fileName: String): String? {
        return when {
            fileName.endsWith(CONFIG_FILE_SUFFIX) -> fileName.removeSuffix(CONFIG_FILE_SUFFIX)
            fileName.endsWith(EXTRA_FILE_SUFFIX) -> fileName.removeSuffix(EXTRA_FILE_SUFFIX)
            fileName.endsWith(DATA_FILE_SUFFIX) -> fileName.removeSuffix(DATA_FILE_SUFFIX)
            else -> null
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
        val completedRanges = parseCompletedRanges(json)
            .ifEmpty { buildRangesFromBlocks(blocks, blockSize) }
        val normalizedBlocks = if (blocks.isNotEmpty()) {
            blocks
        } else {
            buildBlocksFromRanges(completedRanges, blockSize)
        }
        val lastAccessEpochMs = parseLongField(json, "lastAccessEpochMs") ?: -1L
        return StorageSnapshot(
            blockSizeBytes = blockSize,
            contentLength = contentLength,
            durationMs = durationMs,
            blockIndexes = normalizedBlocks,
            completedRanges = completedRanges,
            lastAccessEpochMs = lastAccessEpochMs
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
        val completedRanges = buildCompletedRangesJson(buildRangesFromBlocks(blockIndexes, blockSizeBytes))
        return buildString {
            append("{\n")
            append("  \"version\": 1,\n")
            append("  \"resourceKey\": \"${escapeJson(resourceKey)}\",\n")
            append("  \"contentLength\": $contentLength,\n")
            append("  \"durationMs\": $durationMs,\n")
            append("  \"blockSizeBytes\": $blockSizeBytes,\n")
            append("  \"blocks\": [$blocks],\n")
            append("  \"completedRanges\": $completedRanges,\n")
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

    private fun parseCompletedRanges(json: String): List<LongRange> {
        val bodyPattern = Pattern.compile("\"completedRanges\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
        val bodyMatcher = bodyPattern.matcher(json)
        if (!bodyMatcher.find()) {
            return emptyList()
        }
        val body = bodyMatcher.group(1) ?: return emptyList()
        val itemPattern =
            Pattern.compile("\\{\\s*\"start\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"end\"\\s*:\\s*(-?\\d+)\\s*}")
        val itemMatcher = itemPattern.matcher(body)
        val ranges = mutableListOf<LongRange>()
        while (itemMatcher.find()) {
            val start = itemMatcher.group(1)?.toLongOrNull() ?: continue
            val end = itemMatcher.group(2)?.toLongOrNull() ?: continue
            if (start < 0L || end <= start) {
                continue
            }
            ranges += start until end
        }
        return mergeRanges(ranges)
    }

    private fun buildRangesFromBlocks(
        blocks: Set<Long>,
        blockSizeBytes: Int
    ): List<LongRange> {
        if (blockSizeBytes <= 0 || blocks.isEmpty()) {
            return emptyList()
        }
        val sorted = blocks.filter { it >= 0L }.sorted()
        if (sorted.isEmpty()) {
            return emptyList()
        }

        val ranges = mutableListOf<LongRange>()
        var start = sorted.first()
        var prev = start
        sorted.drop(1).forEach { value ->
            if (value == prev + 1L) {
                prev = value
                return@forEach
            }
            ranges += (start * blockSizeBytes) until ((prev + 1L) * blockSizeBytes)
            start = value
            prev = value
        }
        ranges += (start * blockSizeBytes) until ((prev + 1L) * blockSizeBytes)
        return ranges
    }

    private fun mergeRanges(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) {
            return emptyList()
        }
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        var currentStart = sorted.first().first
        var currentEndExclusive = sorted.first().last + 1L
        sorted.drop(1).forEach { range ->
            val start = range.first
            val endExclusive = range.last + 1L
            if (start > currentEndExclusive) {
                merged += currentStart until currentEndExclusive
                currentStart = start
                currentEndExclusive = endExclusive
            } else {
                currentEndExclusive = maxOf(currentEndExclusive, endExclusive)
            }
        }
        merged += currentStart until currentEndExclusive
        return merged
    }

    private fun buildCompletedRangesJson(ranges: List<LongRange>): String {
        if (ranges.isEmpty()) {
            return "[]"
        }
        return ranges.joinToString(prefix = "[", postfix = "]", separator = ", ") { range ->
            val endExclusive = range.last + 1L
            "{\"start\": ${range.first}, \"end\": $endExclusive}"
        }
    }

    private fun buildBlocksFromRanges(
        ranges: List<LongRange>,
        blockSizeBytes: Int
    ): Set<Long> {
        if (blockSizeBytes <= 0 || ranges.isEmpty()) {
            return emptySet()
        }
        val blocks = linkedSetOf<Long>()
        ranges.forEach { range ->
            val startBlock = range.first / blockSizeBytes
            val endBlock = range.last / blockSizeBytes
            for (block in startBlock..endBlock) {
                if (block >= 0L) {
                    blocks += block
                }
            }
        }
        return blocks
    }

    private fun parseLastAccessEpochMs(configFile: File): Long {
        val json = runCatching { configFile.readText() }.getOrElse { return -1L }
        return parseLongField(json, "lastAccessEpochMs") ?: -1L
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private inner class CacheSessionImpl(
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
                provider.cancelInFlightRead()
                synchronized(lock) {
                    val base = when (whence) {
                        0 -> 0L
                        1 -> currentOffset
                        2 -> {
                            val known = storage.contentLength
                            if (known >= 0L) known else (provider.queryContentLength() ?: 0L)
                        }
                        else -> throw IllegalArgumentException("unsupported whence: $whence")
                    }
                    currentOffset = (base + offset).coerceAtLeast(0L)
                    memoryBlocks.clear()
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
                storage.completedRanges.clear()
                storage.completedRanges += buildRangesFromBlocks(storage.cachedBlocks, storage.blockSizeBytes)
                persistConfigLocked()
            }

            val blockStart = blockIndex * storage.blockSizeBytes.toLong()
            val retryCount = (config?.readRetryCount ?: 0).coerceAtLeast(0)
            var fetched = ByteArray(0)
            for (attempt in 0..retryCount) {
                fetched = provider.readAtBytes(blockStart, storage.blockSizeBytes)
                if (fetched.isNotEmpty()) {
                    break
                }
            }
            if (fetched.isEmpty()) {
                return null
            }
            writeBlockToDisk(blockIndex, fetched)
            storage.cachedBlocks.add(blockIndex)
            storage.completedRanges.clear()
            storage.completedRanges += buildRangesFromBlocks(storage.cachedBlocks, storage.blockSizeBytes)
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

    }

    private data class SessionStorageFiles(
        val dataFile: File,
        val configFile: File,
        val extraFile: File,
        val blockSizeBytes: Int,
        var contentLength: Long,
        var durationMs: Long,
        val cachedBlocks: MutableSet<Long>,
        val completedRanges: MutableList<LongRange>
    )

    private data class StorageSnapshot(
        val blockSizeBytes: Int,
        val contentLength: Long,
        val durationMs: Long,
        val blockIndexes: Set<Long>,
        val completedRanges: List<LongRange>,
        val lastAccessEpochMs: Long
    )
}
