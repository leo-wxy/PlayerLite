package com.wxy.playerlite.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailPagingFooter(
    footerTagPrefix: String,
    loadTriggerKey: Int,
    isLoadingMore: Boolean,
    loadMoreErrorMessage: String?,
    endReached: Boolean,
    loadingText: String,
    endText: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        loadMoreErrorMessage != null -> {
            DetailErrorCard(
                message = loadMoreErrorMessage,
                onRetry = onRetry,
                testTag = "${footerTagPrefix}_load_more_error"
            )
        }

        isLoadingMore -> {
            DetailLoadingCard(
                text = loadingText,
                modifier = Modifier.testTag("${footerTagPrefix}_load_more_loading")
            )
        }

        !endReached -> {
            LaunchedEffect(loadTriggerKey) {
                onLoadMore()
            }
            DetailLoadingCard(
                text = loadingText,
                modifier = Modifier.testTag("${footerTagPrefix}_load_more_loading")
            )
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("${footerTagPrefix}_load_more_end")
            ) {
                Text(
                    text = endText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
