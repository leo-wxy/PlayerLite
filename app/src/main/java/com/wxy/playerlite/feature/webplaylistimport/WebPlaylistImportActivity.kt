package com.wxy.playerlite.feature.webplaylistimport

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class WebPlaylistImportActivity : ComponentActivity() {
    private val viewModel: WebPlaylistImportViewModel by viewModels {
        WebPlaylistImportViewModel.factory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                WebPlaylistImportScreen(
                    state = state,
                    onBack = ::finish,
                    onUrlChanged = viewModel::onUrlChanged,
                    onSubmit = viewModel::submitUrl,
                    onOpenLogin = {
                        startActivity(LoginActivity.createIntent(this@WebPlaylistImportActivity))
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, WebPlaylistImportActivity::class.java)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebPlaylistImportScreen(
    state: WebPlaylistImportUiState,
    onBack: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("导入歌单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val stage = state.stage) {
            WebPlaylistImportStage.Input -> {
                WebPlaylistImportInputContent(
                    state = state,
                    innerPadding = innerPadding,
                    onUrlChanged = onUrlChanged,
                    onSubmit = onSubmit
                )
            }

            WebPlaylistImportStage.LoginRequired -> {
                WebPlaylistImportLoginRequiredContent(
                    innerPadding = innerPadding,
                    onOpenLogin = onOpenLogin
                )
            }

            is WebPlaylistImportStage.Loading -> {
                WebPlaylistImportLoadingContent(
                    innerPadding = innerPadding,
                    message = stage.message
                )
            }

            is WebPlaylistImportStage.Preview -> {
                WebPlaylistImportPreviewContent(
                    innerPadding = innerPadding,
                    snapshot = stage.snapshot
                )
            }

            is WebPlaylistImportStage.Error -> {
                WebPlaylistImportErrorContent(
                    innerPadding = innerPadding,
                    title = stage.title,
                    message = stage.message,
                    onRetry = onSubmit
                )
            }
        }
    }
}

@Composable
private fun WebPlaylistImportInputContent(
    state: WebPlaylistImportUiState,
    innerPadding: PaddingValues,
    onUrlChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "粘贴网页歌单链接",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "当前支持网易云歌单和 QQ 音乐歌单。第一版会先读取歌单信息，再进入导入预览。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.inputUrl,
                    onValueChange = onUrlChanged,
                    label = { Text("歌单网页地址") },
                    placeholder = { Text("https://music.163.com/#/playlist?id=...") },
                    supportingText = {
                        state.inputErrorMessage?.let { Text(text = it) }
                    },
                    isError = state.inputErrorMessage != null,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_playlist_import_url_field")
                )
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_playlist_import_submit")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始解析")
                }
            }
        }
    }
}

@Composable
private fun WebPlaylistImportLoginRequiredContent(
    innerPadding: PaddingValues,
    onOpenLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .testTag("web_playlist_import_login_required"),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "导入前需要登录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "当前导入流程需要在线搜索和播放能力，先完成登录后再继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onOpenLogin,
                    modifier = Modifier.testTag("web_playlist_import_login_button")
                ) {
                    Text("去登录")
                }
            }
        }
    }
}

@Composable
private fun WebPlaylistImportLoadingContent(
    innerPadding: PaddingValues,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .testTag("web_playlist_import_loading"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun WebPlaylistImportPreviewContent(
    innerPadding: PaddingValues,
    snapshot: ImportedPlaylistSnapshot
) {
    val summary = snapshot.summary
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .testTag("web_playlist_import_preview"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = snapshot.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("web_playlist_import_preview_title")
                    )
                    Text(
                        text = "${snapshot.source.wireValue} · ${snapshot.creatorName.ifBlank { "未知创建者" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (snapshot.description.isNotBlank()) {
                        Text(
                            text = snapshot.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImportSummaryRow(
                    tag = "web_playlist_import_summary_total",
                    label = "总曲目数",
                    value = summary.totalCount.toString()
                )
                ImportSummaryRow(
                    tag = "web_playlist_import_summary_direct",
                    label = "直接可导入",
                    value = summary.directCount.toString()
                )
                ImportSummaryRow(
                    tag = "web_playlist_import_summary_matched",
                    label = "匹配成功",
                    value = summary.matchedCount.toString()
                )
                ImportSummaryRow(
                    tag = "web_playlist_import_summary_ambiguous",
                    label = "存在歧义",
                    value = summary.ambiguousCount.toString()
                )
                ImportSummaryRow(
                    tag = "web_playlist_import_summary_unmatched",
                    label = "未匹配",
                    value = summary.unmatchedCount.toString()
                )
            }
        }
        itemsIndexed(
            items = snapshot.tracks,
            key = { index, item -> item.sourceTrackId ?: "${item.title}-$index" }
        ) { index, track ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${index + 1}. ${track.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = track.artistText.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (track.albumTitle.isNotBlank()) {
                        Text(
                            text = track.albumTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = track.resolution.asLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSummaryRow(
    tag: String,
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun WebPlaylistImportErrorContent(
    innerPadding: PaddingValues,
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .testTag("web_playlist_import_error"),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("web_playlist_import_retry")
                ) {
                    Text("重试")
                }
            }
        }
    }
}

private fun ImportedTrackResolution.asLabel(): String {
    return when (this) {
        is ImportedTrackResolution.Direct -> "可直接导入"
        is ImportedTrackResolution.Matched -> "已匹配"
        is ImportedTrackResolution.Ambiguous -> "存在歧义"
        ImportedTrackResolution.Unmatched -> "未匹配"
    }
}
