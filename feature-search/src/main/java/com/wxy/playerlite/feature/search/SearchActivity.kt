package com.wxy.playerlite.feature.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

class SearchActivity : ComponentActivity() {
    private val hostDependencies by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.requireSearchHostDependencies()
    }
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModel.factory(
            application = application,
            repository = hostDependencies.repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            SearchFeatureTheme {
                SearchScreen(
                    state = state,
                    onBack = ::finish,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSubmitSearch = viewModel::submitSearch,
                    onHistoryKeywordClick = viewModel::submitSearch,
                    onSuggestionClick = viewModel::onSuggestionClick,
                    onHotKeywordClick = viewModel::onHotKeywordClick,
                    onResultTypeSelected = viewModel::onResultTypeSelected,
                    onResultClick = { target ->
                        hostDependencies.routeHandler.open(this@SearchActivity, target)
                    },
                    onRetry = viewModel::retryCurrentMode
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, SearchActivity::class.java)
        }
    }
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onHistoryKeywordClick: (String) -> Unit,
    onSuggestionClick: (SearchSuggestionUiModel) -> Unit,
    onHotKeywordClick: (SearchHotKeywordUiModel) -> Unit,
    onResultTypeSelected: (SearchResultType) -> Unit,
    onResultClick: (SearchRouteTarget) -> Unit,
    onRetry: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SEARCH_PAGE_BACKGROUND_BRUSH)
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            SearchTopBar(
                query = state.query,
                onBack = onBack,
                onQueryChanged = onQueryChanged,
                onSubmitSearch = onSubmitSearch,
                usesExpandedTypography = usesExpandedTypography,
                modifier = Modifier.testTag("search_top_bar")
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (state.pageMode == SearchPageMode.HOT && state.historyKeywords.isNotEmpty()) {
                SearchPinnedHistorySection(
                    historyKeywords = state.historyKeywords,
                    onHistoryKeywordClick = onHistoryKeywordClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (usesExpandedTypography) 96.dp else 88.dp)
                        .testTag("search_history_pinned")
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when (state.pageMode) {
                    SearchPageMode.HOT -> {
                        SearchHotContent(
                            state = state.hotState,
                            onHotKeywordClick = onHotKeywordClick,
                            onRetry = onRetry
                        )
                    }

                    SearchPageMode.SUGGEST -> {
                        SearchSuggestContent(
                            state = state.suggestState,
                            onSuggestionClick = onSuggestionClick,
                            onRetry = onRetry
                        )
                    }

                    SearchPageMode.RESULT -> {
                        SearchResultContent(
                            selectedResultState = state.resultState,
                            resultStatesByType = state.resultStatesByType,
                            selectedResultType = state.selectedResultType,
                            availableResultTypes = state.availableResultTypes,
                            onResultTypeSelected = onResultTypeSelected,
                            onResultClick = onResultClick,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    usesExpandedTypography: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            tonalElevation = 0.dp,
            color = SEARCH_PANEL_COLOR.copy(alpha = 0.9f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            )
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(if (usesExpandedTypography) 42.dp else 40.dp)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(if (usesExpandedTypography) 20.dp else 19.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (usesExpandedTypography) 52.dp else 48.dp)
                .testTag("search_input_container"),
            shape = RoundedCornerShape(if (usesExpandedTypography) 26.dp else 24.dp),
            color = SEARCH_PANEL_COLOR,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = if (usesExpandedTypography) 13.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = SEARCH_PRIMARY_RED.copy(alpha = 0.82f),
                    modifier = Modifier.size(if (usesExpandedTypography) 20.dp else 19.dp)
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input"),
                    singleLine = true,
                    textStyle = (
                        if (usesExpandedTypography) {
                            MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        }
                    ).copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSubmitSearch() }
                    ),
                    decorationBox = { innerTextField ->
                        if (query.isBlank()) {
                            Text(
                                text = "搜索歌曲 / 歌手 / 专辑",
                                style = if (usesExpandedTypography) {
                                    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchPinnedHistorySection(
    historyKeywords: List<String>,
    onHistoryKeywordClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Column(
        modifier = modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(if (usesExpandedTypography) 12.dp else 10.dp)
    ) {
        SearchSectionTitle(
            title = "历史搜索",
            icon = {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = SEARCH_PRIMARY_RED.copy(alpha = 0.9f),
                    modifier = Modifier.size(17.dp)
                )
            },
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .testTag("search_history_section")
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (usesExpandedTypography) 10.dp else 8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            itemsIndexed(
                items = historyKeywords,
                key = { _, keyword -> keyword }
            ) { index, keyword ->
                SearchHistoryChip(
                    keyword = keyword,
                    modifier = Modifier.testTag("search_history_chip_$index"),
                    onClick = { onHistoryKeywordClick(keyword) }
                )
            }
        }
    }
}

@Composable
private fun SearchHotContent(
    state: SearchHotUiState,
    onHotKeywordClick: (SearchHotKeywordUiModel) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        SearchHotUiState.Loading -> {
            SearchStatusCard(
                title = "热搜加载中",
                subtitle = "正在同步当前热门搜索词。"
            ) {
                CircularProgressIndicator()
            }
        }

        is SearchHotUiState.Error -> {
            SearchStatusCard(
                title = "热搜加载失败",
                subtitle = state.message
            ) {
                RetryButton(onRetry = onRetry)
            }
        }

        is SearchHotUiState.Content -> {
            if (state.items.isEmpty()) {
                SearchStatusCard(
                    title = "暂无热搜内容",
                    subtitle = "稍后再来看看大家都在搜什么。"
                )
                return
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_hot_scroll_container"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hot_section") {
                    SearchSectionTitle(
                        title = "推荐搜索",
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = SEARCH_PRIMARY_RED,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("search_hot_section")
                    )
                }
                item(key = "hot_board") {
                    SearchHotBoard(
                        items = state.items,
                        onHotKeywordClick = onHotKeywordClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestContent(
    state: SearchSuggestUiState,
    onSuggestionClick: (SearchSuggestionUiModel) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        SearchSuggestUiState.Idle -> Unit

        SearchSuggestUiState.Loading -> {
            SearchStatusCard(
                title = "搜索建议加载中",
                subtitle = "正在为你匹配更贴近的关键词。"
            ) {
                CircularProgressIndicator()
            }
        }

        is SearchSuggestUiState.Error -> {
            SearchStatusCard(
                title = "搜索建议加载失败",
                subtitle = state.message
            ) {
                RetryButton(onRetry = onRetry)
            }
        }

        is SearchSuggestUiState.Content -> {
            if (state.items.isEmpty()) {
                SearchStatusCard(
                    title = "暂无搜索建议",
                    subtitle = "继续输入更多关键词，或直接发起搜索。"
                )
                return
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_suggestion_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { _, item -> item.keyword }
                ) { index, item ->
                    SearchSuggestionCard(
                        keyword = item.keyword,
                        modifier = Modifier.testTag("search_suggestion_item_$index"),
                        onClick = { onSuggestionClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultContent(
    selectedResultState: SearchResultUiState,
    resultStatesByType: Map<SearchResultType, SearchResultUiState>,
    selectedResultType: SearchResultType,
    availableResultTypes: List<SearchResultType>,
    onResultTypeSelected: (SearchResultType) -> Unit,
    onResultClick: (SearchRouteTarget) -> Unit,
    onRetry: () -> Unit
) {
    val selectedPageIndex = availableResultTypes.indexOf(selectedResultType).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedPageIndex,
        pageCount = { availableResultTypes.size }
    )

    LaunchedEffect(selectedResultType, availableResultTypes) {
        val pageIndex = availableResultTypes.indexOf(selectedResultType)
        if (pageIndex >= 0 && pagerState.currentPage != pageIndex) {
            pagerState.scrollToPage(pageIndex)
        }
    }

    LaunchedEffect(pagerState, availableResultTypes, selectedResultType) {
        snapshotFlow { pagerState.currentPage }
            .filter { pageIndex -> pageIndex in availableResultTypes.indices }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val type = availableResultTypes[pageIndex]
                if (type != selectedResultType) {
                    onResultTypeSelected(type)
                }
            }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SearchResultTypeRow(
            selectedResultType = selectedResultType,
            availableResultTypes = availableResultTypes,
            onResultTypeSelected = onResultTypeSelected,
            modifier = Modifier.testTag("search_result_type_pinned")
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .testTag("search_result_pager"),
            userScrollEnabled = availableResultTypes.size > 1,
            beyondViewportPageCount = 1
        ) {
            val pageType = availableResultTypes[it]
            val pageState = resultStatesByType[pageType]
                ?: if (pageType == selectedResultType) {
                    selectedResultState
                } else {
                    SearchResultUiState.Idle
                }
            SearchResultPage(
                state = pageState,
                onResultClick = onResultClick,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun SearchResultPage(
    state: SearchResultUiState,
    onResultClick: (SearchRouteTarget) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        SearchResultUiState.Idle -> Unit

        SearchResultUiState.Loading -> {
            SearchStatusCard(
                title = "搜索中",
                subtitle = "正在匹配与你输入相关的结果。"
            ) {
                CircularProgressIndicator()
            }
        }

        SearchResultUiState.Empty -> {
            SearchStatusCard(
                title = "暂无匹配结果",
                subtitle = "换个关键词试试，或检查输入内容。"
            )
        }

        is SearchResultUiState.Error -> {
            SearchStatusCard(
                title = "搜索结果加载失败",
                subtitle = state.message
            ) {
                RetryButton(onRetry = onRetry)
            }
        }

        is SearchResultUiState.Content -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_result_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.items,
                    key = { item -> "${item.resultType.name}_${item.id}" }
                ) { item ->
                    SearchResultCard(
                        item = item,
                        onClick = { onResultClick(item.routeTarget) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultTypeRow(
    selectedResultType: SearchResultType,
    availableResultTypes: List<SearchResultType>,
    onResultTypeSelected: (SearchResultType) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedResultType, availableResultTypes) {
        val selectedIndex = availableResultTypes.indexOf(selectedResultType)
        if (selectedIndex >= 0) {
            listState.centerSelectedTypeItem(selectedIndex)
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_result_type_row"),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        items(
            items = availableResultTypes,
            key = { type -> type.name }
        ) { type ->
            SearchResultTypeChip(
                type = type,
                selected = type == selectedResultType,
                onClick = { onResultTypeSelected(type) }
            )
        }
    }
}

private suspend fun LazyListState.centerSelectedTypeItem(index: Int) {
    val selectedInfoBeforeScroll = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (selectedInfoBeforeScroll == null) {
        animateScrollToItem(index)
    }
    val selectedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = calculateTypeRowCenterScrollDelta(
        viewportStartOffset = layoutInfo.viewportStartOffset,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        itemOffset = selectedInfo.offset,
        itemSize = selectedInfo.size
    )
    if (kotlin.math.abs(delta) > 1f) {
        animateScrollBy(delta)
    }
}

@Composable
private fun SearchResultTypeChip(
    type: SearchResultType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    val scale = animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = tween(durationMillis = 180),
        label = "search_result_type_chip_scale"
    ).value
    Surface(
        modifier = Modifier
            .testTag("search_result_type_${type.name.lowercase()}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SEARCH_PRIMARY_RED.copy(alpha = 0.14f) else SEARCH_PANEL_COLOR.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                SEARCH_PRIMARY_RED.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            }
        )
    ) {
        Text(
            text = type.displayLabel,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = if (usesExpandedTypography) {
                MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (selected) SEARCH_PRIMARY_RED else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun SearchHotBoard(
    items: List<SearchHotKeywordUiModel>,
    onHotKeywordClick: (SearchHotKeywordUiModel) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_hot_board"),
        shape = RoundedCornerShape(22.dp),
        color = SEARCH_PANEL_COLOR,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                SearchHotBoardRow(
                    index = index,
                    item = item,
                    modifier = Modifier.testTag(
                        if (index == 0) "search_hot_list" else "search_hot_item_$index"
                    ),
                    onClick = { onHotKeywordClick(item) }
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 58.dp, end = 14.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHotBoardRow(
    index: Int,
    item: SearchHotKeywordUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    val rankColor = when (index) {
        0 -> SEARCH_PRIMARY_RED
        1 -> Color(0xFFE36A2E)
        2 -> Color(0xFFEF9B2D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingText = item.content.ifBlank {
        item.score.takeIf { it > 0 }?.let { "热度 $it" } ?: ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (usesExpandedTypography) 36.dp else 32.dp)
                .testTag("search_hot_rank_$index"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                style = if (usesExpandedTypography) {
                    MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = rankColor,
                fontWeight = if (index < 3) FontWeight.Bold else FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.keyword,
                style = if (usesExpandedTypography) {
                    MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (index < 3) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supportingText.isNotBlank()) {
                Text(
                    text = supportingText,
                    style = if (usesExpandedTypography) {
                        MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SearchHotBadge(item = item, rankIndex = index)
    }
}

@Composable
private fun SearchHistoryChip(
    keyword: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = SEARCH_PANEL_COLOR.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Text(
            text = keyword,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = if (usesExpandedTypography) {
                MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchSectionTitle(
    title: String,
    icon: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = title,
            style = if (usesExpandedTypography) {
                MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResultUiModel,
    onClick: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    val supportingText = item.supportingText()
    val tertiaryText = item.tertiaryText()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_card_${item.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = SEARCH_PANEL_COLOR.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = SEARCH_PRIMARY_RED.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = SEARCH_PRIMARY_RED
                    )
                }
            } else {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .testTag("search_result_cover_${item.id}")
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = if (usesExpandedTypography) {
                        MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        style = if (usesExpandedTypography) {
                            MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (tertiaryText.isNotBlank()) {
                    Text(
                        text = tertiaryText,
                        style = MaterialTheme.typography.labelMedium,
                        color = SEARCH_PRIMARY_RED.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchStatusCard(
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_status_card"),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = if (usesExpandedTypography) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = if (usesExpandedTypography) {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                action()
            }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    OutlinedButton(
        onClick = onRetry,
        modifier = Modifier.testTag("search_retry_button")
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("重试")
    }
}

@Composable
private fun SearchSuggestionCard(
    keyword: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SEARCH_PANEL_COLOR.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = SEARCH_PRIMARY_RED.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = SEARCH_PRIMARY_RED,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = keyword,
                style = if (usesExpandedTypography) {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchHotBadge(
    item: SearchHotKeywordUiModel,
    rankIndex: Int
) {
    val usesExpandedTypography = usesExpandedSearchTypography()
    if (!item.iconUrl.isNullOrBlank()) {
        AsyncImage(
            model = item.iconUrl,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        return
    }

    val badgeText = when {
        rankIndex == 0 -> "TOP"
        item.iconType > 0 -> "热"
        else -> null
    } ?: return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SEARCH_PRIMARY_RED.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Text(
            text = badgeText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = if (usesExpandedTypography) {
                MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = SEARCH_PRIMARY_RED,
            fontWeight = FontWeight.Bold
        )
    }
}

private val SEARCH_PAGE_BACKGROUND_BRUSH = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFF7F2),
        Color(0xFFFFFBF8),
        Color(0xFFFFFFFF)
    )
)

private val SEARCH_PANEL_COLOR = Color.White
private val SEARCH_PRIMARY_RED = Color(0xFFD33A31)
private fun SearchResultUiModel.supportingText(): String {
    return when (this) {
        is SearchResultUiModel.Song -> listOf(artistText, albumTitle)
            .filter { it.isNotBlank() }
            .joinToString(separator = " · ")

        is SearchResultUiModel.Album -> listOf("专辑", artistText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " · ")

        is SearchResultUiModel.Artist -> "歌手"

        is SearchResultUiModel.Playlist -> listOf("歌单", creatorName)
            .filter { it.isNotBlank() }
            .joinToString(separator = " · ")

        is SearchResultUiModel.Generic -> subtitle
    }
}

private fun SearchResultUiModel.tertiaryText(): String {
    return when (this) {
        is SearchResultUiModel.Song -> ""
        is SearchResultUiModel.Album -> songCount?.let { "共 ${it} 首" }.orEmpty()
        is SearchResultUiModel.Artist -> albumCount?.let { "${it} 张专辑" }.orEmpty()
        is SearchResultUiModel.Playlist -> trackCount?.let { "${it} 首歌曲" }.orEmpty()
        is SearchResultUiModel.Generic -> tertiary
    }
}

internal fun calculateTypeRowCenterScrollDelta(
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    itemOffset: Int,
    itemSize: Int
): Float {
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f
    val itemCenter = itemOffset + (itemSize / 2f)
    return itemCenter - viewportCenter
}
