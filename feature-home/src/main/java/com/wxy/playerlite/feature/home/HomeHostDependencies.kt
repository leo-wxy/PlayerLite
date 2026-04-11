package com.wxy.playerlite.feature.home

import android.content.Context

interface HomeHostDependenciesProvider {
    fun homeHostDependencies(): HomeHostDependencies
}

data class HomeHostDependencies(
    val repository: HomeDiscoveryRepository
)

internal fun Context.requireHomeHostDependencies(): HomeHostDependencies {
    val provider = (this as? HomeHostDependenciesProvider)
        ?: runCatching { applicationContext as? HomeHostDependenciesProvider }.getOrNull()
        ?: error("Application must implement HomeHostDependenciesProvider")
    return provider.homeHostDependencies()
}
