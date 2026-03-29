package com.wxy.playerlite.feature.main

import android.app.Application
import com.wxy.playerlite.test.MainDispatcherRule
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loggedIn_shouldExposeAccountSummaryAndLoadSections() = runTest {
        val userRepository = FakeSettingsUserRepository(
            initialState = LoginState.LoggedIn(settingsSession())
        )
        val cacheRepository = FakeSettingsCacheRepository(
            snapshot = ManagedCacheSnapshot(
                totalBytes = 16L,
                entries = listOf(
                    ManagedCacheEntry(ManagedCacheKind.PLAYBACK, "在线播放缓存", 10L),
                    ManagedCacheEntry(ManagedCacheKind.LYRICS, "歌词缓存", 6L)
                )
            )
        )
        val audioSourceRepository = FakeAudioSourceRepository().apply {
            addInitialSource(
                ManagedAudioSource(
                    id = "source-1",
                    displayName = "自定义源",
                    baseUrl = "https://example.com/api",
                    kind = ManagedAudioSourceKind.CUSTOM,
                    enabled = true,
                    addedAtMs = 123L
                )
            )
        }
        val clearController = FakeSettingsCacheController()

        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = userRepository,
            cacheRepository = cacheRepository,
            cacheController = clearController,
            audioSourceRepository = audioSourceRepository,
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.accountState.isLoggedIn)
        assertEquals("Codex", state.accountState.title)
        assertEquals(16L, state.cacheState.snapshot?.totalBytes)
        assertEquals(1, state.sourcesState.items.size)
    }

    @Test
    fun logout_shouldDelegateToUserRepositoryAndSyncState() = runTest {
        val userRepository = FakeSettingsUserRepository(
            initialState = LoginState.LoggedIn(settingsSession())
        )
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = userRepository,
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, userRepository.logoutCount)
        assertTrue(!viewModel.uiStateFlow.value.accountState.isLoggedIn)
    }

    @Test
    fun clearManagedCache_shouldRefreshSnapshotAndExposeSuccessMessage() = runTest {
        val cacheRepository = FakeSettingsCacheRepository(
            snapshot = ManagedCacheSnapshot(
                totalBytes = 16L,
                entries = listOf(
                    ManagedCacheEntry(ManagedCacheKind.PLAYBACK, "在线播放缓存", 10L),
                    ManagedCacheEntry(ManagedCacheKind.LYRICS, "歌词缓存", 6L)
                )
            ),
            refreshedSnapshot = ManagedCacheSnapshot(
                totalBytes = 0L,
                entries = listOf(
                    ManagedCacheEntry(ManagedCacheKind.PLAYBACK, "在线播放缓存", 0L),
                    ManagedCacheEntry(ManagedCacheKind.LYRICS, "歌词缓存", 0L)
                )
            )
        )
        val clearController = FakeSettingsCacheController(clearAccepted = true)
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = cacheRepository,
            cacheController = clearController,
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.clearManagedCache()
        advanceUntilIdle()

        assertEquals(1, clearController.clearRequests)
        assertEquals(0L, viewModel.uiStateFlow.value.cacheState.snapshot?.totalBytes)
        assertEquals("缓存已清理", viewModel.uiStateFlow.value.cacheState.feedbackMessage)
    }

    @Test
    fun clearManagedCache_whenPlaybackCacheClearsLater_shouldWaitForFreshSnapshot() = runTest {
        var playbackCleared = false
        var lyricsCleared = false
        val cacheRepository = DeferredPlaybackClearCacheRepository(
            playbackCleared = { playbackCleared },
            lyricsCleared = { lyricsCleared },
            onLyricsCleared = { lyricsCleared = true }
        )
        val clearController = DelayedSettingsCacheController(
            testScope = backgroundScope,
            onPlaybackCleared = { playbackCleared = true }
        )
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = cacheRepository,
            cacheController = clearController,
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.clearManagedCache()
        advanceUntilIdle()

        assertEquals(1, clearController.clearRequests)
        assertEquals(0L, viewModel.uiStateFlow.value.cacheState.snapshot?.totalBytes)
        assertEquals("缓存已清理", viewModel.uiStateFlow.value.cacheState.feedbackMessage)
    }

    @Test
    fun addAudioSource_shouldAppendToListAndClearFormError() = runTest {
        val audioSourceRepository = FakeAudioSourceRepository()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = audioSourceRepository,
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.updatePendingSourceName("自定义源")
        viewModel.updatePendingSourceBaseUrl("https://example.com/api")
        viewModel.addAudioSource()
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value.sourcesState
        assertEquals(1, state.items.size)
        assertEquals("自定义源", state.items.single().displayName)
        assertEquals(null, state.validationMessage)
    }

    @Test
    fun importAudioSourceFromUrl_shouldAppendToListClearInputAndShowFeedback() = runTest {
        val audioSourceRepository = FakeAudioSourceRepository()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = audioSourceRepository,
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.updatePendingImportUrl("https://cdn.example.com/lx.json")
        viewModel.importAudioSourceFromUrl()
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value.sourcesState
        assertEquals(1, state.items.size)
        assertEquals("https://cdn.example.com/lx.json", state.items.single().importUrl)
        assertEquals("", state.pendingImportUrl)
        assertEquals("音源已导入", state.importFeedbackMessage)
        assertNull(state.validationMessage)
    }

    @Test
    fun init_shouldLoadPlaybackPreferences() = runTest {
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository(
            preferredAudioQuality = PlaybackAudioQuality.HIRES,
            playbackCacheLimitBytes = 1024L * BYTES_PER_MB
        )
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = preferencesRepository,
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        assertEquals(
            PlaybackAudioQuality.HIRES,
            viewModel.uiStateFlow.value.playbackPreferencesState.preferredAudioQuality
        )
        assertEquals(
            1024L * BYTES_PER_MB,
            viewModel.uiStateFlow.value.cacheState.playbackCacheLimitBytes
        )
        assertEquals(
            "1024",
            viewModel.uiStateFlow.value.cacheState.pendingPlaybackCacheLimitMb
        )
    }

    @Test
    fun updatePreferredAudioQuality_shouldPersistAndDispatchPlaybackCommand() = runTest {
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository()
        val playbackController = FakeSettingsPlaybackController()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = preferencesRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.updatePreferredAudioQuality(PlaybackAudioQuality.LOSSLESS)
        advanceUntilIdle()

        assertEquals(PlaybackAudioQuality.LOSSLESS, preferencesRepository.preferredAudioQuality)
        assertEquals(listOf(PlaybackAudioQuality.LOSSLESS), playbackController.requestedQualities)
        assertEquals(
            PlaybackAudioQuality.LOSSLESS,
            viewModel.uiStateFlow.value.playbackPreferencesState.preferredAudioQuality
        )
    }

    @Test
    fun preferredAudioQualityDialogVisibility_shouldToggleFromViewModelActions() = runTest {
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = FakeSettingsPlaybackPreferencesRepository(),
            playbackController = FakeSettingsPlaybackController()
        )
        advanceUntilIdle()

        viewModel.showPreferredAudioQualityDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiStateFlow.value.playbackPreferencesState.isPreferredAudioQualityDialogVisible)

        viewModel.dismissPreferredAudioQualityDialog()
        advanceUntilIdle()
        assertFalse(viewModel.uiStateFlow.value.playbackPreferencesState.isPreferredAudioQualityDialogVisible)
    }

    @Test
    fun updatePreferredAudioQuality_shouldDismissDialogAfterSelection() = runTest {
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository()
        val playbackController = FakeSettingsPlaybackController()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = preferencesRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()
        viewModel.showPreferredAudioQualityDialog()
        advanceUntilIdle()

        viewModel.updatePreferredAudioQuality(PlaybackAudioQuality.LOSSLESS)
        advanceUntilIdle()

        assertFalse(viewModel.uiStateFlow.value.playbackPreferencesState.isPreferredAudioQualityDialogVisible)
        assertEquals(PlaybackAudioQuality.LOSSLESS, preferencesRepository.preferredAudioQuality)
    }

    @Test
    fun savePlaybackCacheLimit_shouldPersistAndDispatchPlaybackCommand() = runTest {
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository()
        val playbackController = FakeSettingsPlaybackController()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = FakeAudioSourceRepository(),
            playbackPreferencesRepository = preferencesRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.updatePendingPlaybackCacheLimitMb("1024")
        viewModel.savePlaybackCacheLimit()
        advanceUntilIdle()

        assertEquals(1024L * BYTES_PER_MB, preferencesRepository.playbackCacheLimitBytes)
        assertEquals(listOf(1024L * BYTES_PER_MB), playbackController.requestedCacheLimits)
        assertEquals(
            1024L * BYTES_PER_MB,
            viewModel.uiStateFlow.value.cacheState.playbackCacheLimitBytes
        )
    }

    @Test
    fun activateAudioSource_shouldPersistActiveSourceConfigAndRefreshList() = runTest {
        val audioSourceRepository = FakeAudioSourceRepository().apply {
            addInitialSource(
                ManagedAudioSource(
                    id = "builtin-default-source",
                    displayName = "默认网易源",
                    baseUrl = "http://139.9.223.233:3000",
                    kind = ManagedAudioSourceKind.CUSTOM,
                    resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                    sourceConfigJson = neteaseCompatibleConfigJson("http://139.9.223.233:3000"),
                    enabled = true,
                    isBuiltIn = true,
                    isActive = true,
                    addedAtMs = 0L
                )
            )
            addInitialSource(
                ManagedAudioSource(
                    id = "source-1",
                    displayName = "LX Mirror",
                    baseUrl = "https://mirror.example.com",
                    kind = ManagedAudioSourceKind.CUSTOM,
                    resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                    sourceConfigJson = neteaseCompatibleConfigJson("https://mirror.example.com"),
                    enabled = true,
                    isActive = false,
                    addedAtMs = 123L
                )
            )
        }
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository()
        val playbackController = FakeSettingsPlaybackController()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = audioSourceRepository,
            playbackPreferencesRepository = preferencesRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.setActiveAudioSource("source-1")
        advanceUntilIdle()

        assertEquals(
            neteaseCompatibleConfigJson("https://mirror.example.com"),
            preferencesRepository.activeAudioSourceConfigJson
        )
        assertEquals(
            listOf(neteaseCompatibleConfigJson("https://mirror.example.com")),
            playbackController.requestedSourceConfigJsons
        )
        assertEquals(
            "source-1",
            viewModel.uiStateFlow.value.sourcesState.items.firstOrNull { it.isActive }?.id
        )
    }

    @Test
    fun removeActiveAudioSource_shouldFallbackToBuiltInAndDispatchNullConfig() = runTest {
        val audioSourceRepository = FakeAudioSourceRepository().apply {
            addInitialSource(
                ManagedAudioSource(
                    id = "builtin-default-source",
                    displayName = "默认网易源",
                    baseUrl = "http://139.9.223.233:3000",
                    kind = ManagedAudioSourceKind.CUSTOM,
                    resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                    sourceConfigJson = neteaseCompatibleConfigJson("http://139.9.223.233:3000"),
                    enabled = true,
                    isBuiltIn = true,
                    isActive = false,
                    addedAtMs = 0L
                )
            )
            addInitialSource(
                ManagedAudioSource(
                    id = "source-1",
                    displayName = "LX Mirror",
                    baseUrl = "https://mirror.example.com",
                    kind = ManagedAudioSourceKind.CUSTOM,
                    resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                    sourceConfigJson = neteaseCompatibleConfigJson("https://mirror.example.com"),
                    enabled = true,
                    isActive = true,
                    addedAtMs = 123L
                )
            )
        }
        val preferencesRepository = FakeSettingsPlaybackPreferencesRepository(
            activeAudioSourceConfigJson = neteaseCompatibleConfigJson("https://mirror.example.com")
        )
        val playbackController = FakeSettingsPlaybackController()
        val viewModel = SettingsViewModel(
            application = Application(),
            userRepository = FakeSettingsUserRepository(initialState = LoginState.LoggedOut),
            cacheRepository = FakeSettingsCacheRepository(),
            cacheController = FakeSettingsCacheController(),
            audioSourceRepository = audioSourceRepository,
            playbackPreferencesRepository = preferencesRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.removeAudioSource("source-1")
        advanceUntilIdle()

        assertNull(preferencesRepository.activeAudioSourceConfigJson)
        assertEquals(listOf<String?>(null), playbackController.requestedSourceConfigJsons)
        assertEquals(
            "builtin-default-source",
            viewModel.uiStateFlow.value.sourcesState.items.firstOrNull { it.isActive }?.id
        )
    }
}

private class FakeSettingsUserRepository(
    initialState: LoginState
) : UserRepository {
    private val state = MutableStateFlow(initialState)
    var logoutCount = 0

    override val loginStateFlow: StateFlow<LoginState> = state

    override fun currentSession(): UserSession? {
        return (state.value as? LoginState.LoggedIn)?.session
    }

    override suspend fun restorePersistedSession() = Unit

    override suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String
    ): UserSession {
        error("Not needed in this test")
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession {
        error("Not needed in this test")
    }

    override suspend fun refreshUserInfo(): UserSession? = currentSession()

    override suspend fun logout() {
        logoutCount += 1
        state.value = LoginState.LoggedOut
    }
}

private class FakeSettingsCacheRepository(
    private val snapshot: ManagedCacheSnapshot = ManagedCacheSnapshot(),
    private val refreshedSnapshot: ManagedCacheSnapshot = snapshot
) : SettingsCacheRepositoryContract {
    private var hasCleared = false

    override suspend fun readSnapshot(): ManagedCacheSnapshot {
        return if (hasCleared) refreshedSnapshot else snapshot
    }

    override suspend fun clearLyricsCache() {
        hasCleared = true
    }
}

private class FakeSettingsCacheController(
    private val clearAccepted: Boolean = true
) : SettingsCacheControllerContract {
    var clearRequests = 0

    override suspend fun clearPlaybackCache(): Boolean {
        clearRequests += 1
        return clearAccepted
    }
}

private class DelayedSettingsCacheController(
    private val testScope: CoroutineScope,
    private val onPlaybackCleared: () -> Unit
) : SettingsCacheControllerContract {
    var clearRequests = 0

    override suspend fun clearPlaybackCache(): Boolean {
        clearRequests += 1
        testScope.launch {
            delay(100)
            onPlaybackCleared()
        }
        return true
    }
}

private class DeferredPlaybackClearCacheRepository(
    private val playbackCleared: () -> Boolean,
    private val lyricsCleared: () -> Boolean,
    private val onLyricsCleared: () -> Unit
) : SettingsCacheRepositoryContract {
    override suspend fun readSnapshot(): ManagedCacheSnapshot {
        val playbackBytes = if (playbackCleared()) 0L else 10L
        val lyricsBytes = if (lyricsCleared()) 0L else 6L
        return ManagedCacheSnapshot(
            totalBytes = playbackBytes + lyricsBytes,
            entries = listOf(
                ManagedCacheEntry(ManagedCacheKind.PLAYBACK, "在线播放缓存", playbackBytes),
                ManagedCacheEntry(ManagedCacheKind.LYRICS, "歌词缓存", lyricsBytes)
            )
        )
    }

    override suspend fun clearLyricsCache() {
        onLyricsCleared()
    }
}

private class FakeAudioSourceRepository : AudioSourceRepositoryContract {
    private val items = mutableListOf<ManagedAudioSource>()

    fun addInitialSource(source: ManagedAudioSource) {
        items += source
    }

    override suspend fun readSources(): List<ManagedAudioSource> = items.toList()

    override suspend fun addSource(
        displayName: String,
        baseUrl: String,
        kind: ManagedAudioSourceKind
    ): Result<ManagedAudioSource> {
        if (displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("音源名称不能为空"))
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("音源地址必须是有效的 http/https 地址"))
        }
        if (items.any { it.baseUrl == baseUrl }) {
            return Result.failure(IllegalArgumentException("音源地址已存在"))
        }
        val source = ManagedAudioSource(
            id = "source-${items.size + 1}",
            displayName = displayName,
            baseUrl = baseUrl,
            kind = kind,
            resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
            sourceConfigJson = neteaseCompatibleConfigJson(baseUrl),
            enabled = true,
            addedAtMs = 123L
        )
        items += source
        return Result.success(source)
    }

    override suspend fun importSourceFromUrl(manifestUrl: String): Result<ManagedAudioSource> {
        return addImportedSource(
            displayName = "导入音源 ${items.size + 1}",
            baseUrl = "https://mirror-${items.size + 1}.example.com",
            importUrl = manifestUrl
        )
    }

    override suspend fun importSourceFromLocalJson(
        rawJson: String,
        displayLabel: String?
    ): Result<ManagedAudioSource> {
        return addImportedSource(
            displayName = "本地音源 ${items.size + 1}",
            baseUrl = "https://local-${items.size + 1}.example.com",
            importUrl = displayLabel
        )
    }

    override suspend fun setActiveSource(sourceId: String): Result<List<ManagedAudioSource>> {
        if (items.none { it.id == sourceId }) {
            return Result.failure(IllegalArgumentException("音源不存在"))
        }
        val updated = items.map { item ->
            item.copy(isActive = item.id == sourceId)
        }
        items.clear()
        items += updated
        return Result.success(updated)
    }

    override suspend fun removeSource(sourceId: String): Result<List<ManagedAudioSource>> {
        if (items.none { it.id == sourceId }) {
            return Result.failure(IllegalArgumentException("音源不存在"))
        }
        val removedWasActive = items.firstOrNull { it.id == sourceId }?.isActive == true
        items.removeAll { it.id == sourceId }
        if (removedWasActive) {
            val builtInIndex = items.indexOfFirst { it.isBuiltIn }
            if (builtInIndex >= 0) {
                items[builtInIndex] = items[builtInIndex].copy(isActive = true)
            }
        }
        return Result.success(items.toList())
    }

    private fun addImportedSource(
        displayName: String,
        baseUrl: String,
        importUrl: String?
    ): Result<ManagedAudioSource> {
        val source = ManagedAudioSource(
            id = "source-${items.count { !it.isBuiltIn } + 1}",
            displayName = displayName,
            baseUrl = baseUrl,
            kind = ManagedAudioSourceKind.CUSTOM,
            resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
            sourceConfigJson = neteaseCompatibleConfigJson(baseUrl),
            enabled = true,
            importUrl = importUrl,
            addedAtMs = 123L
        )
        items += source
        return Result.success(source)
    }
}

private class FakeSettingsPlaybackPreferencesRepository(
    var preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
    var playbackCacheLimitBytes: Long = DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES,
    var activeAudioSourceConfigJson: String? = null
) : SettingsPlaybackPreferencesRepositoryContract {
    override suspend fun readPreferredAudioQuality(): PlaybackAudioQuality = preferredAudioQuality

    override suspend fun writePreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        preferredAudioQuality = audioQuality
    }

    override suspend fun readPlaybackCacheLimitBytes(): Long = playbackCacheLimitBytes

    override suspend fun writePlaybackCacheLimitBytes(maxBytes: Long) {
        playbackCacheLimitBytes = maxBytes
    }

    override suspend fun readActiveAudioSourceConfigJson(): String? = activeAudioSourceConfigJson

    override suspend fun writeActiveAudioSourceConfigJson(configJson: String?) {
        activeAudioSourceConfigJson = configJson
    }
}

private class FakeSettingsPlaybackController : SettingsPlaybackControllerContract {
    val requestedQualities = mutableListOf<PlaybackAudioQuality>()
    val requestedCacheLimits = mutableListOf<Long>()
    val requestedSourceConfigJsons = mutableListOf<String?>()

    override suspend fun setPreferredAudioQuality(audioQuality: PlaybackAudioQuality): Boolean {
        requestedQualities += audioQuality
        return true
    }

    override suspend fun setPlaybackCacheLimitBytes(maxBytes: Long): Boolean {
        requestedCacheLimits += maxBytes
        return true
    }

    override suspend fun setActiveAudioSourceConfigJson(configJson: String?): Boolean {
        requestedSourceConfigJsons += configJson
        return true
    }
}

private fun neteaseCompatibleConfigJson(baseUrl: String): String {
    return org.json.JSONObject()
        .put("type", ManagedAudioSourceResolverType.NETEASE_COMPATIBLE.wireValue)
        .put("baseUrl", baseUrl.trim().trimEnd('/'))
        .toString()
}

private fun settingsSession(): UserSession {
    return UserSession(
        cookie = "MUSIC_U=token; __csrf=csrf-token;",
        csrfToken = "csrf-token",
        userInfo = UserInfo(
            userId = 77L,
            accountId = 88L,
            nickname = "Codex",
            avatarUrl = "https://example.com/avatar.jpg",
            vipType = 11,
            level = 9,
            signature = "hello",
            backgroundUrl = "https://example.com/bg.jpg",
            playlistCount = 9,
            followeds = 8,
            follows = 7,
            eventCount = 6,
            listenSongs = 321,
            accountIdentity = "13800138000"
        ),
        lastValidatedAtMs = 123L
    )
}
