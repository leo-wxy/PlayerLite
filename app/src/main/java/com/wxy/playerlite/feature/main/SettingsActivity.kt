package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wxy.playerlite.feature.user.AccountCardSurface
import com.wxy.playerlite.feature.user.AccountPageBackground
import com.wxy.playerlite.feature.user.AccountPrimaryButton
import com.wxy.playerlite.feature.user.AccountVisualStyle
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import kotlin.math.ln
import kotlin.math.pow
import kotlin.text.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Unit
    }

    private val audioSourceManifestLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val rawJson = runCatching {
                readAudioSourceManifest(uri)
            }.getOrElse { error ->
                viewModel.showAudioSourceValidationMessage(
                    error.message ?: "本地音源导入失败"
                )
                return@launch
            }
            viewModel.importAudioSourceFromLocalJson(
                rawJson = rawJson,
                displayLabel = uri.toString()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
                BackHandler(onBack = ::finish)
                SettingsScreen(
                    state = state,
                    onBack = ::finish,
                    onLoginClick = {
                        loginLauncher.launch(LoginActivity.createIntent(this@SettingsActivity))
                    },
                    onShowLogoutConfirm = viewModel::showLogoutConfirmation,
                    onDismissLogoutConfirm = viewModel::dismissLogoutConfirmation,
                    onConfirmLogout = viewModel::logout,
                    onRefreshCache = viewModel::refreshCache,
                    onClearCache = viewModel::clearManagedCache,
                    onPlaybackCacheLimitChange = viewModel::updatePendingPlaybackCacheLimitMb,
                    onSavePlaybackCacheLimit = viewModel::savePlaybackCacheLimit,
                    onPreferredAudioQualityChange = viewModel::updatePreferredAudioQuality,
                    onPendingImportUrlChange = viewModel::updatePendingImportUrl,
                    onImportAudioSourceFromUrl = viewModel::importAudioSourceFromUrl,
                    onImportAudioSourceFromLocal = {
                        audioSourceManifestLauncher.launch("*/*")
                    },
                    onSetActiveAudioSource = viewModel::setActiveAudioSource,
                    onRemoveAudioSource = viewModel::removeAudioSource
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    private suspend fun readAudioSourceManifest(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText().trim()
        }?.takeIf { it.isNotBlank() }
            ?: error("无法读取所选音源文件")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onShowLogoutConfirm: () -> Unit,
    onDismissLogoutConfirm: () -> Unit,
    onConfirmLogout: () -> Unit,
    onRefreshCache: () -> Unit,
    onClearCache: () -> Unit,
    onPlaybackCacheLimitChange: (String) -> Unit,
    onSavePlaybackCacheLimit: () -> Unit,
    onPreferredAudioQualityChange: (PlaybackAudioQuality) -> Unit,
    onPendingImportUrlChange: (String) -> Unit,
    onImportAudioSourceFromUrl: () -> Unit,
    onImportAudioSourceFromLocal: () -> Unit,
    onSetActiveAudioSource: (String) -> Unit,
    onRemoveAudioSource: (String) -> Unit
) {
    if (state.accountState.isLogoutConfirmVisible) {
        AlertDialog(
            onDismissRequest = onDismissLogoutConfirm,
            title = { Text("确认退出登录") },
            text = { Text("退出后只会清理当前在线账户状态，本地播放和设置项不会被清空。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissLogoutConfirm()
                        onConfirmLogout()
                    },
                    modifier = Modifier.testTag("settings_logout_confirm")
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissLogoutConfirm) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                }
            )
        }
    ) { innerPadding ->
        AccountPageBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .testTag("settings_scroll_content"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsAccountSection(
                        state = state.accountState,
                        onLoginClick = onLoginClick,
                        onLogoutClick = onShowLogoutConfirm
                    )
                }
                item {
                    SettingsPlaybackPreferencesSection(
                        playbackState = state.playbackPreferencesState,
                        cacheState = state.cacheState,
                        onPlaybackCacheLimitChange = onPlaybackCacheLimitChange,
                        onSavePlaybackCacheLimit = onSavePlaybackCacheLimit,
                        onPreferredAudioQualityChange = onPreferredAudioQualityChange
                    )
                }
                item {
                    SettingsCacheSection(
                        state = state.cacheState,
                        onRefresh = onRefreshCache,
                        onClear = onClearCache
                    )
                }
                item {
                    SettingsAudioSourcesSection(
                        state = state.sourcesState,
                        onImportUrlChange = onPendingImportUrlChange,
                        onImportFromUrl = onImportAudioSourceFromUrl,
                        onImportFromLocal = onImportAudioSourceFromLocal,
                        onSetActiveSource = onSetActiveAudioSource,
                        onRemoveSource = onRemoveAudioSource
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAccountSection(
    state: SettingsAccountUiState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    AccountCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_account_section")
    ) {
        SectionHeader(
            icon = Icons.Rounded.AccountCircle,
            title = "账户",
            subtitle = "登录状态和账号操作"
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isLoggedIn) {
            OutlinedButton(
                onClick = onLogoutClick,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_logout_button"),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("退出登录")
            }
        } else {
            AccountPrimaryButton(
                text = "去登录",
                onClick = onLoginClick,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_login_button")
            )
        }
    }
}

@Composable
private fun SettingsPlaybackPreferencesSection(
    playbackState: SettingsPlaybackPreferencesUiState,
    cacheState: SettingsCacheUiState,
    onPlaybackCacheLimitChange: (String) -> Unit,
    onSavePlaybackCacheLimit: () -> Unit,
    onPreferredAudioQualityChange: (PlaybackAudioQuality) -> Unit
) {
    AccountCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_playback_preferences_section")
    ) {
        SectionHeader(
            icon = Icons.Rounded.Tune,
            title = "播放偏好",
            subtitle = "默认音质和歌曲缓存上限会即时下发到播放进程"
        )
        Text(
            text = "当前默认音质：${playbackState.preferredAudioQuality.displayName}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("settings_playback_quality_current_value")
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                supportedSettingsAudioQualities().forEachIndexed { index, quality ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    val isCurrentQuality = playbackState.preferredAudioQuality == quality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .then(
                                if (isCurrentQuality) {
                                    Modifier
                                } else {
                                    Modifier
                                        .testTag("settings_playback_quality_option_${quality.wireValue}")
                                        .clickable { onPreferredAudioQualityChange(quality) }
                                }
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quality.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (isCurrentQuality) {
                            Text(
                                text = "当前默认",
                                color = AccountVisualStyle.accentTextColor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("settings_playback_quality_current")
                            )
                        } else {
                            OutlinedButton(onClick = { onPreferredAudioQualityChange(quality) }) {
                                Text("设为默认")
                            }
                        }
                    }
                }
            }
        }
        playbackState.feedbackMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier.testTag("settings_playback_quality_feedback")
            )
        }
        Text(
            text = "当前歌曲缓存上限 ${(cacheState.playbackCacheLimitBytes / BYTES_PER_MB)} MB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = cacheState.pendingPlaybackCacheLimitMb,
            onValueChange = onPlaybackCacheLimitChange,
            label = { Text("歌曲缓存上限（MB）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_playback_cache_limit_input")
        )
        cacheState.playbackCacheLimitMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier.testTag("settings_playback_cache_limit_feedback")
            )
        }
        Button(
            onClick = onSavePlaybackCacheLimit,
            enabled = !cacheState.isSavingPlaybackCacheLimit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_playback_cache_limit_save")
        ) {
            if (cacheState.isSavingPlaybackCacheLimit) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("保存缓存上限")
        }
    }
}

@Composable
private fun SettingsCacheSection(
    state: SettingsCacheUiState,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    AccountCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_cache_section")
    ) {
        SectionHeader(
            icon = Icons.Rounded.Storage,
            title = "缓存",
            subtitle = "只管理在线播放缓存和歌词缓存"
        )
        Text(
            text = "总占用 ${formatStorageSize(state.snapshot?.totalBytes ?: 0L)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("settings_cache_total")
        )
        state.snapshot?.entries?.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatStorageSize(entry.bytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.feedbackMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isClearing) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    AccountVisualStyle.accentTextColor
                },
                modifier = Modifier.testTag("settings_cache_feedback")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing && !state.isClearing,
                modifier = Modifier.weight(1f)
            ) {
                Text("刷新")
            }
            Button(
                onClick = onClear,
                enabled = !state.isRefreshing && !state.isClearing,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_clear_cache_button")
            ) {
                if (state.isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("清理缓存")
            }
        }
    }
}

@Composable
private fun SettingsAudioSourcesSection(
    state: SettingsSourcesUiState,
    onImportUrlChange: (String) -> Unit,
    onImportFromUrl: () -> Unit,
    onImportFromLocal: () -> Unit,
    onSetActiveSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit
) {
    AccountCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_audio_sources_section")
    ) {
        SectionHeader(
            icon = Icons.Rounded.AddLink,
            title = "音源",
            subtitle = "支持在线 URL 或本地 JSON 导入，仅支持 netease-compatible"
        )
        Text(
            text = "当前音源：${state.items.firstOrNull { it.isActive }?.displayName ?: "未设置"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = state.pendingImportUrl,
            onValueChange = onImportUrlChange,
            label = { Text("在线导入地址") },
            placeholder = { Text("https://cdn.example.com/source.json") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_audio_source_import_url_input")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onImportFromUrl,
                enabled = !state.isImporting,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_audio_source_import_url_submit")
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("在线导入")
            }
            OutlinedButton(
                onClick = onImportFromLocal,
                enabled = !state.isImporting,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_audio_source_import_local")
            ) {
                Text("导入本地 JSON")
            }
        }
        state.importFeedbackMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier.testTag("settings_audio_source_feedback")
            )
        }
        state.validationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier.testTag("settings_audio_source_validation")
            )
        }
        if (state.items.isEmpty()) {
            Text(
                text = "还没有添加音源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_audio_source_empty_state")
            )
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        AudioSourceRow(
                            item = item,
                            onSetActiveSource = onSetActiveSource,
                            onRemoveSource = onRemoveSource,
                            modifier = Modifier.testTag("settings_audio_source_item_${item.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = AccountVisualStyle.accentColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccountVisualStyle.accentColor
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AudioSourceRow(
    item: ManagedAudioSource,
    onSetActiveSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = item.sourceStatusSummary(),
            style = MaterialTheme.typography.bodySmall,
            color = AccountVisualStyle.accentTextColor
        )
        item.sourceMetadataSummary()?.let { metadata ->
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = item.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        item.importUrl?.let { importUrl ->
            Text(
                text = "导入地址：$importUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        (item.initError ?: item.detailMessage)?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.initError != null) {
                    AccountVisualStyle.accentTextColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isActive) {
                Text(
                    text = "当前音源",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccountVisualStyle.accentTextColor,
                    modifier = Modifier.testTag("settings_audio_source_current_${item.id}")
                )
            } else {
                OutlinedButton(
                    onClick = { onSetActiveSource(item.id) },
                    enabled = item.enabled,
                    modifier = Modifier.testTag("settings_audio_source_activate_${item.id}")
                ) {
                    Text("设为当前")
                }
            }
            if (!item.isBuiltIn) {
                OutlinedButton(
                    onClick = { onRemoveSource(item.id) },
                    modifier = Modifier.testTag("settings_audio_source_remove_${item.id}")
                ) {
                    Text("删除")
                }
            }
        }
    }
}

private fun supportedSettingsAudioQualities(): List<PlaybackAudioQuality> {
    return PlaybackAudioQuality.descendingPreference.filterNot { it == PlaybackAudioQuality.VIVID }
}

private fun ManagedAudioSource.sourceMetadataSummary(): String? {
    return listOfNotNull(author, version?.let { "v$it" }).joinToString(" · ").takeIf { it.isNotBlank() }
}

private fun ManagedAudioSource.sourceStatusSummary(): String {
    return buildList {
        add(if (isBuiltIn) "内置源" else "已导入")
        add(resolverType.displayName)
        add(if (enabled) "已启用" else "已禁用")
        if (isActive) {
            add("当前音源")
        }
    }.joinToString(" · ")
}

private fun formatStorageSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) {
        return "${safeBytes} B"
    }
    val units = listOf("KB", "MB", "GB", "TB")
    val digitGroup = (ln(safeBytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size)
    val unitBase = 1024.0.pow(digitGroup.toDouble())
    val value = safeBytes / unitBase
    val unit = units[digitGroup - 1]
    return String.format("%.1f %s", value, unit)
}
