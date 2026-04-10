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
    fun horizontalSections_shouldUseTighterEdgePaddingForDenserViewport() {
        assertEquals(
            4.dp,
            HomeDiscoveryLayoutSpec.rowContentPadding.calculateLeftPadding(LayoutDirection.Ltr)
        )
        assertEquals(
            4.dp,
            HomeDiscoveryLayoutSpec.rowContentPadding.calculateRightPadding(LayoutDirection.Ltr)
        )
    }

    @Test
    fun cards_shouldUseFixedHeightsToAvoidViewportJitter() {
        assertEquals(206.dp, HomeDiscoveryLayoutSpec.bannerHeight)
        assertEquals(222.dp, HomeDiscoveryLayoutSpec.discoveryCardHeight)
        assertEquals(116.dp, HomeDiscoveryLayoutSpec.compactCardHeight)
        assertEquals(82.dp, HomeDiscoveryLayoutSpec.songCardHeight)
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
    fun dailyShortcutCards_shouldUseCompactIconLeadingStyle() {
        assertTrue(HomeDiscoveryLayoutSpec.dailyShortcutUsesCompactIconStyle)
        assertEquals(116.dp, HomeDiscoveryLayoutSpec.compactCardHeight)
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
    fun songCards_shouldUseStableWidthFractionAndSpacing() {
        assertEquals(0.7f, HomeDiscoveryLayoutSpec.songCardWidthFraction)
        assertEquals(18.dp, HomeDiscoveryLayoutSpec.songCardSpacing)
        assertEquals(3, HomeDiscoveryLayoutSpec.songColumnItemCount)
        assertEquals(0.dp, HomeDiscoveryLayoutSpec.songColumnItemSpacing)
        assertEquals(0.dp, HomeDiscoveryLayoutSpec.songSectionCornerRadius)
    }

    @Test
    fun homepageCards_shouldUseTighterCornerHierarchy() {
        assertEquals(18.dp, HomeDiscoveryLayoutSpec.bannerCardCornerRadius)
        assertEquals(16.dp, HomeDiscoveryLayoutSpec.standardCardCornerRadius)
        assertEquals(22.dp, HomeDiscoveryLayoutSpec.compactCardCornerRadius)
        assertEquals(10.dp, HomeDiscoveryLayoutSpec.songCardCornerRadius)
    }

    @Test
    fun songCards_shouldReserveArtworkAndOverflowActionSpace() {
        assertEquals(56.dp, HomeDiscoveryLayoutSpec.songCardCoverSize)
        assertEquals(12.dp, HomeDiscoveryLayoutSpec.songCardCoverCornerRadius)
        assertEquals(24.dp, HomeDiscoveryLayoutSpec.songCardMenuButtonSize)
    }

    @Test
    fun homeSearchBox_shouldUseCalmerChrome() {
        assertEquals(16.dp, HomeDiscoveryLayoutSpec.searchBoxCornerRadius)
        assertEquals(0.dp, HomeDiscoveryLayoutSpec.searchBoxShadowElevation)
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
