package com.wxy.playerlite.feature.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal fun shouldUseLightStatusBarContent(backgroundColor: Color): Boolean {
    return backgroundColor.luminance() < 0.45f
}
