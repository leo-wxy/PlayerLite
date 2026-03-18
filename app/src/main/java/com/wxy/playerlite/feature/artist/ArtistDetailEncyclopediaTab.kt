package com.wxy.playerlite.feature.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wxy.playerlite.feature.detail.previewSummaryText

internal object ArtistDetailEncyclopediaTab {
    @Composable
    fun ArtistDescriptionCard(
        summary: String,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("artist_description_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "歌手简介",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = previewSummaryText(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .testTag("artist_description_preview")
                )
                Text(
                    text = "查看全文",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onClick)
                )
            }
        }
    }

    @Composable
    fun ArtistEncyclopediaCard(
        body: String,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("artist_encyclopedia_panel"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "百科",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    fun artistDescriptionSummary(
        content: ArtistDetailContent,
        encyclopediaState: ArtistEncyclopediaUiState
    ): String {
        return when (encyclopediaState) {
            is ArtistEncyclopediaUiState.Content -> encyclopediaState.content.summary
                .ifBlank { content.encyclopediaSummary.ifBlank { content.briefDesc } }

            ArtistEncyclopediaUiState.Empty -> content.encyclopediaSummary.ifBlank { content.briefDesc }
            is ArtistEncyclopediaUiState.Error -> content.encyclopediaSummary.ifBlank { content.briefDesc }
            ArtistEncyclopediaUiState.Loading -> content.encyclopediaSummary.ifBlank { content.briefDesc }
        }.ifBlank {
            "暂时没有更多歌手简介。"
        }
    }

    fun artistDescriptionBody(
        content: ArtistDetailContent,
        encyclopediaState: ArtistEncyclopediaUiState
    ): String {
        val sections = when (encyclopediaState) {
            is ArtistEncyclopediaUiState.Content -> encyclopediaState.content.sections
            else -> content.encyclopediaSections
        }
        val summary = artistDescriptionSummary(content, encyclopediaState)
        val sectionText = sections.joinToString(separator = "\n\n") { section ->
            buildString {
                if (section.title.isNotBlank()) {
                    append(section.title)
                    append('\n')
                }
                append(section.body)
            }
        }
        return listOf(summary, sectionText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = "\n\n")
            .ifBlank {
                "暂时没有更多歌手简介。"
            }
    }
}
