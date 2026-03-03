package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
                configFile.writeText(buildDefaultConfigJson(params))
            }

            if (!extraFile.exists()) {
                extraFile.writeText("{\n}\n")
            }
            SessionStorageFiles(dataFile = dataFile, configFile = configFile, extraFile = extraFile)
        }
    }

    private fun buildDefaultConfigJson(params: OpenSessionParams): String {
        val contentLength = params.contentLengthHint ?: -1L
        val durationMs = params.durationMsHint ?: -1L
        val now = System.currentTimeMillis()
        return buildString {
            append("{\n")
            append("  \"version\": 1,\n")
            append("  \"resourceKey\": \"${escapeJson(params.resourceKey)}\",\n")
            append("  \"contentLength\": $contentLength,\n")
            append("  \"durationMs\": $durationMs,\n")
            append("  \"blockSizeBytes\": ${params.config.blockSizeBytes},\n")
            append("  \"blocks\": [],\n")
            append("  \"completedRanges\": [],\n")
            append("  \"lastAccessEpochMs\": $now\n")
            append("}\n")
        }
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
            return runCatching { provider.readAt(offset, size) }
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
                        SEEK_END -> provider.queryContentLength() ?: 0L
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
            runCatching { provider.close() }
            if (removeSession) {
                onClose(sessionId)
            }
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
        val extraFile: File
    )
}
