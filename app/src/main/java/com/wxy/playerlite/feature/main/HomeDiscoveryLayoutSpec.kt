package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object HomeDiscoveryLayoutSpec {
    val rowContentPadding = PaddingValues(horizontal = 8.dp)
    val bannerContentPadding = PaddingValues(horizontal = 4.dp)

    val bannerHeight = 188.dp
    val discoveryCardWidth = 164.dp
    val discoveryCardHeight = 236.dp
    val compactCardWidth = 112.dp
    val compactCardHeight = 84.dp
    val compactImageSize = 56.dp
    val itemSpacing = 12.dp

    const val titleMaxLines = 1
    const val subtitleMaxLines = 1
    const val bannerBadgeUsesTextOnlyStyle = true
    const val bannerImageFillsCard = true
    const val bannerUsesInfiniteLoop = true
    const val dailyShortcutUsesTextOnlyStyle = true
    const val discoveryImageUsesFullBleed = true
    const val discoveryImageAspectRatio = 1f
    const val virtualBannerPageCount = 4_000

    private val dailyShortcutPalette = listOf(
        Color(0xFFFFF1E6),
        Color(0xFFFFE8EE),
        Color(0xFFEAF4FF),
        Color(0xFFF1EDFF),
        Color(0xFFE8FAF1),
        Color(0xFFFFF4DA)
    )

    fun usesCarousel(layout: HomeSectionLayout): Boolean {
        return layout == HomeSectionLayout.BANNER
    }

    fun initialBannerPage(itemCount: Int): Int {
        if (itemCount <= 1) {
            return 0
        }
        val midpoint = virtualBannerPageCount / 2
        return midpoint - (midpoint % itemCount)
    }

    fun recenterBannerPage(currentPage: Int, itemCount: Int): Int {
        if (itemCount <= 1 || !bannerUsesInfiniteLoop) {
            return 0
        }
        val nearStart = currentPage < itemCount
        val nearEnd = currentPage > virtualBannerPageCount - itemCount - 1
        if (!nearStart && !nearEnd) {
            return currentPage
        }
        return initialBannerPage(itemCount) + positiveMod(currentPage, itemCount)
    }

    fun dailyShortcutBackgroundColor(seed: String): Color {
        val stableSeed = seed.ifBlank { "home-shortcut" }
        return dailyShortcutPalette[positiveMod(stableSeed.hashCode(), dailyShortcutPalette.size)]
    }

    private fun positiveMod(value: Int, divisor: Int): Int {
        return ((value % divisor) + divisor) % divisor
    }
}
