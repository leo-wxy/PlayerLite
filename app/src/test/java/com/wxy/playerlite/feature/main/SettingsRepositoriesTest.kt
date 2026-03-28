package com.wxy.playerlite.feature.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoriesTest {
    private val tempRoots = mutableListOf<java.nio.file.Path>()

    @After
    fun tearDown() {
        tempRoots.forEach { path ->
            runCatching { path.toFile().deleteRecursively() }
        }
        tempRoots.clear()
    }

    @Test
    fun cacheRepository_shouldReportTotalAndSectionSizes() = runTest {
        val root = createTempDirectory(prefix = "settings-cache-test")
        tempRoots.add(root)
        val cacheDir = root.resolve("cache_core").toFile().apply {
            mkdirs()
            resolve("audio.bin").writeBytes(ByteArray(10))
        }
        val lyricsDir = root.resolve("lyrics").toFile().apply {
            mkdirs()
            resolve("song-1.lrc").writeBytes(ByteArray(6))
        }
        val repository = SettingsCacheRepository(
            playbackCacheDirProvider = { cacheDir },
            lyricsCacheDirProvider = { lyricsDir }
        )

        val snapshot = repository.readSnapshot()

        assertEquals(16L, snapshot.totalBytes)
        assertEquals(10L, snapshot.entries.single { it.kind == ManagedCacheKind.PLAYBACK }.bytes)
        assertEquals(6L, snapshot.entries.single { it.kind == ManagedCacheKind.LYRICS }.bytes)
    }

    @Test
    fun cacheRepository_clearLyricsCache_shouldDeleteLyricsFilesOnly() = runTest {
        val root = createTempDirectory(prefix = "settings-lyrics-clear")
        tempRoots.add(root)
        val cacheDir = root.resolve("cache_core").toFile().apply {
            mkdirs()
            resolve("audio.bin").writeBytes(ByteArray(10))
        }
        val lyricsDir = root.resolve("lyrics").toFile().apply {
            mkdirs()
            resolve("song-1.lrc").writeBytes(ByteArray(6))
        }
        val repository = SettingsCacheRepository(
            playbackCacheDirProvider = { cacheDir },
            lyricsCacheDirProvider = { lyricsDir }
        )

        repository.clearLyricsCache()

        assertTrue(cacheDir.resolve("audio.bin").exists())
        assertFalse(lyricsDir.resolve("song-1.lrc").exists())
    }

    @Test
    fun audioSourceRepository_shouldPersistAndRestoreSavedSources() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "settings_audio_sources_test_restore",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = AudioSourceRepository(
            preferences = preferences,
            timeProvider = { 123L },
            idProvider = { "source-1" }
        )

        val result = repository.addSource(
            displayName = "自定义源",
            baseUrl = "https://example.com/api",
            kind = ManagedAudioSourceKind.CUSTOM
        )

        assertTrue(result.isSuccess)
        val restored = repository.readSources()
        assertEquals(2, restored.size)
        assertEquals("builtin-default-source", restored.first().id)
        assertEquals("source-1", restored.last().id)
        assertEquals("自定义源", restored.last().displayName)
        assertEquals("https://example.com/api", restored.last().baseUrl)
    }

    @Test
    fun audioSourceRepository_shouldRejectInvalidOrDuplicateBaseUrl() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "settings_audio_sources_test_validation",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = AudioSourceRepository(
            preferences = preferences,
            timeProvider = { 123L },
            idProvider = { "source-1" }
        )

        val invalid = repository.addSource(
            displayName = "无效源",
            baseUrl = "ftp://example.com",
            kind = ManagedAudioSourceKind.CUSTOM
        )
        val first = repository.addSource(
            displayName = "自定义源",
            baseUrl = "https://example.com/api",
            kind = ManagedAudioSourceKind.CUSTOM
        )
        val duplicate = repository.addSource(
            displayName = "重复源",
            baseUrl = "https://example.com/api",
            kind = ManagedAudioSourceKind.CUSTOM
        )

        assertTrue(invalid.isFailure)
        assertTrue(first.isSuccess)
        assertTrue(duplicate.isFailure)
        assertEquals(
            1,
            repository.readSources().count { !it.isBuiltIn }
        )
    }

    @Test
    fun playbackPreferencesRepository_shouldPersistQualityCacheLimitAndActiveSourceConfigJson() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "player_playback_preferences_test_settings",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = SettingsPlaybackPreferencesRepository(preferences)

        repository.writePreferredAudioQuality(com.wxy.playerlite.playback.model.PlaybackAudioQuality.HIRES)
        repository.writePlaybackCacheLimitBytes(1024L * BYTES_PER_MB)
        repository.writeActiveAudioSourceConfigJson(
            """
            {
              "type": "netease-compatible",
              "baseUrl": "https://mirror.example.com/"
            }
            """.trimIndent()
        )

        assertEquals(
            com.wxy.playerlite.playback.model.PlaybackAudioQuality.HIRES,
            repository.readPreferredAudioQuality()
        )
        assertEquals(1024L * BYTES_PER_MB, repository.readPlaybackCacheLimitBytes())
        assertNeteaseCompatibleConfig(
            repository.readActiveAudioSourceConfigJson(),
            "https://mirror.example.com"
        )
    }

    @Test
    fun playbackPreferencesRepository_shouldMigrateLegacyPreferredSourceBaseUrl() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "player_playback_preferences_test_legacy_source",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("preferred_audio_source_base_url", "https://legacy.example.com/api/")
            .commit()
        val repository = SettingsPlaybackPreferencesRepository(preferences)

        assertNeteaseCompatibleConfig(
            repository.readActiveAudioSourceConfigJson(),
            "https://legacy.example.com/api"
        )
    }

    @Test
    fun audioSourceRepository_shouldImportSourcePackageManifestPersistConfigAndSwitchActiveSource() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "settings_audio_sources_test_manifest",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = AudioSourceRepository(
            preferences = preferences,
            builtInBaseUrl = "http://139.9.223.233:3000",
            manifestFetcher = { _ ->
                """
                {
                  "name": "LX Mirror",
                  "author": "Codex",
                  "version": "1.0.0",
                  "runtime": {
                    "type": "native"
                  },
                  "resolver": {
                    "type": "netease-compatible",
                    "baseUrl": "https://mirror.example.com/"
                  }
                }
                """.trimIndent()
            },
            timeProvider = { 123L },
            idProvider = { "source-lx" }
        )

        val imported = repository.importSourceFromUrl("https://cdn.example.com/lx-source.json")
        val activated = repository.setActiveSource("source-lx")
        val restored = repository.readSources()

        assertTrue(imported.isSuccess)
        assertTrue(activated.isSuccess)
        assertEquals(2, restored.size)
        assertEquals("默认网易源", restored.first().displayName)
        assertFalse(restored.first().isActive)
        val importedSource = restored.last()
        assertEquals("source-lx", importedSource.id)
        assertEquals("LX Mirror", importedSource.displayName)
        assertEquals("Codex", importedSource.author)
        assertEquals("1.0.0", importedSource.version)
        assertEquals("https://mirror.example.com", importedSource.baseUrl)
        assertEquals(
            ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
            importedSource.resolverType
        )
        assertNeteaseCompatibleConfig(
            importedSource.sourceConfigJson,
            "https://mirror.example.com"
        )
        assertEquals("https://cdn.example.com/lx-source.json", importedSource.importUrl)
        assertTrue(importedSource.isActive)
    }

    @Test
    fun audioSourceRepository_shouldRestoreLegacyPersistedSourceEntriesAsNeteaseCompatibleConfigs() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "settings_audio_sources_test_legacy_entries",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString(
                "audio_sources",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "source-legacy")
                        put("display_name", "Legacy Mirror")
                        put("base_url", "https://legacy.example.com/api/")
                        put("kind", ManagedAudioSourceKind.CUSTOM.name)
                        put("enabled", true)
                        put("author", "Legacy")
                        put("version", "0.9.0")
                        put("added_at_ms", 123L)
                    }
                ).toString()
            )
            .commit()
        val repository = AudioSourceRepository(
            preferences = preferences,
            builtInBaseUrl = "http://139.9.223.233:3000"
        )

        val restored = repository.readSources()

        assertEquals(2, restored.size)
        val source = restored.last()
        assertEquals("source-legacy", source.id)
        assertEquals("Legacy Mirror", source.displayName)
        assertEquals(ManagedAudioSourceResolverType.NETEASE_COMPATIBLE, source.resolverType)
        assertNeteaseCompatibleConfig(
            source.sourceConfigJson,
            "https://legacy.example.com/api"
        )
    }

    @Test
    fun audioSourceRepository_removeActiveImportedSource_shouldFallbackToBuiltIn() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "settings_audio_sources_test_remove",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = AudioSourceRepository(
            preferences = preferences,
            builtInBaseUrl = "http://139.9.223.233:3000",
            manifestFetcher = { _ ->
                """
                {
                  "type": "netease-compatible",
                  "name": "Mirror B",
                  "baseUrl": "https://mirror-b.example.com"
                }
                """.trimIndent()
            },
            timeProvider = { 123L },
            idProvider = { "source-b" }
        )

        assertTrue(repository.importSourceFromUrl("https://cdn.example.com/source-b.json").isSuccess)
        assertTrue(repository.setActiveSource("source-b").isSuccess)
        val removed = repository.removeSource("source-b")
        val restored = repository.readSources()

        assertTrue(removed.isSuccess)
        assertEquals(1, restored.size)
        assertTrue(restored.single().isBuiltIn)
        assertTrue(restored.single().isActive)
        assertNull(restored.single().importUrl)
    }
}

private fun assertNeteaseCompatibleConfig(rawJson: String?, expectedBaseUrl: String) {
    assertTrue(rawJson != null && rawJson.isNotBlank())
    val json = JSONObject(requireNotNull(rawJson))
    assertEquals("netease-compatible", json.getString("type"))
    assertEquals(expectedBaseUrl, json.getString("baseUrl"))
}
