package com.wxy.playerlite

import android.app.Application
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.search.SearchHostDependencies
import com.wxy.playerlite.feature.search.SearchHostDependenciesProvider
import com.wxy.playerlite.feature.search.SearchRouteHandler
import com.wxy.playerlite.feature.search.searchRouteIntent

class PlayerLiteApplication : Application(), SearchHostDependenciesProvider {
    override fun searchHostDependencies(): SearchHostDependencies {
        return SearchHostDependencies(
            repository = AppContainer.searchRepository(this),
            routeHandler = SearchRouteHandler { context, target ->
                searchRouteIntent(context, target)?.let(context::startActivity)
            }
        )
    }
}
