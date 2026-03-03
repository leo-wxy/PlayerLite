# Cache Core Restart Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebuild cache capability with Native-first architecture, expose `CacheCore + CacheSession` boundary, and integrate into `playback-service` for `http/https` streaming with seek-cancel support.

**Architecture:** Implement a cross-platform C++ core (session lifecycle, block cache, disk metadata) behind a thin Android JNI/Kotlin facade. `CacheCore` owns global runtime only, `CacheSession` owns per-resource operations. Playback uses `CachedNetworkSource` with injected `RangeDataProvider` and strict fail-fast behavior.

**Tech Stack:** Android library modules, Kotlin, C++17/JNI, JUnit4, Gradle.

---

### Task 1: Re-bootstrap `:cache-core` Module and Wire Build

**Files:**
- Create: `cache-core/build.gradle.kts`
- Create: `cache-core/src/main/AndroidManifest.xml`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/CacheCoreSmokeTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `playback-service/build.gradle.kts`

**Step 1: Write the failing test**

Add smoke test asserting module class is available and default init state is false.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreSmokeTest"`
Expected: FAIL (module/class missing).

**Step 3: Write minimal implementation**

Create `CacheCore` with `isInitialized()` returning false and minimal build files.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreSmokeTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add settings.gradle.kts playback-service/build.gradle.kts cache-core
git commit -m "feat(cache-core): bootstrap module and smoke test"
```

### Task 2: Define Public Contracts (`CacheCore`, `CacheSession`, `RangeDataProvider`)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/CacheSession.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/OpenSessionParams.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/SessionCacheConfig.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/provider/RangeDataProvider.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/config/CacheCoreConfig.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/session/OpenSessionContractTest.kt`

**Step 1: Write the failing test**

Test blank `resourceKey` rejected, valid params return session instance.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*OpenSessionContractTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Implement contracts and `openSession` validation. No JNI yet.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*OpenSessionContractTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core cache-core/src/test/java/com/wxy/playerlite/cache/core/session
git commit -m "feat(cache-core): add session contracts and open validation"
```

### Task 3: Implement Native Runtime Skeleton + JNI Bridge

**Files:**
- Create: `cache-core/src/main/cpp/CMakeLists.txt`
- Create: `cache-core/src/main/cpp/cache_core_jni.cpp`
- Create: `cache-core/src/main/cpp/core/cache_runtime.h`
- Create: `cache-core/src/main/cpp/core/cache_runtime.cpp`
- Modify: `cache-core/build.gradle.kts`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCoreNativeBridge.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/NativeBootstrapTest.kt`

**Step 1: Write the failing test**

Test native availability probe and init call path behavior.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*NativeBootstrapTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Native runtime supports init/shutdown/open/close empty session map; JNI methods callable.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*NativeBootstrapTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/cpp cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCoreNativeBridge.kt cache-core/build.gradle.kts cache-core/src/test/java/com/wxy/playerlite/cache/core/NativeBootstrapTest.kt
git commit -m "feat(cache-core): add native runtime skeleton and jni bridge"
```

### Task 4: Add Disk Layout (`.data`, `_config.json`, `_extra.json`) and Recovery

**Files:**
- Create: `cache-core/src/main/cpp/core/cache_runtime_storage.cpp`
- Modify: `cache-core/src/main/cpp/core/cache_runtime.h`
- Modify: `cache-core/src/main/cpp/core/cache_runtime.cpp`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/persistence/CachePersistenceRecoveryTest.kt`

**Step 1: Write the failing test**

Test writes cache metadata and data files, shutdown/reinit, then session recovers block map.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CachePersistenceRecoveryTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Persist files exactly as:
- `<resourceKey>.data`
- `<resourceKey>_config.json`
- `<resourceKey>_extra.json`

Implement load-on-open recovery and corruption detection.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CachePersistenceRecoveryTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/cpp/core cache-core/src/test/java/com/wxy/playerlite/cache/core/persistence
git commit -m "feat(cache-core): persist cache data and config with recovery"
```

### Task 5: Implement Read Path (Memory->Disk->Provider) with Partial Return

**Files:**
- Create: `cache-core/src/main/cpp/core/cache_runtime_read.cpp`
- Modify: `cache-core/src/main/cpp/core/cache_runtime.cpp`
- Modify: `cache-core/src/main/cpp/cache_core_jni.cpp`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCoreNativeBridge.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/CacheSession.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/session/CacheCoreReadSessionTest.kt`

**Step 1: Write the failing test**

Test reads return partial bytes (not forced full size), and second read can continue from next offset.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreReadSessionTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Implement block mapping, cache lookup chain, miss fetch and write-through to memory+disk.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreReadSessionTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/cpp/core cache-core/src/main/cpp/cache_core_jni.cpp cache-core/src/main/java/com/wxy/playerlite/cache/core cache-core/src/test/java/com/wxy/playerlite/cache/core/session
 git commit -m "feat(cache-core): add read pipeline with memory disk provider chain"
```

### Task 6: Implement Seek-Cancel Semantics

**Files:**
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/provider/RangeDataProvider.kt`
- Modify: `cache-core/src/main/cpp/core/cache_runtime.h`
- Modify: `cache-core/src/main/cpp/core/cache_runtime_read.cpp`
- Modify: `cache-core/src/main/cpp/cache_core_jni.cpp`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/CacheSession.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/session/SeekCancelTest.kt`

**Step 1: Write the failing test**

Test seek triggers cancel, blocked provider read exits quickly, next read at new offset succeeds.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*SeekCancelTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Wire `CacheSession.cancelPendingRead` to provider cancel path and map cancel status to retryable result.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*SeekCancelTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core cache-core/src/main/cpp/core cache-core/src/main/cpp/cache_core_jni.cpp cache-core/src/test/java/com/wxy/playerlite/cache/core/session/SeekCancelTest.kt
git commit -m "feat(cache-core): add seek cancel in-flight provider read semantics"
```

### Task 7: Integrate `cache-core` into `playback-service`

**Files:**
- Create: `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/CachedNetworkSource.kt`
- Create: `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/HttpRangeDataProvider.kt`
- Modify: `playback-service/src/main/java/com/wxy/playerlite/playback/process/TrackPreparationCoordinator.kt`
- Modify: `playback-service/src/main/java/com/wxy/playerlite/playback/process/PreparedSourceSession.kt`
- Modify: `playback-service/src/main/java/com/wxy/playerlite/playback/model/MusicInfo.kt`
- Create: `playback-service/src/test/java/com/wxy/playerlite/playback/process/source/CachedNetworkSourceTest.kt`
- Create: `playback-service/src/test/java/com/wxy/playerlite/playback/process/source/HttpRangeDataProviderTest.kt`

**Step 1: Write the failing tests**

Test cached source open/read/seek/cancel behavior and provider range request correctness.

**Step 2: Run tests to verify they fail**

Run: `./gradlew :playback-service:testDebugUnitTest --tests "*CachedNetworkSourceTest" --tests "*HttpRangeDataProviderTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

Integrate cache session with playback source path for `http/https` tracks only.

**Step 4: Run tests to verify they pass**

Run: `./gradlew :playback-service:testDebugUnitTest --tests "*CachedNetworkSourceTest" --tests "*HttpRangeDataProviderTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add playback-service/src/main/java/com/wxy/playerlite/playback/process playback-service/src/main/java/com/wxy/playerlite/playback/model/MusicInfo.kt playback-service/src/test/java/com/wxy/playerlite/playback/process/source
git commit -m "feat(playback-service): integrate cached network source with cache-core"
```

### Task 8: Add End-to-End Regression Verification

**Files:**
- Create: `playback-service/src/test/java/com/wxy/playerlite/playback/process/source/CachePlaybackIntegrationTest.kt`
- Modify: `README.md` (cache-core usage notes)

**Step 1: Write the failing integration test**

Simulate startup -> read -> seek -> read -> close -> reopen -> read from disk hit.

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest :playback-service:testDebugUnitTest --tests "*CachePlaybackIntegrationTest"`
Expected: FAIL.

**Step 3: Implement missing glue/bugfixes**

Only minimal fixes required by test.

**Step 4: Run full verification**

Run:
- `./gradlew :cache-core:testDebugUnitTest`
- `./gradlew :playback-service:testDebugUnitTest`
- `./gradlew :app:compileDebugKotlin`

Expected: PASS.

**Step 5: Commit**

```bash
git add playback-service/src/test/java/com/wxy/playerlite/playback/process/source/CachePlaybackIntegrationTest.kt README.md
git commit -m "test: add cache playback integration regression coverage"
```

