# User Center Local Songs Page Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在个人中心增加“本地歌曲”入口和独立页面，支持扫描 `MediaStore`、缓存扫描结果，并通过“播放全部/点击歌曲播放”快速验证播放列表能力。

**Architecture:** 个人中心只负责暴露入口，真正的本地歌曲能力放到独立 Activity + ViewModel + Repository 中。Repository 负责 `MediaStore` 扫描与本地快照缓存，ViewModel 负责“先读缓存、无缓存首扫、右上角手动重扫、权限态和播放动作”，播放仍复用现有 `RuntimeDetailPlaybackGateway` 与统一播放列表替换语义。

**Tech Stack:** Kotlin、Jetpack Compose、Android MediaStore、SharedPreferences、kotlinx.serialization、Robolectric、OpenSpec

---

### Task 1: 同步 OpenSpec 与计划范围

**Files:**
- Modify: `/Users/wxy/Projects/player-lite/openspec/changes/enhance-playlist-playback-and-musicinfo/proposal.md`
- Modify: `/Users/wxy/Projects/player-lite/openspec/changes/enhance-playlist-playback-and-musicinfo/design.md`
- Create: `/Users/wxy/Projects/player-lite/openspec/changes/enhance-playlist-playback-and-musicinfo/specs/user-center-tab-shell/spec.md`
- Modify: `/Users/wxy/Projects/player-lite/openspec/changes/enhance-playlist-playback-and-musicinfo/tasks.md`

**Step 1: 回写最终方案**

- 把“个人中心入口 -> 本地歌曲独立页 -> 扫描结果缓存 -> 右上角扫描”同步进 proposal/design/spec/tasks。
- 明确页面第一次无缓存时可以首扫，有缓存时下次打开优先秒显缓存，不依赖后台自动刷新。

**Step 2: 校验变更工件**

Run: `source ~/.nvm/nvm.sh >/dev/null 2>&1 && nvm use 20 >/dev/null && openspec validate enhance-playlist-playback-and-musicinfo --type change`

Expected: `Change 'enhance-playlist-playback-and-musicinfo' is valid`

### Task 2: 先写失败测试

**Files:**
- Modify: `/Users/wxy/Projects/player-lite/app/src/test/java/com/wxy/playerlite/feature/main/UserCenterScreenRobolectricTest.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/test/java/com/wxy/playerlite/feature/local/LocalSongsViewModelTest.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/test/java/com/wxy/playerlite/feature/local/LocalSongsScreenRobolectricTest.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/test/java/com/wxy/playerlite/feature/local/LocalSongsSnapshotStorageTest.kt`

**Step 1: 写失败测试**

- 个人中心页应展示“本地歌曲”入口并在点击时触发回调。
- 本地歌曲 ViewModel 在权限允许且已有缓存时应直接展示缓存而不强制首扫。
- 本地歌曲页应展示右上角扫描入口、缓存列表和“播放全部”入口。
- 缓存存储应能把扫描结果写入并在下次读取时恢复。

**Step 2: 运行失败测试确认变红**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wxy.playerlite.feature.main.UserCenterScreenRobolectricTest" --tests "com.wxy.playerlite.feature.local.LocalSongsViewModelTest" --tests "com.wxy.playerlite.feature.local.LocalSongsScreenRobolectricTest" --tests "com.wxy.playerlite.feature.local.LocalSongsSnapshotStorageTest"`

Expected: FAIL，提示缺少本地歌曲入口、本地歌曲页面/状态或缓存实现

### Task 3: 实现本地歌曲仓储与缓存

**Files:**
- Create: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/local/LocalSongsRepository.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/local/LocalSongsModels.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/local/LocalSongsSnapshotStorage.kt`

**Step 1: 实现缓存快照**

- 用 SharedPreferences + kotlinx.serialization 保存本地歌曲扫描快照。
- 保证能够稳定读写 `id / contentUri / title / artist / album / durationMs`。

**Step 2: 实现 MediaStore 扫描**

- 查询音频媒体列表并映射为轻量本地歌曲模型。
- 扫描成功后回写缓存，供下次进入直接展示。

### Task 4: 实现本地歌曲页与权限/扫描流程

**Files:**
- Create: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/local/LocalSongsActivity.kt`
- Create: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/local/LocalSongsViewModel.kt`
- Modify: `/Users/wxy/Projects/player-lite/app/src/main/AndroidManifest.xml`

**Step 1: Activity 宿主**

- 新增本地歌曲 Activity。
- 处理读取音频权限申请与结果回调。

**Step 2: ViewModel 状态**

- 权限可用时先读缓存；若缓存为空则首扫。
- 用户点击右上角扫描入口时触发重新扫描并更新缓存。

**Step 3: Compose 页面**

- 顶部返回按钮 + 右上角扫描按钮。
- 支持权限态、加载态、空态、错误态、列表态。
- 列表态提供“播放全部”和“点击某首歌播放”。

### Task 5: 个人中心入口与播放接线

**Files:**
- Modify: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/main/MainShellScreen.kt`
- Modify: `/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/MainActivity.kt`

**Step 1: 个人中心入口**

- 在个人中心资料头部和内容面板之间增加“本地歌曲”入口。
- 登录态和游客态都可见。

**Step 2: 播放接线**

- 本地歌曲页复用 `RuntimeDetailPlaybackGateway`。
- `播放全部` 和单曲点击都走“替换当前播放列表并指定激活项”的统一语义。

### Task 6: 验证与任务回写

**Files:**
- Modify: `/Users/wxy/Projects/player-lite/openspec/changes/enhance-playlist-playback-and-musicinfo/tasks.md`

**Step 1: 跑定向测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.wxy.playerlite.feature.main.UserCenterScreenRobolectricTest" --tests "com.wxy.playerlite.feature.local.LocalSongsViewModelTest" --tests "com.wxy.playerlite.feature.local.LocalSongsScreenRobolectricTest" --tests "com.wxy.playerlite.feature.local.LocalSongsSnapshotStorageTest"`

Expected: PASS

**Step 2: 跑仓库要求验证**

Run: `./gradlew :playback-service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS

**Step 3: 更新任务状态并复查 apply**

Run: `source ~/.nvm/nvm.sh >/dev/null 2>&1 && nvm use 20 >/dev/null && openspec instructions apply --change "enhance-playlist-playback-and-musicinfo" --json`

Expected: 本地歌曲页面相关任务被标记完成，剩余未完成项只保留该 change 其他范围内的任务
