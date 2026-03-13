package com.wxy.playerlite.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object AccountVisualStyle {
    val accentColor = Color(0xFFD33A31)
    val accentDeepColor = Color(0xFFB42A23)
    val accentSoftColor = Color(0xFFFFE1DE)
    val accentTextColor = Color(0xFF7A1F1A)
    val pageBackgroundColors = listOf(
        Color(0xFFFFF7F6),
        Color(0xFFFFEDE9),
        Color(0xFFFFFBFA)
    )
    val heroBackgroundColors = listOf(
        Color(0xFFFF7A63),
        Color(0xFFD33A31),
        Color(0xFF9F231E)
    )
    val contentHorizontalPadding = 20.dp
    val sectionSpacing = 16.dp
    val contentMaxWidth = 420.dp
    val heroCorner = 34.dp
    val cardCorner = 30.dp
    val primaryButtonHeight = 56.dp
    val heroArtworkSize = 152.dp
    val profileHeaderMinHeight = 240.dp
}

internal fun accountPageBackgroundBrush(): Brush {
    return Brush.verticalGradient(AccountVisualStyle.pageBackgroundColors)
}

internal fun accountHeroBrush(): Brush {
    return Brush.verticalGradient(AccountVisualStyle.heroBackgroundColors)
}

@Composable
internal fun AccountPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(accountPageBackgroundBrush())
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-108).dp)
                .clip(CircleShape)
                .background(AccountVisualStyle.accentColor.copy(alpha = 0.14f))
                .defaultMinSize(minWidth = 320.dp, minHeight = 320.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 54.dp, y = 72.dp)
                .clip(CircleShape)
                .background(AccountVisualStyle.accentColor.copy(alpha = 0.08f))
                .defaultMinSize(minWidth = 180.dp, minHeight = 180.dp)
        )
        content()
    }
}

@Composable
internal fun AccountCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AccountVisualStyle.cardCorner),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                AccountVisualStyle.sectionSpacing
            ),
            content = content
        )
    }
}

@Composable
internal fun AccountPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(AccountVisualStyle.primaryButtonHeight),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccountVisualStyle.accentColor,
            contentColor = Color.White,
            disabledContainerColor = AccountVisualStyle.accentColor.copy(alpha = 0.48f),
            disabledContentColor = Color.White.copy(alpha = 0.82f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun AccountStatusChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
