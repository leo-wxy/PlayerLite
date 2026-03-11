package com.wxy.playerlite.feature.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDiscoveryLayoutSpecTest {
    @Test
    fun bannerSections_shouldUseCarouselPresentation() {
        assertTrue(HomeDiscoveryLayoutSpec.usesCarousel(HomeSectionLayout.BANNER))
    }

    @Test
    fun horizontalSections_shouldReserveContainerPaddingForShadowSpace() {
        assertEquals(
            8.dp,
            HomeDiscoveryLayoutSpec.rowContentPadding.calculateLeftPadding(LayoutDirection.Ltr)
        )
        assertEquals(
            8.dp,
            HomeDiscoveryLayoutSpec.rowContentPadding.calculateRightPadding(LayoutDirection.Ltr)
        )
    }

    @Test
    fun cards_shouldUseFixedHeightsToAvoidViewportJitter() {
        assertEquals(188.dp, HomeDiscoveryLayoutSpec.bannerHeight)
        assertEquals(236.dp, HomeDiscoveryLayoutSpec.discoveryCardHeight)
        assertEquals(84.dp, HomeDiscoveryLayoutSpec.compactCardHeight)
    }

    @Test
    fun cardText_shouldClampToSingleLineForStableHeight() {
        assertEquals(1, HomeDiscoveryLayoutSpec.titleMaxLines)
        assertEquals(1, HomeDiscoveryLayoutSpec.subtitleMaxLines)
    }

    @Test
    fun bannerBadge_shouldUsePlainTextStyle() {
        assertTrue(HomeDiscoveryLayoutSpec.bannerBadgeUsesTextOnlyStyle)
    }

    @Test
    fun bannerCarousel_shouldUseSmallerSidePaddingThanRegularRows() {
        assertTrue(
            HomeDiscoveryLayoutSpec.bannerContentPadding.calculateLeftPadding(LayoutDirection.Ltr) <
                HomeDiscoveryLayoutSpec.rowContentPadding.calculateLeftPadding(LayoutDirection.Ltr)
        )
    }

    @Test
    fun bannerImage_shouldFillTheWholeCard() {
        assertTrue(HomeDiscoveryLayoutSpec.bannerImageFillsCard)
    }

    @Test
    fun bannerCarousel_shouldUseVirtualLoopingPages() {
        assertTrue(HomeDiscoveryLayoutSpec.bannerUsesInfiniteLoop)
        assertTrue(HomeDiscoveryLayoutSpec.virtualBannerPageCount > 1000)
    }

    @Test
    fun dailyShortcutCards_shouldUseTextOnlyCompactStyle() {
        assertTrue(HomeDiscoveryLayoutSpec.dailyShortcutUsesTextOnlyStyle)
        assertEquals(84.dp, HomeDiscoveryLayoutSpec.compactCardHeight)
    }

    @Test
    fun dailyShortcutCards_shouldDeriveStableAccentBackgrounds() {
        val expected = HomeDiscoveryLayoutSpec.dailyShortcutBackgroundColor("每日推荐")
        val actual = HomeDiscoveryLayoutSpec.dailyShortcutBackgroundColor("每日推荐")

        assertEquals(expected, actual)
        assertTrue(actual != Color.Unspecified)
    }

    @Test
    fun discoveryCards_shouldUseFullBleedSquareArtwork() {
        assertTrue(HomeDiscoveryLayoutSpec.discoveryImageUsesFullBleed)
        assertEquals(1f, HomeDiscoveryLayoutSpec.discoveryImageAspectRatio)
    }

    @Test
    fun bannerCarousel_shouldRecentreNearEdgesWhileKeepingSameLogicalItem() {
        val itemCount = 5
        val edgePage = HomeDiscoveryLayoutSpec.virtualBannerPageCount - 1
        val recenteredPage = HomeDiscoveryLayoutSpec.recenterBannerPage(
            currentPage = edgePage,
            itemCount = itemCount
        )

        assertEquals(edgePage % itemCount, recenteredPage % itemCount)
        assertTrue(recenteredPage < edgePage)
        assertTrue(recenteredPage >= HomeDiscoveryLayoutSpec.initialBannerPage(itemCount))
    }
}
