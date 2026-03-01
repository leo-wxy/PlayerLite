# Native Cache Framework Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an independent `:cache-core` module that provides request-scoped source-byte caching with business-injected network provider, configurable block size, watermark cleanup, and playback-safe clear behavior.

**Architecture:** Keep `:player` fully cache-agnostic. Implement cache primitives in `:cache-core` (Kotlin API + JNI + C++ core), and integrate only at business-layer sources (`app`/`playback-service`). Session APIs accept `resourceKey + SessionCacheConfig + RangeDataProvider`, while cleanup uses watermark defaults and optional blocking selector callback.

**Tech Stack:** Android Library module, Kotlin, JNI (C++17), JUnit4, Gradle multi-module build.

---

### Task 1: Bootstrap `:cache-core` Module

**Files:**
- Create: `cache-core/build.gradle.kts`
- Create: `cache-core/src/main/AndroidManifest.xml`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/CacheCoreSmokeTest.kt`
- Modify: `settings.gradle.kts`

**Step 1: Write the failing test**

```kotlin
package com.wxy.playerlite.cache.core

import org.junit.Assert.assertFalse
import org.junit.Test

class CacheCoreSmokeTest {
    @Test
    fun initFlagIsFalseByDefault() {
        assertFalse(CacheCore.isInitialized())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreSmokeTest"`
Expected: FAIL with module/class not found.

**Step 3: Write minimal implementation**

```kotlin
package com.wxy.playerlite.cache.core

object CacheCore {
    fun isInitialized(): Boolean = false
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CacheCoreSmokeTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add settings.gradle.kts cache-core/build.gradle.kts cache-core/src/main/AndroidManifest.xml \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/CacheCoreSmokeTest.kt
git commit -m "feat(cache-core): bootstrap cache module"
```

### Task 2: Add Global Init Config With Safe Fallbacks

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/config/CacheCoreConfig.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/config/CacheDefaults.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/config/ConfigSanitizer.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/config/ConfigSanitizerTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun invalidWatermarkFallsBackToDefaults() {
    val raw = CacheCoreConfig(
        cacheRootDirPath = "/tmp/cache",
        maxDiskBytes = -1,
        highWatermarkPercent = 10,
        lowWatermarkPercent = 20
    )
    val sanitized = ConfigSanitizer.sanitize(raw)
    assertEquals(300L * 1024 * 1024, sanitized.maxDiskBytes)
    assertEquals(90, sanitized.highWatermarkPercent)
    assertEquals(70, sanitized.lowWatermarkPercent)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ConfigSanitizerTest"`
Expected: FAIL with missing sanitizer/config classes.

**Step 3: Write minimal implementation**

```kotlin
object CacheDefaults {
    const val DEFAULT_MAX_DISK_BYTES: Long = 300L * 1024 * 1024
    const val DEFAULT_HIGH_WATERMARK = 90
    const val DEFAULT_LOW_WATERMARK = 70
}
```

Implement sanitizer to fallback invalid values and emit warning log hook.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ConfigSanitizerTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/config \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/config/ConfigSanitizerTest.kt
git commit -m "feat(cache-core): add init config sanitizer and defaults"
```

### Task 3: Add Request-Scoped Session API (`resourceKey + config + provider`)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/OpenSessionParams.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/SessionCacheConfig.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/CloseSessionPolicy.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/provider/RangeDataProvider.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/CacheSession.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/session/OpenSessionParamsTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun openSessionRejectsBlankResourceKey() {
    val result = CacheCore.openSession(
        OpenSessionParams(
            resourceKey = "  ",
            provider = FakeProvider(),
            config = SessionCacheConfig()
        )
    )
    assertTrue(result.isFailure)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*OpenSessionParamsTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement `OpenSessionParams` with required `resourceKey` and `provider`.
- Implement `SessionCacheConfig` with:
  - `cacheMode: MEMORY_ONLY | MEMORY_AND_DISK`
  - `blockSizeBytes: Int?`
  - `closeSessionPolicy`
  - `prefetchConfig`
- Add `CacheCore.openSession(...)` validation.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*OpenSessionParamsTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/session \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/provider \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/session/OpenSessionParamsTest.kt
git commit -m "feat(cache-core): add request scoped session api"
```

### Task 4: Implement Block Mapping + Custom Block Size Validation

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/block/BlockCalculator.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/block/BlockSizeValidator.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/index/ResourceIndex.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/block/BlockCalculatorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun usesSessionBlockSizeWhenProvided() {
    val mapping = BlockCalculator.map(offset = 300_000L, blockSizeBytes = 128 * 1024)
    assertEquals(2, mapping.blockIndex)
    assertEquals(37_856, mapping.inBlockOffset)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*BlockCalculatorTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement mapping formula:
  - `blockIndex = offset / blockSizeBytes`
  - `inBlockOffset = offset % blockSizeBytes`
- Validate size range (64KB~2MB, 4KB aligned), fallback to default + warning.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*BlockCalculatorTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/block \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/index/ResourceIndex.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/block/BlockCalculatorTest.kt
git commit -m "feat(cache-core): add block mapping and validation"
```

### Task 5: Add Watermark Cleanup + Default LRU Candidate Builder

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/CleanupPolicy.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/WatermarkPolicy.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/LruCandidateBuilder.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/model/RemovedCacheItem.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/WatermarkPolicyTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun triggersCleanupAtHighWatermarkAndTargetsLowWatermark() {
    val decision = WatermarkPolicy(90, 70).evaluate(
        usedBytes = 920,
        maxDiskBytes = 1000
    )
    assertTrue(decision.shouldCleanup)
    assertEquals(220, decision.targetBytesToFree)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*WatermarkPolicyTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement watermark decision.
- Implement default LRU candidate sort when business policy is absent.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*WatermarkPolicyTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/WatermarkPolicyTest.kt
git commit -m "feat(cache-core): add watermark cleanup and default lru"
```

### Task 6: Add Blocking Candidate Selector Callback (Business Decides Keys)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/CleanupCandidateSelector.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/model/CleanupSelectionRequest.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/model/CleanupSelectionResult.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/WatermarkPolicy.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/CleanupCandidateSelectorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun selectorReceivesCandidatesAndReturnsKeysToDelete() {
    val selector = RecordingSelector(listOf("song-A"))
    val keys = selector.selectCandidates(
        CleanupSelectionRequest(
            reason = "HIGH_WATERMARK",
            candidates = listOf(RemovedCacheItem(resourceKey = "song-A", bytesFreed = 10))
        )
    ).resourceKeysToDelete
    assertEquals(listOf("song-A"), keys)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CleanupCandidateSelectorTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement blocking selector contract.
- Add timeout fallback to default LRU when selector times out/errors.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CleanupCandidateSelectorTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/CleanupCandidateSelectorTest.kt
git commit -m "feat(cache-core): add blocking cleanup selector"
```

### Task 7: Protect Active Playback During Clear (`EXCLUDE_ACTIVE + DEFERRED`)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/ActivePinRegistry.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/DeferredDeleteQueue.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/CleanupCoordinator.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/ActivePinCleanupTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun clearAllSkipsActiveResourceAndMarksDeferred() {
    val registry = ActivePinRegistry().apply { pin("song-active") }
    val result = CleanupCoordinator(registry).clearAll(listOf("song-active", "song-idle"))
    assertEquals(listOf("song-idle"), result.deletedResourceKeys)
    assertEquals(listOf("song-active"), result.deferredResourceKeys)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ActivePinCleanupTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement active pin ref-count.
- Skip active keys during clear.
- Queue deferred deletion and process when ref-count returns 0.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ActivePinCleanupTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/session/ActivePinRegistry.kt \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/cleanup/ActivePinCleanupTest.kt
git commit -m "feat(cache-core): protect active playback during cleanup"
```

### Task 8: Add Aggregated Cleanup Event Callbacks (Batch Array)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/events/CacheEventListener.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/events/CleanupEvents.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/CleanupCoordinator.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/events/CleanupEventAggregationTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun emitsBatchCompletedWithRemovedItemsArray() {
    val listener = RecordingCacheEventListener()
    val coordinator = CleanupCoordinator(listener = listener)
    coordinator.reportBatch(
        removed = listOf(RemovedCacheItem(resourceKey = "a", bytesFreed = 1))
    )
    assertEquals(1, listener.batchEvents.single().removedItems.size)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CleanupEventAggregationTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement events:
  - `onCleanupTriggered`
  - `onCleanupBatchCompleted(removedItems: List<RemovedCacheItem>, ...)`
  - `onCleanupCompleted`
  - `onCleanupFailed`
- Ensure no per-item callback exists.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*CleanupEventAggregationTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/events \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/cleanup/CleanupCoordinator.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/events/CleanupEventAggregationTest.kt
git commit -m "feat(cache-core): add aggregated cleanup callbacks"
```

### Task 8.1: Add Unified Cache Query API (Exact + Prefix)

**Files:**
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/query/CacheQueryApi.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/query/model/ResourceCacheEntry.kt`
- Create: `cache-core/src/main/java/com/wxy/playerlite/cache/core/query/model/ResourceCacheQueryResult.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt`
- Modify: `cache-core/src/main/java/com/wxy/playerlite/cache/core/session/OpenSessionParams.kt`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/query/ResourceCacheQueryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun resourceQuerySupportsExactAndPrefixModes() {
    val api = FakeCacheQueryApi(
        entries = listOf(
            ResourceCacheEntry(resourceKey = "song/1001/source-a"),
            ResourceCacheEntry(resourceKey = "song/1001/source-b")
        )
    )

    val exact = api.getResourceCache("song/1001/source-a", includePrefixMatches = false)
    assertEquals(1, exact.matchedResources.size)

    val prefix = api.getResourceCache("song/1001/", includePrefixMatches = true)
    assertEquals(2, prefix.matchedResources.size)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ResourceCacheQueryTest"`
Expected: FAIL with missing query API/models.

**Step 3: Write minimal implementation**

- Add `CacheCore.getResourceCache(resourceKeyOrPrefix, includePrefixMatches)`:
  - `false` => exact key query
  - `true` => prefix query (supports `limit/cursor`)
- Merge old stats shape into this result model.
- Keep `ResourceCacheEntry` minimal; quality-related fields remain optional future extension.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*ResourceCacheQueryTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/src/main/java/com/wxy/playerlite/cache/core/query \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCore.kt \
  cache-core/src/main/java/com/wxy/playerlite/cache/core/session/OpenSessionParams.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/query/ResourceCacheQueryTest.kt
git commit -m "feat(cache-core): add unified resource cache query api"
```

### Task 9: Wire JNI + C++ Core Skeleton (Independent Native Library)

**Files:**
- Create: `cache-core/src/main/cpp/CMakeLists.txt`
- Create: `cache-core/src/main/cpp/cache_core_jni.cpp`
- Create: `cache-core/src/main/cpp/core/cache_runtime.h`
- Create: `cache-core/src/main/cpp/core/cache_runtime.cpp`
- Modify: `cache-core/build.gradle.kts`
- Create: `cache-core/src/test/java/com/wxy/playerlite/cache/core/NativeBridgeLoadTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun nativeLibraryLoads() {
    assertTrue(CacheCoreNativeBridge.isLoaded())
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*NativeBridgeLoadTest"`
Expected: FAIL with `UnsatisfiedLinkError`.

**Step 3: Write minimal implementation**

- Add `externalNativeBuild` and CMake wiring.
- Export a minimal JNI probe method from `libcachecore.so`.
- Provide Kotlin `CacheCoreNativeBridge` wrapper.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cache-core:testDebugUnitTest --tests "*NativeBridgeLoadTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add cache-core/build.gradle.kts cache-core/src/main/cpp cache-core/src/main/java/com/wxy/playerlite/cache/core/CacheCoreNativeBridge.kt \
  cache-core/src/test/java/com/wxy/playerlite/cache/core/NativeBridgeLoadTest.kt
git commit -m "feat(cache-core): add native runtime skeleton and jni bridge"
```

### Task 10: Business-Layer Integration Without Touching `:player`

**Files:**
- Create: `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/CachedNetworkSource.kt`
- Create: `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/NoCacheNetworkSource.kt`
- Create: `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/SourceFactory.kt`
- Modify: `playback-service/build.gradle.kts`
- Create: `playback-service/src/test/java/com/wxy/playerlite/playback/process/source/SourceFactoryTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun factoryCanSwitchCacheImplementationByFlag() {
    val source = SourceFactory(useCacheCore = false).create("rk", FakeProvider())
    assertTrue(source is NoCacheNetworkSource)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :playback-service:testDebugUnitTest --tests "*SourceFactoryTest"`
Expected: FAIL.

**Step 3: Write minimal implementation**

- Implement switchable factory.
- Keep `:player` untouched.
- `CachedNetworkSource` internally calls `CacheCore.openSession(...)`.

**Step 4: Run test to verify it passes**

Run: `./gradlew :playback-service:testDebugUnitTest --tests "*SourceFactoryTest"`
Expected: PASS.

**Step 5: Commit**

```bash
git add playback-service/src/main/java/com/wxy/playerlite/playback/process/source \
  playback-service/build.gradle.kts \
  playback-service/src/test/java/com/wxy/playerlite/playback/process/source/SourceFactoryTest.kt
git commit -m "feat(playback-service): integrate cache-core at business layer"
```

### Task 11: End-to-End Verification + Docs Sync

**Files:**
- Modify: `README.md`
- Create: `docs/plans/2026-03-01-native-cache-framework-verification.md`

**Step 1: Write verification checklist doc (failing state)**

Document expected checks before execution:
- `:cache-core` unit tests
- `:playback-service` unit tests
- cache clear behavior with active playback
- config fallback warnings

**Step 2: Run verification commands**

Run:
- `./gradlew :cache-core:testDebugUnitTest`
- `./gradlew :playback-service:testDebugUnitTest`

Expected: PASS.

**Step 3: Update README integration notes**

Add section for:
- `:cache-core` module purpose
- init config defaults (`300MB`, `90/70`)
- business `RangeDataProvider` injection
- `:player` remains cache-agnostic

**Step 4: Re-run verification**

Run same commands, ensure no regression.
Expected: PASS.

**Step 5: Commit**

```bash
git add README.md docs/plans/2026-03-01-native-cache-framework-verification.md
git commit -m "docs: add cache-core integration and verification notes"
```

---

## Skills To Apply During Execution

- `@superpowers:test-driven-development` for every task implementation loop.
- `@superpowers:verification-before-completion` before each task commit.
- `@superpowers:receiving-code-review` after Tasks 6, 9, and 10.

## Risks and Guardrails

1. Do not add any cache dependency to `:player`.
2. Keep cleanup selector callback off playback read threads.
3. Keep callback payloads aggregated (batch arrays), not per item.
4. Preserve active playback safety (`EXCLUDE_ACTIVE + DEFERRED`) in all clear paths.

Plan complete and saved to `docs/plans/2026-03-01-native-cache-framework-implementation.md`. Two execution options:

1. Subagent-Driven (this session) - I dispatch fresh subagent per task, review between tasks, fast iteration
2. Parallel Session (separate) - Open new session with executing-plans, batch execution with checkpoints

Which approach?
