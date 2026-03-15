## 1. 共享播放契约与 mapper

- [x] 1.1 收口 `MusicInfo`、`LocalMusicInfo`、`PlayableItem` / `PlayableItemSnapshot` 的职责边界，明确跨进程最小共享契约
- [x] 1.2 为 `/song/detail` 增加显式 mapper，把 `id`、`name`、`ar`、`al`、`dt` 等字段转换为语义化共享元数据

## 2. 队列状态与跨进程投影

- [x] 2.1 升级播放列表状态、持久化迁移与 `MediaItem extras`，确保 `songId`、`durationMs`、`coverUrl` 与必要请求头稳定跨进程
- [x] 2.2 更新远端快照与播放器页状态投影，让当前曲目的封面与时长直接来自共享快照和当前队列
- [x] 2.3 为按 item id 异步回填队列元数据提供状态更新通道，不改变当前队列顺序与激活项

## 3. 验证

- [x] 3.1 为共享契约投影、语义化 mapper、远端快照映射和异步队列元数据回填补充测试
- [x] 3.2 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
