package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object PlayerRuntimeRegistry {
    @Volatile
    private var runtime: PlayerRuntime? = null

    private val runtimeScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    fun get(context: Context): PlayerRuntime {
        val existing = runtime
        if (existing != null) {
            return existing
        }

        return synchronized(this) {
            runtime ?: PlayerRuntime(
                appContext = context.applicationContext,
                scope = runtimeScope
            ).also {
                runtime = it
            }
        }
    }
}
