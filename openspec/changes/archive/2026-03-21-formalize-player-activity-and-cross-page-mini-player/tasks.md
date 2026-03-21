## 1. 独立播放器宿主与启动契约

- [x] 1.1 让 `PlayerActivity` 成为完整播放器的唯一宿主，并校准 `PlaybackLaunchRequest` 对 `openPlaylist` / `startPlayback` 的初始动作语义
- [x] 1.2 更新 `MainActivity`、本地歌曲返回路径和通知/锁屏内容入口，统一通过独立 `PlayerActivity` 打开播放器，而不是继续依赖 Home 域内展开 surface
- [x] 1.3 清理首页壳层中遗留的 Home 域播放器展开假设与不再使用的兼容入口，保证首页只保留 `minibar` 入口职责

## 2. 跨页面 detail mini player chrome

- [x] 2.1 基于共享 detail chrome 建立稳定的底部 `minibar` 宿主与内容预留契约，保证 overlay 不遮挡正文内容
- [x] 2.2 将专辑、歌手、歌单详情页统一接入共享 `minibar`，补齐主体点击、播放切换、播放列表入口与横滑切歌行为
- [x] 2.3 校准 detail 页底部 overlay 与吸顶/滚动逻辑的关系，避免 `minibar` 改变正文顶部吸顶计算

## 3. 播放列表入口、歌词摘要与 MediaSession 同步

- [x] 3.1 让首页播放列表入口通过打开 `PlayerActivity` 并默认展开列表 sheet 来访问当前播放列表，同时让详情页播放列表入口在当前页共享 chrome 中直接展开同一份列表
- [x] 3.2 统一首页 `minibar`、详情页 `minibar` 与 `MediaSession` 的当前歌词摘要投影与标题回退规则
- [x] 3.3 更新 `PlayerMediaSessionService` 与相关展示状态映射，保证系统媒体标题、次级信息和 app 内播放器语义一致

## 4. 回归与死代码清理

- [x] 4.1 补齐或更新 `PlayerActivity` launch contract、首页/详情页 `minibar`、播放列表入口和歌词摘要相关测试
- [x] 4.2 删除独立播放器迁移后不再使用的旧 Home 域播放器辅助代码、旧入口封装和过时测试
- [ ] 4.3 运行 `./gradlew :app:testDebugUnitTest`、`./gradlew :playback-service:testDebugUnitTest` 和 `./gradlew :app:assembleDebug` 验证整条链路

当前状态（2026-03-21）：
- `minibar` 主链相关实现与定向回归已完成，包括：首页 `minibar` 打开独立 `PlayerActivity`、首页播放列表按钮通过 `PlayerActivity(openPlaylist = true)` 打开列表、详情页 `minibar` 主体打开独立 `PlayerActivity`、详情页播放列表按钮在当前页本地打开列表 sheet、首页/详情页/`MediaSession` 共享歌词摘要与标题回退规则。
- 已通过验证：
  - `./gradlew :app:testDebugUnitTest --tests "*PlaybackDetailChromeRobolectricTest" --tests "*DetailPlaybackNavigationTest" --tests "*MusicDetailScaffoldRobolectricTest.detailMiniPlayerBar_clickCard_shouldDispatchOpenPlayer" --tests "*MusicDetailScaffoldRobolectricTest.detailMiniPlayerBar_localButtons_shouldDispatchIndependentCallbacks" --tests "*MusicDetailScaffoldRobolectricTest.detailMiniPlayerBar_shouldPreferCurrentLyricInSingleLine" --tests "*HomeOverviewScreenRobolectricTest.miniPlayerBar_shouldPreferCurrentLyricInSingleLine" --tests "*HomeOverviewScreenRobolectricTest.miniPlayerBar_playlistButton_shouldDispatchOpenPlaylistRequestWithoutOpeningPlayer" --tests "*LyricDisplayProjectionTest" --tests "*PlayerActivityLaunchRequestTest"`
  - `./gradlew :playback-service:testDebugUnitTest`
  - `./gradlew :app:assembleDebug`
- `4.3` 仍未勾选，因为 `./gradlew :app:testDebugUnitTest` 目前只剩 3 条与本 change 无关的既有失败：
  - `ArtistDetailScreenRobolectricTest.selectingTabs_shouldSwitchBetweenShellPanelsWithoutNeedingAlbumData`
  - `ArtistDetailScreenRobolectricTest.switchingTabs_afterScrollingHotSongs_shouldResetToAlbumsTop`
  - `ArtistDetailScreenRobolectricTest.selectingTabs_beforeStickyHeaderPins_shouldNotForceWholePageToTop`
