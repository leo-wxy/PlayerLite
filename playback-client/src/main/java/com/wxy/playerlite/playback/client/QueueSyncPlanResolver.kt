package com.wxy.playerlite.playback.client

internal object QueueSyncPlanResolver {
    fun shouldReplaceQueue(
        currentMediaIds: List<String>,
        currentIndex: Int,
        currentMediaId: String?,
        requestedMediaIds: List<String>,
        requestedIndex: Int
    ): Boolean {
        if (currentMediaIds.size != requestedMediaIds.size) {
            return true
        }
        if (currentMediaIds != requestedMediaIds) {
            return true
        }
        val normalizedRequestedIndex = requestedIndex.coerceIn(0, requestedMediaIds.lastIndex)
        val requestedCurrentMediaId = requestedMediaIds.getOrNull(normalizedRequestedIndex)
        if (!currentMediaId.isNullOrBlank()) {
            return currentMediaId != requestedCurrentMediaId
        }
        return currentIndex != normalizedRequestedIndex
    }
}
