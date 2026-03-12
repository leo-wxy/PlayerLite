package com.wxy.playerlite.feature.search

import android.content.Context

interface SearchHostDependenciesProvider {
    fun searchHostDependencies(): SearchHostDependencies
}

data class SearchHostDependencies(
    val repository: SearchRepository,
    val routeHandler: SearchRouteHandler = SearchRouteHandler { _, _ -> Unit }
)

fun interface SearchRouteHandler {
    fun open(context: Context, target: SearchRouteTarget)
}

internal fun Context.requireSearchHostDependencies(): SearchHostDependencies {
    val provider = applicationContext as? SearchHostDependenciesProvider
        ?: error("Application must implement SearchHostDependenciesProvider")
    return provider.searchHostDependencies()
}
