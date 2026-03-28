package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedOutState_shouldShowLoginAndKeepPlaybackSectionsVisible() {
        composeRule.setContent {
            PlayerLiteTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        cacheState = SettingsCacheUiState(
                            snapshot = ManagedCacheSnapshot(
                                totalBytes = 0L
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onShowLogoutConfirm = {},
                    onDismissLogoutConfirm = {},
                    onConfirmLogout = {},
                    onRefreshCache = {},
                    onClearCache = {},
                    onPlaybackCacheLimitChange = {},
                    onSavePlaybackCacheLimit = {},
                    onPreferredAudioQualityChange = {},
                    onPendingImportUrlChange = {},
                    onImportAudioSourceFromUrl = {},
                    onImportAudioSourceFromLocal = {},
                    onSetActiveAudioSource = {},
                    onRemoveAudioSource = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_account_section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_login_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_playback_preferences_section")
        )
        composeRule.onNodeWithTag("settings_playback_preferences_section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_cache_section")
        )
        composeRule.onNodeWithTag("settings_cache_section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_audio_sources_section")
        )
        composeRule.onNodeWithTag("settings_audio_sources_section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_audio_source_empty_state").assertIsDisplayed()
    }

    @Test
    fun loggedInState_shouldRenderLogoutCacheFeedbackAndManagedSources() {
        composeRule.setContent {
            PlayerLiteTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        accountState = SettingsAccountUiState(
                            isLoggedIn = true,
                            title = "Codex",
                            summary = "在线账户 · Lv.9"
                        ),
                        cacheState = SettingsCacheUiState(
                            snapshot = ManagedCacheSnapshot(
                                totalBytes = 16L,
                                entries = listOf(
                                    ManagedCacheEntry(
                                        kind = ManagedCacheKind.PLAYBACK,
                                        label = "在线播放缓存",
                                        bytes = 10L
                                    ),
                                    ManagedCacheEntry(
                                        kind = ManagedCacheKind.LYRICS,
                                        label = "歌词缓存",
                                        bytes = 6L
                                    )
                                )
                            ),
                            feedbackMessage = "缓存已清理"
                        ),
                        sourcesState = SettingsSourcesUiState(
                            items = listOf(
                                ManagedAudioSource(
                                    id = "source-1",
                                    displayName = "LX Mirror",
                                    baseUrl = "https://example.com/api",
                                    kind = ManagedAudioSourceKind.CUSTOM,
                                    enabled = true,
                                    author = "Codex",
                                    version = "1.0.0",
                                    importUrl = "https://cdn.example.com/lx.json",
                                    isActive = true,
                                    addedAtMs = 123L
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onShowLogoutConfirm = {},
                    onDismissLogoutConfirm = {},
                    onConfirmLogout = {},
                    onRefreshCache = {},
                    onClearCache = {},
                    onPlaybackCacheLimitChange = {},
                    onSavePlaybackCacheLimit = {},
                    onPreferredAudioQualityChange = {},
                    onPendingImportUrlChange = {},
                    onImportAudioSourceFromUrl = {},
                    onImportAudioSourceFromLocal = {},
                    onSetActiveAudioSource = {},
                    onRemoveAudioSource = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_logout_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_cache_total")
        )
        composeRule.onNodeWithTag("settings_cache_total").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_cache_feedback").assertTextContains("缓存已清理")
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_audio_source_item_source-1")
        )
        composeRule.onNodeWithTag("settings_audio_source_item_source-1").assertIsDisplayed()
        composeRule.onNodeWithTag(
            testTag = "settings_audio_source_current_source-1",
            useUnmergedTree = true
        ).assertTextContains("当前音源")
    }

    @Test
    fun actionCallbacks_shouldDispatchFromSettingsScreen() {
        var logoutClicks = 0
        var importUrlClicks = 0
        var importLocalClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        accountState = SettingsAccountUiState(
                            isLoggedIn = true,
                            title = "Codex",
                            summary = "在线账户"
                        ),
                        cacheState = SettingsCacheUiState(
                            pendingPlaybackCacheLimitMb = "512"
                        ),
                        sourcesState = SettingsSourcesUiState(
                            items = listOf(
                                ManagedAudioSource(
                                    id = "source-1",
                                    displayName = "LX Mirror",
                                    baseUrl = "https://mirror.example.com",
                                    kind = ManagedAudioSourceKind.CUSTOM,
                                    enabled = true,
                                    addedAtMs = 123L
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onShowLogoutConfirm = { logoutClicks += 1 },
                    onDismissLogoutConfirm = {},
                    onConfirmLogout = {},
                    onRefreshCache = {},
                    onClearCache = {},
                    onPlaybackCacheLimitChange = {},
                    onSavePlaybackCacheLimit = {},
                    onPreferredAudioQualityChange = {},
                    onPendingImportUrlChange = {},
                    onImportAudioSourceFromUrl = { importUrlClicks += 1 },
                    onImportAudioSourceFromLocal = { importLocalClicks += 1 },
                    onSetActiveAudioSource = {},
                    onRemoveAudioSource = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_logout_button").performClick()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_playback_quality_option_lossless")
        )
        composeRule.onNodeWithTag("settings_playback_quality_option_lossless")
            .assertHasClickAction()
        composeRule.onNodeWithTag("settings_playback_cache_limit_save")
            .assertHasClickAction()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_audio_source_import_url_input")
        )
        composeRule.onNodeWithTag("settings_audio_source_import_url_submit").performClick()
        composeRule.onNodeWithTag("settings_audio_source_import_local").performClick()
        composeRule.onNodeWithTag("settings_scroll_content").performScrollToNode(
            matcher = hasTestTag("settings_audio_source_activate_source-1")
        )
        composeRule.onNodeWithTag("settings_audio_source_activate_source-1")
            .assertHasClickAction()
        composeRule.onNodeWithTag("settings_audio_source_remove_source-1")
            .assertHasClickAction()

        composeRule.runOnIdle {
            assertEquals(1, logoutClicks)
            assertEquals(1, importUrlClicks)
            assertEquals(1, importLocalClicks)
        }
    }
}
