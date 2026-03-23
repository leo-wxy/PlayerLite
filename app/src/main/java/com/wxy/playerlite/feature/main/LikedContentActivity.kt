package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class LikedContentActivity : ComponentActivity() {
    private val viewModel: LikedContentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                LikedContentScreen(
                    state = state,
                    onBack = ::finish,
                    onSelectTab = viewModel::selectTab,
                    onLoginClick = {
                        startActivity(LoginActivity.createIntent(this@LikedContentActivity))
                    },
                    onRetry = viewModel::retry,
                    onItemClick = ::handleContentEntryAction
                )
            }
        }
    }

    private fun handleContentEntryAction(action: ContentEntryAction) {
        val launch = resolveContentEntryLaunch(
            context = this,
            action = action
        )
        val intent = launch.intent
        if (intent == null) {
            launch.failureMessage?.let(::showMessage)
            return
        }
        val canLaunch = intent.component != null || intent.resolveActivity(packageManager) != null
        if (!canLaunch) {
            showMessage(launch.failureMessage ?: "当前内容暂时无法打开")
            return
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            showMessage(launch.failureMessage ?: "当前内容暂时无法打开")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LikedContentActivity::class.java)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LikedContentScreen(
    state: LikedContentUiState,
    onBack: () -> Unit,
    onSelectTab: (LikedContentTab) -> Unit,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (ContentEntryAction) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    title = { Text("喜欢") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        if (state.isLoggedIn) {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("liked_content_retry_action")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "刷新"
                                )
                            }
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    modifier = Modifier.testTag("liked_content_tabs")
                ) {
                    LikedContentTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == state.selectedTab,
                            onClick = { onSelectTab(tab) },
                            text = {
                                Text(
                                    text = tab.label,
                                    modifier = Modifier.testTag("liked_content_tab_${tab.name.lowercase()}")
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            !state.isLoggedIn -> {
                LikedContentLoginState(
                    onLoginClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("liked_content_login_state")
                )
            }

            state.currentState is LikedTabContentState.Loading || state.currentState is LikedTabContentState.Idle -> {
                LikedContentLoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("liked_content_loading_state")
                )
            }

            state.currentState is LikedTabContentState.Empty -> {
                LikedContentStatusState(
                    title = "${state.selectedTab.label}为空",
                    subtitle = "当前账号还没有可展示的${state.selectedTab.label}内容。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("liked_content_empty_state")
                )
            }

            state.currentState is LikedTabContentState.Error -> {
                LikedContentStatusState(
                    title = "${state.selectedTab.label}加载失败",
                    subtitle = (state.currentState as LikedTabContentState.Error).message,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("liked_content_error_state")
                ) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("liked_content_retry_button")
                    ) {
                        Text("重试")
                    }
                }
            }

            state.currentState is LikedTabContentState.Content -> {
                val items = (state.currentState as LikedTabContentState.Content).items
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("liked_content_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> "${state.selectedTab.name}_${item.id}" }
                    ) { _, item ->
                        LikedContentRow(
                            item = item,
                            onClick = { onItemClick(item.action) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("liked_content_item_${item.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedContentRow(
    item: UserCenterCollectionItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.meta?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedContentLoginState(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LikedContentStatusState(
        title = "登录后查看喜欢内容",
        subtitle = "歌手、MV 和专栏收藏都需要登录后才能同步。",
        modifier = modifier
    ) {
        Button(
            onClick = onLoginClick,
            modifier = Modifier.testTag("liked_content_login_button")
        ) {
            Text("去登录")
        }
    }
}

@Composable
private fun LikedContentLoadingState(
    modifier: Modifier = Modifier
) {
    LikedContentStatusState(
        title = "正在加载喜欢内容",
        subtitle = "正在同步当前账号的收藏数据，请稍候。",
        modifier = modifier
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LikedContentStatusState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            action?.invoke()
        }
    }
}
