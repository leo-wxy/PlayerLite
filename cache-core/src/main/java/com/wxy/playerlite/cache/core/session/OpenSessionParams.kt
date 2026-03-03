package com.wxy.playerlite.cache.core.session

import com.wxy.playerlite.cache.core.provider.RangeDataProvider

data class OpenSessionParams(
    val resourceKey: String,
    val provider: RangeDataProvider,
    val config: SessionCacheConfig = SessionCacheConfig(),
    val contentLengthHint: Long? = null,
    val durationMsHint: Long? = null,
    val extra: Map<String, String> = emptyMap()
)
