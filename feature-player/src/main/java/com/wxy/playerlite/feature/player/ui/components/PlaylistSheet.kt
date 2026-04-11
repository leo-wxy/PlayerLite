package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.playback.model.PlaybackMode

val PlaylistSheetFirstVisibleIndexKey =
    SemanticsPropertyKey<Int>("PlaylistSheetFirstVisibleIndex")

internal var SemanticsPropertyReceiver.playlistSheetFirstVisibleIndex by
    PlaylistSheetFirstVisibleIndexKey

data class PlaylistSheetLayoutSpec(
    val isLandscape: Boolean,
    val widthFraction: Float,
    val minWidthDp: Float? = null,
    val maxWidthDp: Float? = null,
    val heightFraction: Float,
    val dockToEnd: Boolean
)

fun resolvePlaylistSheetLayoutSpec(
    viewportWidthDp: Float,
    viewportHeightDp: Float
): PlaylistSheetLayoutSpec {
    val isLandscape = viewportWidthDp > viewportHeightDp
    return if (isLandscape) {
        PlaylistSheetLayoutSpec(
            isLandscape = true,
            widthFraction = 0.5f,
            minWidthDp = 360f,
            maxWidthDp = 560f,
            heightFraction = 0.84f,
            dockToEnd = true
        )
    } else {
        PlaylistSheetLayoutSpec(
            isLandscape = false,
            widthFraction = 1f,
            heightFraction = 0.74f,
            dockToEnd = false
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PlaylistBottomSheet(
    visible: Boolean,
    items: List<PlaylistItem>,
    activeIndex: Int,
    playbackMode: PlaybackMode,
    showOriginalOrderInShuffle: Boolean,
    canReorder: Boolean,
    onDismiss: () -> Unit,
    onCyclePlaybackMode: () -> Unit = {},
    onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onClearAll: () -> Unit = {},
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    val brandPalette = PlayerLiteThemeContract.DefaultBrandPalettes.light
    val scrimInteraction = remember { MutableInteractionSource() }
    val reorderStepPx = with(LocalDensity.current) { 64.dp.toPx() }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val activeScrollTarget = items.getOrNull(activeIndex)?.id?.let { itemId ->
        itemId to activeIndex
    }
    var lastAutoScrolledTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(visible) {
        if (!visible) {
            lastAutoScrolledTarget = null
        }
    }

    LaunchedEffect(visible, activeScrollTarget) {
        val target = activeScrollTarget
        if (!visible || target == null || activeIndex !in items.indices) {
            return@LaunchedEffect
        }

        if (lastAutoScrolledTarget == target) {
            return@LaunchedEffect
        }

        listState.scrollToItem(index = target.second)
        lastAutoScrolledTarget = target
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(280),
            initialOffsetY = { it }
        ),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
            animationSpec = tween(220),
            targetOffsetY = { it }
        ),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layoutSpec = resolvePlaylistSheetLayoutSpec(
                viewportWidthDp = maxWidth.value,
                viewportHeightDp = maxHeight.value
            )
            val surfaceShape = if (layoutSpec.isLandscape) {
                RoundedCornerShape(28.dp)
            } else {
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            }
            val surfaceModifier = if (layoutSpec.isLandscape) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .fillMaxWidth(layoutSpec.widthFraction)
                    .widthIn(
                        min = (layoutSpec.minWidthDp ?: 0f).dp,
                        max = (layoutSpec.maxWidthDp ?: Float.MAX_VALUE).dp
                    )
                    .fillMaxHeight(layoutSpec.heightFraction)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(layoutSpec.widthFraction)
                    .fillMaxHeight(layoutSpec.heightFraction)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.26f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Surface(
                modifier = surfaceModifier
                    .testTag("playlist_sheet_surface"),
                shape = surfaceShape,
                color = brandPalette.neutral,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = visualTokens.dividerSubtle.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 20.dp,
                            top = 14.dp,
                            end = 20.dp,
                            bottom = 14.dp + navigationBottomPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 42.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(visualTokens.handleMuted)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "接下来播放",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "当前播放队列 • ${items.size} 首",
                                style = MaterialTheme.typography.bodyMedium,
                                color = visualTokens.textMuted
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(onClick = onCyclePlaybackMode)
                                    .testTag("playlist_sheet_mode_button")
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = playbackMode.icon(),
                                    contentDescription = null,
                                    tint = visualTokens.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = playbackMode.label(),
                                    color = visualTokens.accentStrong
                                )
                            }
                            if (items.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearAll,
                                    modifier = Modifier.testTag("playlist_sheet_clear_all")
                                ) {
                                    Text(
                                        text = "清空",
                                        color = visualTokens.accentStrong
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭播放列表"
                                )
                            }
                        }
                    }

                    if (playbackMode == PlaybackMode.SHUFFLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "显示原始顺序",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Checkbox(
                                checked = showOriginalOrderInShuffle,
                                onCheckedChange = onShowOriginalOrderInShuffleChange
                            )
                        }
                    }

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "播放列表为空，点击右上角文件按钮添加音频",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("playlist_sheet_list")
                                .semantics {
                                    playlistSheetFirstVisibleIndex = listState.firstVisibleItemIndex
                                },
                            state = listState,
                            contentPadding = PaddingValues(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                val isActive = index == activeIndex
                                val isDragging = index == draggingIndex
                                val itemVisuals = resolvePlaylistSheetItemVisuals(
                                    isActive = isActive,
                                    isDragging = isDragging,
                                    canReorder = canReorder,
                                    visualTokens = visualTokens
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) draggingOffsetY else 0f
                                            scaleX = if (isDragging) 1.01f else 1f
                                            scaleY = if (isDragging) 1.01f else 1f
                                        }
                                        .let { baseModifier ->
                                            if (!canReorder) {
                                                baseModifier
                                            } else {
                                                baseModifier.pointerInput(items.size, index, reorderStepPx) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggingIndex = index
                                                            draggingOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingIndex = -1
                                                            draggingOffsetY = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingIndex = -1
                                                            draggingOffsetY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()

                                                            if (draggingIndex < 0) {
                                                                return@detectDragGesturesAfterLongPress
                                                            }

                                                            draggingOffsetY += dragAmount.y

                                                            if (draggingOffsetY > reorderStepPx && draggingIndex < items.lastIndex) {
                                                                val from = draggingIndex
                                                                val to = from + 1
                                                                onMove(from, to)
                                                                draggingIndex = to
                                                                draggingOffsetY -= reorderStepPx
                                                            }

                                                            if (draggingOffsetY < -reorderStepPx && draggingIndex > 0) {
                                                                val from = draggingIndex
                                                                val to = from - 1
                                                                onMove(from, to)
                                                                draggingIndex = to
                                                                draggingOffsetY += reorderStepPx
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        .clickable(enabled = !isDragging) { onSelect(index) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = itemVisuals.containerColor,
                                    tonalElevation = if (itemVisuals.raised) 1.dp else 0.dp,
                                    shadowElevation = if (itemVisuals.raised) 10.dp else 0.dp,
                                    border = itemVisuals.border
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 78.dp)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(if (isActive) 56.dp else 52.dp)
                                                .testTag("playlist_sheet_artwork_${item.id}"),
                                            shape = RoundedCornerShape(16.dp),
                                            color = itemVisuals.artworkFallbackContainerColor
                                        ) {
                                            if (!item.coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.coverUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.LibraryMusic,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.effectiveTitle,
                                                style = if (isActive) {
                                                    MaterialTheme.typography.titleMedium
                                                } else {
                                                    MaterialTheme.typography.bodyLarge
                                                },
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                                color = itemVisuals.titleColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = resolvePlaylistSheetItemSubtitle(item),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = itemVisuals.subtitleColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemove(index) },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = visualTokens.textSecondary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "移除播放项"
                                            )
                                        }

                                        PlaylistSheetDragHandle(
                                            tint = itemVisuals.dragHandleTint,
                                            containerColor = itemVisuals.dragHandleContainerColor,
                                            enabled = canReorder,
                                            modifier = Modifier
                                                .testTag("playlist_sheet_drag_handle_${item.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PlaybackMode.label(): String {
    return when (this) {
        PlaybackMode.LIST_LOOP -> "列表循环"
        PlaybackMode.SINGLE_LOOP -> "单曲循环"
        PlaybackMode.SHUFFLE -> "随机播放"
    }
}

private fun PlaybackMode.icon() = when (this) {
    PlaybackMode.LIST_LOOP -> Icons.Rounded.Repeat
    PlaybackMode.SINGLE_LOOP -> Icons.Rounded.RepeatOne
    PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
}

data class PlaylistSheetItemVisuals(
    val containerColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val dragHandleTint: Color,
    val dragHandleContainerColor: Color,
    val artworkFallbackContainerColor: Color,
    val border: BorderStroke?,
    val raised: Boolean
)

fun resolvePlaylistSheetItemVisuals(
    isActive: Boolean,
    isDragging: Boolean,
    canReorder: Boolean,
    visualTokens: com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTokens
): PlaylistSheetItemVisuals {
    val baseBorder = BorderStroke(
        width = 1.dp,
        color = visualTokens.dividerSubtle.copy(alpha = 0.48f)
    )
    return when {
        isDragging -> PlaylistSheetItemVisuals(
            containerColor = visualTokens.surfaceRaised,
            titleColor = visualTokens.accentStrong,
            subtitleColor = visualTokens.accentStrong.copy(alpha = 0.84f),
            dragHandleTint = visualTokens.accentStrong,
            dragHandleContainerColor = visualTokens.accentStrong.copy(alpha = 0.10f),
            artworkFallbackContainerColor = visualTokens.accentStrong.copy(alpha = 0.10f),
            border = baseBorder,
            raised = true
        )

        isActive -> PlaylistSheetItemVisuals(
            containerColor = visualTokens.surfaceRaised,
            titleColor = visualTokens.accentStrong,
            subtitleColor = visualTokens.accentStrong.copy(alpha = 0.82f),
            dragHandleTint = visualTokens.accentStrong,
            dragHandleContainerColor = visualTokens.accentStrong.copy(alpha = 0.10f),
            artworkFallbackContainerColor = visualTokens.accentStrong.copy(alpha = 0.10f),
            border = baseBorder,
            raised = true
        )

        else -> PlaylistSheetItemVisuals(
            containerColor = Color.Transparent,
            titleColor = PlayerLiteThemeContract.DefaultBrandPalettes.light.onSurface,
            subtitleColor = visualTokens.textMuted,
            dragHandleTint = visualTokens.textSecondary,
            dragHandleContainerColor = visualTokens.surfaceMuted.copy(alpha = if (canReorder) 0.55f else 0.30f),
            artworkFallbackContainerColor = visualTokens.surfaceMuted.copy(alpha = 0.85f),
            border = null,
            raised = false
        )
    }
}

fun resolvePlaylistSheetItemSubtitle(item: PlaylistItem): String {
    return item.artistText
        ?.takeIf { it.isNotBlank() }
        ?: item.albumTitle?.takeIf { it.isNotBlank() }
        ?: if (item.isOnline) "在线歌曲" else "本地音频"
}

@Composable
private fun PlaylistSheetDragHandle(
    tint: Color,
    containerColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (enabled) tint else tint.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }
        }
    }
}
