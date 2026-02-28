package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.player.source.IPlaysource

internal class PreparedSourceSession {
    private var shouldAutoPlayWhenPrepared: Boolean = false

    private var selectedSource: IPlaysource? = null
    private var preparedItemId: String? = null

    fun currentSource(): IPlaysource? {
        return selectedSource
    }

    fun isPreparedFor(itemId: String): Boolean {
        return selectedSource != null && preparedItemId == itemId
    }

    fun preparedItemId(): String? {
        return preparedItemId
    }

    fun setAutoPlayWhenPrepared(enabled: Boolean) {
        shouldAutoPlayWhenPrepared = enabled
    }

    fun consumeAutoPlayIfPrepared(itemId: String): Boolean {
        if (!shouldAutoPlayWhenPrepared || !isPreparedFor(itemId)) {
            return false
        }
        shouldAutoPlayWhenPrepared = false
        return true
    }

    fun markPrepared(itemId: String, source: IPlaysource) {
        selectedSource = source
        preparedItemId = itemId
    }

    fun stopCurrent() {
        selectedSource?.stop()
    }

    fun release() {
        selectedSource?.abort()
        selectedSource?.close()
        selectedSource = null
        preparedItemId = null
        shouldAutoPlayWhenPrepared = false
    }
}
