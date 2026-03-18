# Player Follow-up Bugfixes Implementation Plan

> **For agentic workers:** REQUIRED: Use `systematic-debugging` 先定位根因，再按 TDD 执行下面的步骤。步骤使用 checkbox (`- [ ]`) 语法追踪。

**Goal:** 修复详情页切换播放列表后的首次播放卡在缓冲中、当前时间/状态投影异常，以及歌词页切换闪烁三个连续反馈问题。

**Architecture:** 先收敛播放启动链路，把详情页播放从“可能被页面生命周期中断的临时 bridge”切回稳定的 app/runtime 权威链路；随后修正 `PlayerRuntime` 对非权威远端快照的状态投影规则；最后单独处理 `PlayerScreen` 的歌词页切换闪烁，避免把 UI 问题和播放状态问题混修。前两个问题共享“状态权威与投影时机”这一大类根因，但测试要分别锁住，不能修一条就默认另一条也好了。

**Tech Stack:** Android ViewModel, Compose, Media3 `MediaController`, `PlayerServiceBridge`, app-side `PlayerRuntime`, Robolectric/JVM tests, adb logcat.

---

## Chunk 1: 首次切换播放列表后第一次播放卡缓冲，需要再次点击

### Task 1: 锁定详情页播放启动 race

**Files:**
- Create: `app/src/test/java/com/wxy/playerlite/feature/player/runtime/DetailPlaybackGatewayTest.kt`
- Modify: `app/src/main/java/com/wxy/playerlite/feature/player/runtime/DetailPlaybackGateway.kt`
- Reference: `app/src/main/java/com/wxy/playerlite/feature/playlist/PlaylistDetailViewModel.kt`
- Reference: `app/src/main/java/com/wxy/playerlite/feature/artist/ArtistDetailViewModel.kt`
- Reference: `app/src/main/java/com/wxy/playerlite/feature/album/AlbumDetailViewModel.kt`

- [ ] **Step 1: 写失败测试，复现“deferred queue sync 还没 flush 就被 close”**

  目标行为：
  - 详情页触发 `play(request)` 时，即使 controller 还未连接，首次播放请求也不能因为详情页 ViewModel `onCleared()` 而丢失。
  - 页面跳回 `MainActivity` 后，不允许出现“先 BUFFERING 但不自动进入可播放状态，必须再手动点一次播放”的行为。

  建议测试形态：
  - fake `PlayerServiceBridge`，让第一次 `syncQueue(...)` 进入 deferred 状态。
  - 紧接着调用 `close()` 模拟详情页销毁。
  - 断言 pending playback launch 仍能被保留，或者播放入口已经改为走长期存活的 bridge/runtime，不受 `close()` 影响。

- [ ] **Step 2: 只跑新测试，确认它先红**

  Run:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*DetailPlaybackGatewayTest"
  ```

  Expected:
  - FAIL，且失败原因明确指向“首次播放请求在 deferred 期间丢失/未真正触发”。

- [ ] **Step 3: 最小实现修复播放启动链路**

  实现方向固定为二选一，但只能落一种：
  - 优先方案：把“详情页发起播放”的真正 queue sync/start 责任切到长期存活的 app/runtime 播放桥接层，不再由详情页私有 bridge 承担。
  - 备选方案：如果仍保留详情页私有 bridge，则 `close()` 不能中断已经排队的首次播放请求，只能取消 metadata enrichment 之类的附属任务。

  约束：
  - 不要在详情页 ViewModel 里再加第二套播放状态真相。
  - 不要通过“多补一次 play()”掩盖第一次请求丢失。

- [ ] **Step 4: 重新跑新测试，确认转绿**

  Run:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*DetailPlaybackGatewayTest"
  ```

  Expected:
  - PASS。

### Task 2: 用设备日志回归首次播放场景

**Files:**
- Reference: `/tmp/playerlite_now.log`
- Reference: `playback-client/src/main/java/com/wxy/playerlite/playback/client/PlayerServiceBridge.kt`

- [ ] **Step 1: 按同一路径手动复现一次**

  复现路径：
  - 从歌单/歌手/专辑详情页点击某首歌或“播放全部”
  - 跳回 `MainActivity`
  - 观察第一次是否直接进入 `PLAYING`

- [ ] **Step 2: 回捞日志确认不再出现旧模式**

  Run:
  ```bash
  adb logcat -d -v threadtime > /tmp/playerlite_after_first_play_fix.log
  rg -n "PlayerServiceBridge|MediaController|MediaSessionService|PlaybackProcessRuntime|TrackPrep|BUFFERING|PLAYING" /tmp/playerlite_after_first_play_fix.log | tail -n 400
  ```

  Expected:
  - 不再出现“`Controller unavailable; queue sync deferred` 后马上 release 导致首次点击无效”的模式。
  - 详情页首次点击后能够自然从 `BUFFERING` 进入 `PLAYING`，无需第二次点播放。

---

## Chunk 2: 当前时间/状态投影异常

### Task 3: 锁定非权威远端快照覆盖本地恢复状态的问题

**Files:**
- Modify: `app/src/test/java/com/wxy/playerlite/feature/player/runtime/PlayerRuntimeInteractionTest.kt`
- Modify: `app/src/main/java/com/wxy/playerlite/feature/player/runtime/PlayerRuntime.kt`

- [ ] **Step 1: 写失败测试，锁住“空远端快照不能抹掉本地恢复状态”**

  目标行为：
  - 当前播放列表和激活项已经由 app 本地恢复时，如果远端只给出非权威快照（例如没有 `currentMediaId/currentPlayable`，或者刚切队列时过渡态只报 `position=0`），UI 不应立刻把当前时间/标题/歌手/封面投影成空或错误值。
  - 真正的权威远端快照到来后，才允许覆盖本地投影。

  最少要覆盖的字段：
  - `displayedSeekMs` / 当前时间
  - `currentTrackTitle`
  - `currentTrackArtist`
  - `currentCoverUrl`

- [ ] **Step 2: 只跑运行时交互测试，确认先红**

  Run:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*PlayerRuntimeInteractionTest"
  ```

  Expected:
  - FAIL，且能明确看到 stopped/paused 的空快照把本地投影错误覆盖。

- [ ] **Step 3: 在 `PlayerRuntime.updateRemotePlaybackState()` 做最小修复**

  修复边界：
  - 当远端快照缺少当前媒体身份时，保留本地已恢复的播放项投影，不把当前时间/封面/歌手直接清空。
  - 当远端快照带有明确 `currentMediaId` 或 `currentPlayable` 时，再按远端权威值覆盖。
  - 不要把“本地恢复值”永久锁死，避免真正切歌时不刷新。

- [ ] **Step 4: 重新跑目标测试，确认转绿**

  Run:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*PlayerRuntimeInteractionTest"
  ```

  Expected:
  - PASS。

### Task 4: 用第一次进入首页/播放器场景验证“当前时间”问题已经消失

**Files:**
- Reference: `app/src/main/java/com/wxy/playerlite/MainActivity.kt`
- Reference: `app/src/main/java/com/wxy/playerlite/feature/main/MainShellScreen.kt`

- [ ] **Step 1: 冷启动或从详情页回到主页面后复测**

  检查点：
  - 不按播放键时，当前条目的时间/标题/封面不应瞬间掉成空白或错误值。
  - 如果服务端尚未给出权威播放中快照，UI 至少要保持本地恢复的稳定展示。

- [ ] **Step 2: 回捞日志，确认不再出现“位置从有效值瞬间跳回 0 且 active item 异常切换”的模式**

  Run:
  ```bash
  adb logcat -d -v threadtime > /tmp/playerlite_after_state_fix.log
  rg -n "MediaSessionService: onSessionPlaybackStateChanged|position=|active item id=" /tmp/playerlite_after_state_fix.log | tail -n 200
  ```

  Expected:
  - 不再出现和本次现场一致的 `4578 / item 5 -> 0 / item 1` 异常跳变链路，除非真的是用户主动切换到新队列。

---

## Chunk 3: 歌词页切换闪烁

### Task 5: 锁定歌词页切换闪烁的具体来源

**Files:**
- Modify: `app/src/test/java/com/wxy/playerlite/feature/player/ui/PlayerScreenRobolectricTest.kt`
- Modify: `app/src/main/java/com/wxy/playerlite/feature/player/ui/PlayerScreen.kt`

- [ ] **Step 1: 先确认闪烁属于哪一种**

  只允许三类根因，先定位再修：
  - `HorizontalPager` 与 `selectedTopTab` 双向同步导致的重复跳页 / 短暂回弹
  - 歌词页和歌曲页切换时内容层被重建，导致 placeholder 或 alpha 闪一下
  - 歌词高亮/自动滚动的副作用在切页首帧被重复触发

- [ ] **Step 2: 写失败 UI 测试，锁住“切换 tab 不应闪到错误页或空态”**

  建议断言：
  - 点击 `歌词` tab 后，当前页稳定停在歌词页，不应瞬间回到歌曲页
  - 左右滑切换时，歌词列表容器不应短暂消失成 placeholder
  - 切回 `歌曲` 再切到 `歌词` 时，当前高亮行和滚动位置应保持稳定

- [ ] **Step 3: 只改 `PlayerScreen` 的同步/副作用时机**

  修复优先级：
  - 先消除重复页状态同步
  - 再避免歌词页切换时的整页重建
  - 最后再处理自动滚动触发条件

  约束：
  - 不改歌词获取逻辑
  - 不改目前已经确认过的整体布局方向
  - 不把 flicker 靠延时硬压掉

- [ ] **Step 4: 重新跑目标 UI 测试**

  Run:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*PlayerScreenRobolectricTest"
  ```

  Expected:
  - 相关切页测试 PASS，且不引入新的 pager 回归。

---

## Chunk 4: 最终回归验证

### Task 6: 跑完整验证并做一次人工串联复测

**Files:**
- Modify: `openspec/specs/player-expanded-page/spec.md`（若歌词 flicker 行为语义有变化）
- Modify: `openspec/specs/playback-state-authority/spec.md`（若状态投影规则有变化）
- Modify: `openspec/specs/playlist-detail-page/spec.md` / `artist-detail-page/spec.md` / `album-detail-page/spec.md`（若首次播放语义需补充）

- [ ] **Step 1: 只在实现与现有 spec 语义不一致时更新 OpenSpec**

  原则：
  - 行为变了再写 spec
  - 只是修 bug、不改语义时不要硬改文案

- [ ] **Step 2: 跑仓库要求的完整验证**

  Run:
  ```bash
  ./gradlew :playback-service:testDebugUnitTest
  ./gradlew :app:testDebugUnitTest
  ./gradlew :app:assembleDebug
  ```

  Expected:
  - 全绿。

- [ ] **Step 3: 跑 OpenSpec 校验**

  Run:
  ```bash
  PATH="/Users/wxy/.nvm/versions/node/v20.20.0/bin:$PATH" openspec validate --specs
  ```

  Expected:
  - 全绿。

- [ ] **Step 4: 做一轮人工串联复测**

  必测路径：
  - 歌单/歌手/专辑详情页点击播放，首次即能播，不需要第二次点播放
  - 回到主页面时当前时间稳定，不被错误清空/跳回 0
  - 歌词 tab 点击和横滑切换不闪烁

