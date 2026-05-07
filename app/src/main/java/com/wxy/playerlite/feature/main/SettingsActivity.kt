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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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

private val SettingsPageBackground = Color(0xFFF7F3F0)
private val SettingsListSurface = Color(0xFFFFFDFB)
private val SettingsInlineSurface = Color(0xFFFBF7F4)
private val SettingsBorder = Color(0x17261D1A)
private val SettingsDivider = Color(0x14261D1A)
private val SettingsGreenText = Color(0xFF237653)
private val SettingsGreenSurface = Color(0x1A1F8758)

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
                    onShowPreferredAudioQualityDialog = viewModel::showPreferredAudioQualityDialog,
                    onDismissPreferredAudioQualityDialog =
                        viewModel::dismissPreferredAudioQualityDialog,
                    onPreferredAudioQualityChange = viewModel::updatePreferredAudioQuality,
                    onRestoreLastPlaybackOnStartupChange =
                        viewModel::updateRestoreLastPlaybackOnStartup,
                    onResumeFromLastPositionChange = viewModel::updateResumeFromLastPosition,
                    onWeakNetworkAutoRetryChange = viewModel::updateWeakNetworkAutoRetry,
                    onShowCacheFailureNotificationsChange =
                        viewModel::updateShowCacheFailureNotifications,
                    onPlaybackPrewarmEnabledChange = viewModel::updatePlaybackPrewarmEnabled,
                    onPlaybackPrewarmBudgetChange = viewModel::updatePlaybackPrewarmBudget,
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
    onShowPreferredAudioQualityDialog: () -> Unit = {},
    onDismissPreferredAudioQualityDialog: () -> Unit = {},
    onPreferredAudioQualityChange: (PlaybackAudioQuality) -> Unit,
    onRestoreLastPlaybackOnStartupChange: (Boolean) -> Unit = {},
    onResumeFromLastPositionChange: (Boolean) -> Unit = {},
    onWeakNetworkAutoRetryChange: (Boolean) -> Unit = {},
    onShowCacheFailureNotificationsChange: (Boolean) -> Unit = {},
    onPlaybackPrewarmEnabledChange: (Boolean) -> Unit = {},
    onPlaybackPrewarmBudgetChange: (PlaybackPrewarmBudgetPreset) -> Unit = {},
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
    if (state.playbackPreferencesState.isPreferredAudioQualityDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissPreferredAudioQualityDialog,
            title = { Text("选择默认音质") },
            text = {
                Surface(
                    modifier = Modifier.testTag("settings_playback_quality_dialog"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        supportedSettingsAudioQualities().forEachIndexed { index, quality ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            val isCurrentQuality =
                                state.playbackPreferencesState.preferredAudioQuality == quality
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        "settings_playback_quality_dialog_option_${quality.wireValue}"
                                    )
                                    .clickable(enabled = !isCurrentQuality) {
                                        onPreferredAudioQualityChange(quality)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (isCurrentQuality) "当前默认" else "点击切换",
                                    color = if (isCurrentQuality) {
                                        AccountVisualStyle.accentTextColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = onDismissPreferredAudioQualityDialog) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SettingsPageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SettingsPageBackground
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsPageBackground)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .testTag("settings_scroll_content"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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
                        onShowPreferredAudioQualityDialog = onShowPreferredAudioQualityDialog,
                        onRestoreLastPlaybackOnStartupChange =
                            onRestoreLastPlaybackOnStartupChange,
                        onResumeFromLastPositionChange = onResumeFromLastPositionChange,
                        onWeakNetworkAutoRetryChange = onWeakNetworkAutoRetryChange,
                        onShowCacheFailureNotificationsChange =
                            onShowCacheFailureNotificationsChange,
                        onPlaybackPrewarmEnabledChange = onPlaybackPrewarmEnabledChange,
                        onPlaybackPrewarmBudgetChange = onPlaybackPrewarmBudgetChange
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
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = SettingsListSurface,
            border = BorderStroke(1.dp, SettingsBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsAccountSection(
    state: SettingsAccountUiState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    SettingsGroup(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_account_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = AccountVisualStyle.accentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.title.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("settings_account_title")
                )
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.isLoggedIn) {
                OutlinedButton(
                    onClick = onLogoutClick,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("settings_logout_button"),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("退出")
                }
            } else {
                Button(
                    onClick = onLoginClick,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("settings_login_button"),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccountVisualStyle.accentColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text("登录")
                }
            }
        }
    }
}

@Composable
private fun SettingsPlaybackPreferencesSection(
    playbackState: SettingsPlaybackPreferencesUiState,
    cacheState: SettingsCacheUiState,
    onPlaybackCacheLimitChange: (String) -> Unit,
    onSavePlaybackCacheLimit: () -> Unit,
    onShowPreferredAudioQualityDialog: () -> Unit,
    onRestoreLastPlaybackOnStartupChange: (Boolean) -> Unit,
    onResumeFromLastPositionChange: (Boolean) -> Unit,
    onWeakNetworkAutoRetryChange: (Boolean) -> Unit,
    onShowCacheFailureNotificationsChange: (Boolean) -> Unit,
    onPlaybackPrewarmEnabledChange: (Boolean) -> Unit,
    onPlaybackPrewarmBudgetChange: (PlaybackPrewarmBudgetPreset) -> Unit
) {
    val behaviorPreferences = playbackState.behaviorPreferences
    val cachePolicyPreferences = playbackState.cachePolicyPreferences
    val prewarmPreferences = playbackState.prewarmPreferences.sanitized()
    val prewarmPreset = PlaybackPrewarmBudgetPreset.fromPreferences(prewarmPreferences)
    SettingsGroup(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_playback_preferences_section"),
        title = "播放与缓存"
    ) {
        SettingsRow(
            title = "默认音质",
            subtitle = "用于在线播放解析和起播",
            value = playbackState.preferredAudioQuality.displayName,
            valueTestTag = "settings_playback_quality_current_value",
            modifier = Modifier.testTag("settings_playback_quality_trigger"),
            enabled = !playbackState.isSavingPreferredAudioQuality,
            onClick = onShowPreferredAudioQualityDialog
        )
        SettingsDividerLine()
        SettingsRow(
            title = "歌曲缓存上限",
            subtitle = "只影响在线播放缓存",
            value = "${cacheState.playbackCacheLimitBytes / BYTES_PER_MB} MB"
        )
        SettingsDividerLine()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsInlineSurface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "调整缓存上限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = cacheState.pendingPlaybackCacheLimitMb,
                onValueChange = onPlaybackCacheLimitChange,
                label = { Text("歌曲缓存上限（MB）") },
                singleLine = true,
                shape = RoundedCornerShape(9.dp),
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
            playbackState.feedbackMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccountVisualStyle.accentTextColor,
                    modifier = Modifier.testTag("settings_playback_quality_feedback")
                )
            }
            Button(
                onClick = onSavePlaybackCacheLimit,
                enabled = !cacheState.isSavingPlaybackCacheLimit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_playback_cache_limit_save"),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountVisualStyle.accentColor,
                    contentColor = Color.White
                )
            ) {
                if (cacheState.isSavingPlaybackCacheLimit) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("保存")
            }
        }
        SettingsDividerLine()
        SettingsSwitchRow(
            title = "启动后恢复上次播放",
            subtitle = "冷启动后恢复最近队列和当前歌曲",
            checked = behaviorPreferences.restoreLastPlaybackOnStartup,
            onCheckedChange = onRestoreLastPlaybackOnStartupChange,
            modifier = Modifier.testTag("settings_restore_last_playback_switch"),
            switchTestTag = "settings_restore_last_playback_switch_control"
        )
        SettingsDividerLine()
        SettingsSwitchRow(
            title = "断点续播",
            subtitle = "恢复上次歌曲时跳到记录进度",
            checked = behaviorPreferences.resumeFromLastPosition,
            onCheckedChange = onResumeFromLastPositionChange,
            modifier = Modifier.testTag("settings_resume_from_last_position_switch"),
            switchTestTag = "settings_resume_from_last_position_switch_control"
        )
        SettingsDividerLine()
        SettingsSwitchRow(
            title = "缓存失败时提示",
            subtitle = "缓存写入失败时提示，不和歌曲播放失败混淆",
            checked = cachePolicyPreferences.showCacheFailureNotifications,
            onCheckedChange = onShowCacheFailureNotificationsChange,
            modifier = Modifier.testTag("settings_cache_failure_notice_switch"),
            switchTestTag = "settings_cache_failure_notice_switch_control"
        )
        SettingsDividerLine()
        SettingsSwitchRow(
            title = "在线播放预热",
            subtitle = "有限缓存当前后续片段和下一首首段，不等同整首下载",
            checked = prewarmPreferences.enabled,
            onCheckedChange = onPlaybackPrewarmEnabledChange,
            modifier = Modifier.testTag("settings_playback_prewarm_switch"),
            switchTestTag = "settings_playback_prewarm_switch_control"
        )
        SettingsDividerLine()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsInlineSurface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "预热预算",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatPrewarmBudgetSummary(prewarmPreferences),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_playback_prewarm_budget_summary")
                    )
                }
                Text(
                    text = prewarmPreset.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = AccountVisualStyle.accentTextColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaybackPrewarmBudgetPreset.entries.forEach { preset ->
                    OutlinedButton(
                        onClick = { onPlaybackPrewarmBudgetChange(preset) },
                        enabled = preset != prewarmPreset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_playback_prewarm_budget_${preset.name.lowercase()}"),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(preset.displayName)
                    }
                }
            }
        }
        SettingsDividerLine()
        SettingsRow(
            title = "缓存清理策略",
            subtitle = "超过容量上限时自动清理",
            value = "最近使用优先保留",
            valueTestTag = "settings_cache_cleanup_policy_value",
            modifier = Modifier.testTag("settings_cache_cleanup_policy")
        )
        SettingsDividerLine()
        SettingsSwitchRow(
            title = "弱网自动重试",
            subtitle = "在线播放失败时自动重新解析和起播",
            checked = behaviorPreferences.weakNetworkAutoRetry,
            onCheckedChange = onWeakNetworkAutoRetryChange,
            modifier = Modifier.testTag("settings_weak_network_retry_switch"),
            switchTestTag = "settings_weak_network_retry_switch_control"
        )
    }
}

private fun formatPrewarmBudgetSummary(
    preferences: com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
): String {
    return "${preferences.budgetDurationMs / 1000} 秒 / ${preferences.budgetBytes / BYTES_PER_MB} MB"
}

@Composable
private fun SettingsCacheSection(
    state: SettingsCacheUiState,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    SettingsGroup(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_cache_section"),
        title = "缓存明细"
    ) {
        SettingsRow(
            title = "总占用",
            subtitle = "歌曲缓存和歌词缓存",
            value = formatStorageSize(state.snapshot?.totalBytes ?: 0L),
            modifier = Modifier.testTag("settings_cache_total")
        )
        state.snapshot?.entries?.forEach { entry ->
            SettingsDividerLine()
            SettingsRow(
                title = entry.label,
                subtitle = if (entry.kind == ManagedCacheKind.PLAYBACK) {
                    "Range 边播边缓存"
                } else {
                    "已保存的歌词资源"
                },
                value = formatStorageSize(entry.bytes)
            )
        }
        SettingsDividerLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsInlineSurface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing && !state.isClearing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(9.dp)
            ) {
                Text("刷新")
            }
            Button(
                onClick = onClear,
                enabled = !state.isRefreshing && !state.isClearing,
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_clear_cache_button"),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccountVisualStyle.accentColor,
                    contentColor = Color.White
                )
            ) {
                if (state.isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("清理")
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
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("settings_cache_feedback")
            )
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
    SettingsGroup(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_audio_sources_section"),
        title = "音源"
    ) {
        val activeSource = state.items.firstOrNull { it.isActive }
        SettingsRow(
            title = "当前音源",
            subtitle = activeSource?.sourceStatusSummary() ?: "导入或启用音源后用于在线播放解析",
            value = activeSource?.displayName ?: "未设置",
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag("settings_audio_source_current_summary")
        )
        SettingsDividerLine()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SettingsInlineSurface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "导入音源",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = state.pendingImportUrl,
                onValueChange = onImportUrlChange,
                label = { Text("在线导入地址") },
                placeholder = { Text("https://cdn.example.com/source.json") },
                singleLine = true,
                shape = RoundedCornerShape(9.dp),
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
                        .testTag("settings_audio_source_import_url_submit"),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccountVisualStyle.accentColor,
                        contentColor = Color.White
                    )
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
                        .testTag("settings_audio_source_import_local"),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text("本地 JSON")
                }
            }
        }
        state.importFeedbackMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("settings_audio_source_feedback")
            )
        }
        state.validationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = AccountVisualStyle.accentTextColor,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("settings_audio_source_validation")
            )
        }
        if (state.items.isEmpty()) {
            SettingsDividerLine()
            Text(
                text = "还没有添加音源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .testTag("settings_audio_source_empty_state")
            )
        } else {
            state.items.forEach { item ->
                SettingsDividerLine()
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

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchTestTag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchTestTag?.let { Modifier.testTag(it) } ?: Modifier
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    if (value != null) {
        val valueModifier = Modifier.widthIn(max = 132.dp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = valueTestTag?.let { valueModifier.testTag(it) } ?: valueModifier
        )
    }
        if (onClick != null) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDividerLine() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = SettingsDivider
    )
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
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.sourceStatusSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isActive) {
                Surface(
                    color = SettingsGreenSurface,
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = SettingsGreenText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("settings_audio_source_current_${item.id}")
                    )
                }
            }
        }
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.isActive) {
                OutlinedButton(
                    onClick = { onSetActiveSource(item.id) },
                    enabled = item.enabled,
                    modifier = Modifier.testTag("settings_audio_source_activate_${item.id}"),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text("设为当前")
                }
            }
            if (!item.isBuiltIn) {
                OutlinedButton(
                    onClick = { onRemoveSource(item.id) },
                    modifier = Modifier.testTag("settings_audio_source_remove_${item.id}"),
                    shape = RoundedCornerShape(9.dp)
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
