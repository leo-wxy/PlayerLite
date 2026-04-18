## 1. OpenSpec 对齐

- [x] 1.1 补齐 `unify-recent-and-queue-song-actions` 的 proposal、design、specs 与 tasks，明确范围覆盖最近播放和播放队列
- [x] 1.2 将最近播放与播放队列的单曲次级入口区分为“最近播放弱详情入口”和“队列语义变体”，并在 spec 中写清

## 2. 最近播放交互收口

- [x] 2.1 为最近播放歌曲补齐 `songId / primaryArtistId / albumId` 等歌曲专属建模
- [x] 2.2 将最近播放列表主点击收口为“按当前最近播放列表直接播放”
- [x] 2.3 将最近播放右侧三点收口为直达歌曲详情的弱入口
- [x] 2.4 补齐最近播放 repository、intent 与 UI 定向测试

## 3. 播放队列更多菜单

- [x] 3.1 为共享 `PlaylistItem` 与在线歌曲队列投影补齐稳定 `albumId`
- [x] 3.2 将播放列表半浮层的单独删除按钮改为三点更多入口
- [x] 3.3 为播放队列更多菜单接通“查看歌曲详情 / 查看歌手 / 查看专辑 / 移出播放队列”
- [x] 3.4 补齐播放队列更多菜单的 UI 与行为测试

## 4. 验证与回归

- [x] 4.1 运行最近播放与播放队列相关的定向单元 / Robolectric 测试
- [x] 4.2 运行默认回归验证，确保 `:playback-service:testDebugUnitTest`、`:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过
