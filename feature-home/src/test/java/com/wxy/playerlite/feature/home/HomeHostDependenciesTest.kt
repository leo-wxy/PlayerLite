package com.wxy.playerlite.feature.home

import android.app.Application
import org.junit.Assert.assertSame
import org.junit.Test

class HomeHostDependenciesTest {
    @Test
    fun requireHomeHostDependencies_shouldReturnProvidedDependencies() {
        val repository = object : HomeDiscoveryRepository {
            override suspend fun fetchHomeOverview(): HomeOverviewContent {
                return HomeOverviewContent(
                    sections = emptyList(),
                    searchKeywords = emptyList()
                )
            }
        }
        val application = TestHomeApplication(
            dependencies = HomeHostDependencies(repository = repository)
        )

        val resolved = application.requireHomeHostDependencies()

        assertSame(repository, resolved.repository)
    }

    @Test(expected = IllegalStateException::class)
    fun requireHomeHostDependencies_shouldFailWhenProviderMissing() {
        val application = Application()

        application.requireHomeHostDependencies()
    }
}

private class TestHomeApplication(
    private val dependencies: HomeHostDependencies
) : Application(), HomeHostDependenciesProvider {
    override fun homeHostDependencies(): HomeHostDependencies = dependencies
}
