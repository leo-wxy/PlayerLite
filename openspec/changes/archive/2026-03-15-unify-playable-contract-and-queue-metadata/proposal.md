## Why

当前 `enhance-playlist-playback-and-musicinfo` 把共享播放契约、在线地址解析、详情页播放入口和播放页 UI 一次性揉在了一起，评审和归档边界都偏重。`MusicInfo` / `LocalMusicInfo` / `PlaylistItem` / 远端快照之间的共享字段投影，本身就是一条独立且可复用的基础能力，值得单独拆成一个 change。

## What Changes

- 定义统一的最小可播放契约，明确在线 `MusicInfo` 与本地 `LocalMusicInfo` 到共享播放契约的投影边界
- 把 `/song/detail` 等上游字段映射为语义化播放元数据，而不是把 `id`、`ar`、`al`、`dt` 这类原始缩写直接泄露到共享契约
- 让队列项的 `songId`、`durationMs`、`coverUrl`、请求头与上下文信息稳定跨进程传递
- 允许播放列表在已经开始播放后，按稳定 item id 异步回填标题、歌手、专辑、封面与时长，而不重建当前队列
- 让播放器页优先从远端快照和当前队列投影拿当前曲目的封面与时长，不再依赖详情页额外查询

## Capabilities

### New Capabilities
- `playable-contract-and-queue-metadata`: 统一共享播放契约、语义化元数据映射与异步队列元数据回填

### Modified Capabilities
- `playback-state-authority`: 当前曲目的时长与封面由共享快照直接投影到播放器 UI

## Impact

- `playback-contract` 中的共享模型与 mapper
- `playback-client` 的远端快照映射
- `app` 中的播放列表状态、持久化迁移与播放器页状态投影
- 相关单元测试与 Robolectric 回归
