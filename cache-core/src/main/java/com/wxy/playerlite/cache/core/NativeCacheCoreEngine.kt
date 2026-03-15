package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import java.util.concurrent.ConcurrentHashMap

internal class NativeCacheCoreEngine : CacheCoreEngine {
    @Volatile
    private var initialized = false
    private val sessions = ConcurrentHashMap<Long, NativeBackedCacheSession>()

    override fun init(config: CacheCoreConfig): Result<Unit> {
        val rootPath = config.cacheRootDirPath.trim()
        if (rootPath.isEmpty()) {
            return Result.failure(IllegalArgumentException("cacheRootDirPath cannot be blank"))
        }
        val available = CacheCoreNativeBridge.isAvailable()
        if (!available) {
            return Result.failure(IllegalStateException("cache core native bridge unavailable"))
        }
        val ok = CacheCoreNativeBridge.init(
            cacheRootDirPath = rootPath,
            memoryCacheCapBytes = config.memoryCacheCapBytes,
            diskCacheMaxBytes = config.diskCacheMaxBytes,
            diskCacheCleanRangeMin = config.diskCacheCleanRangeMin,
            diskCacheCleanRangeMax = config.diskCacheCleanRangeMax,
            readRetryCount = config.readRetryCount
        )
        if (!ok) {
            return Result.failure(IllegalStateException("failed to initialize native cache runtime"))
        }
        initialized = true
        return Result.success(Unit)
    }

    override fun shutdown() {
        val snapshot = sessions.values.toList()
        snapshot.forEach { session ->
            runCatching { session.close() }
        }
        sessions.clear()
        CacheCoreNativeBridge.shutdown()
        initialized = false
        NativeProviderRegistry.clearForTesting()
    }

    override fun isInitialized(): Boolean {
        return initialized && CacheCoreNativeBridge.isInitialized()
    }

    override fun openSession(params: OpenSessionParams): Result<CacheSession> {
        if (!isInitialized()) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (params.resourceKey.isBlank()) {
            return Result.failure(IllegalArgumentException("resourceKey cannot be blank"))
        }
        val providerHandle = NativeProviderRegistry.register(params.provider)
        val sessionId = CacheCoreNativeBridge.openSession(
            resourceKey = params.resourceKey,
            blockSizeBytes = params.config.blockSizeBytes,
            contentLengthHint = params.contentLengthHint ?: -1L,
            durationMsHint = params.durationMsHint ?: -1L,
            providerHandle = providerHandle
        )
        if (sessionId <= 0L) {
            NativeProviderRegistry.release(providerHandle)
            CacheCoreNativeBridge.releaseProviderHandle(providerHandle)
            return Result.failure(IllegalStateException("native openSession failed"))
        }

        val session = NativeBackedCacheSession(
            sessionId = sessionId,
            resourceKey = params.resourceKey,
            providerHandle = providerHandle,
            onClose = { closedSessionId -> sessions.remove(closedSessionId) }
        )
        sessions[sessionId] = session
        return Result.success(session)
    }

    override fun clearAll(): Result<Unit> {
        if (!isInitialized()) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        return runCatching {
            val snapshot = sessions.values.toList()
            snapshot.forEach { session -> session.close() }
            sessions.clear()
            if (!CacheCoreNativeBridge.clearAll()) {
                error("native clearAll failed")
            }
        }
    }

    override fun lookup(resourceKey: String): Result<CacheLookupSnapshot?> {
        if (!isInitialized()) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (resourceKey.isBlank()) {
            return Result.failure(IllegalArgumentException("resourceKey cannot be blank"))
        }
        return runCatching {
            val json = CacheCoreNativeBridge.lookup(resourceKey) ?: return@runCatching null
            parseLookupSnapshot(json)
        }
    }

    override fun lookupByPrefix(prefix: String, limit: Int): Result<List<CacheLookupSnapshot>> {
        if (!isInitialized()) {
            return Result.failure(IllegalStateException("cache core not initialized"))
        }
        if (limit <= 0) {
            return Result.success(emptyList())
        }
        return runCatching {
            val raw = CacheCoreNativeBridge.lookupByPrefix(prefix, limit)
            parseLookupSnapshotArray(raw)
        }
    }

    private class NativeBackedCacheSession(
        override val sessionId: Long,
        override val resourceKey: String,
        private val providerHandle: Long,
        private val onClose: (Long) -> Unit
    ) : CacheSession {
        @Volatile
        private var closed = false

        override fun read(size: Int): Result<ByteArray> {
            if (size <= 0) {
                return Result.success(ByteArray(0))
            }
            if (closed) {
                return Result.failure(IllegalStateException("session already closed"))
            }
            val bytes = CacheCoreNativeBridge.read(sessionId, size)
                ?: return Result.failure(IllegalStateException("native read failed"))
            return Result.success(bytes)
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
            val bytes = CacheCoreNativeBridge.readAt(sessionId, offset, size)
                ?: return Result.failure(IllegalStateException("native readAt failed"))
            return Result.success(bytes)
        }

        override fun seek(offset: Long, whence: Int): Result<Long> {
            if (closed) {
                return Result.failure(IllegalStateException("session already closed"))
            }
            val result = CacheCoreNativeBridge.seek(sessionId, offset, whence)
            if (result < 0L) {
                return Result.failure(IllegalStateException("native seek failed"))
            }
            return Result.success(result)
        }

        override fun cancelPendingRead() {
            if (closed) {
                return
            }
            CacheCoreNativeBridge.cancelPendingRead(sessionId)
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            runCatching { CacheCoreNativeBridge.closeSession(sessionId) }
            runCatching { CacheCoreNativeBridge.releaseProviderHandle(providerHandle) }
            onClose(sessionId)
        }
    }

    companion object {
        private fun parseLookupSnapshotArray(raw: String): List<CacheLookupSnapshot> {
            if (raw.isBlank() || raw == "[]") {
                return emptyList()
            }
            val objectRegex = Regex("\\{[\\s\\S]*?}")
            return objectRegex.findAll(raw)
                .mapNotNull { match -> parseLookupSnapshot(match.value) }
                .toList()
        }

        private fun parseLookupSnapshot(raw: String): CacheLookupSnapshot? {
            val resourceKey = parseStringField(raw, "resourceKey") ?: return null
            val dataFilePath = parseStringField(raw, "dataFilePath") ?: return null
            val configFilePath = parseStringField(raw, "configFilePath") ?: return null
            val extraFilePath = parseStringField(raw, "extraFilePath") ?: return null
            val dataFileSizeBytes = parseLongField(raw, "dataFileSizeBytes") ?: 0L
            val blockSizeBytes = (parseLongField(raw, "blockSizeBytes") ?: (64 * 1024L)).toInt()
            val contentLength = parseLongField(raw, "contentLength") ?: -1L
            val durationMs = parseLongField(raw, "durationMs") ?: -1L
            val lastAccessEpochMs = parseLongField(raw, "lastAccessEpochMs") ?: -1L
            val cachedBlocks = parseLongArrayField(raw, "cachedBlocks").toSet()
            val completedRanges = parseRangeArrayField(raw, "completedRanges")
            return CacheLookupSnapshot(
                resourceKey = resourceKey,
                dataFilePath = dataFilePath,
                configFilePath = configFilePath,
                extraFilePath = extraFilePath,
                dataFileSizeBytes = dataFileSizeBytes,
                blockSizeBytes = blockSizeBytes,
                contentLength = contentLength,
                durationMs = durationMs,
                cachedBlocks = cachedBlocks,
                lastAccessEpochMs = lastAccessEpochMs,
                completedRanges = completedRanges
            )
        }

        private fun parseStringField(json: String, field: String): String? {
            val regex = Regex("\"$field\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            val raw = regex.find(json)?.groupValues?.get(1) ?: return null
            return raw
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }

        private fun parseLongField(json: String, field: String): Long? {
            val regex = Regex("\"$field\"\\s*:\\s*(-?\\d+)")
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
        }

        private fun parseLongArrayField(json: String, field: String): List<Long> {
            val regex = Regex("\"$field\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            val body = regex.find(json)?.groupValues?.get(1) ?: return emptyList()
            if (body.isBlank()) {
                return emptyList()
            }
            return body.split(",").mapNotNull { it.trim().toLongOrNull() }
        }

        private fun parseRangeArrayField(json: String, field: String): List<CacheCompletedRange> {
            val regex = Regex("\"$field\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            val body = regex.find(json)?.groupValues?.get(1) ?: return emptyList()
            if (body.isBlank()) {
                return emptyList()
            }
            val objectRegex = Regex("\\{[^}]*}")
            return objectRegex.findAll(body)
                .mapNotNull { match ->
                    val start = parseLongField(match.value, "start") ?: return@mapNotNull null
                    val end = parseLongField(match.value, "end") ?: return@mapNotNull null
                    if (end <= start) {
                        return@mapNotNull null
                    }
                    CacheCompletedRange(start = start, endExclusive = end)
                }
                .toList()
        }
    }
}
