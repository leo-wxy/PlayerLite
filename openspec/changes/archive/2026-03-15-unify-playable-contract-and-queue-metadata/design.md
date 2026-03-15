## Context

播放器的共享数据目前同时承担了三类职责：上游接口字段承载、跨进程传递和 UI 展示。这导致 `MusicInfo` 一边贴着 `/song/detail` 原始字段，一边又被 `MediaItem extras`、远端快照和播放页直接消费。后续再引入本地 `LocalMusicInfo`、异步元数据 enrichment 或持久化恢复时，边界会越来越模糊。

## Decisions

1. 共享播放契约与上层领域模型分层。
   - 在线歌曲继续使用语义更完整的 `MusicInfo`。
   - 本地歌曲使用 `LocalMusicInfo` 或等价本地领域模型。
   - 跨进程同步与播放服务只消费最小 `PlayableItem` / `PlayableItemSnapshot` 契约。

2. 上游接口字段通过显式 mapper 转成语义化命名。
   - `/song/detail` 返回的 `id`、`name`、`ar`、`al`、`dt` 等字段先映射为 `songId`、`title`、`artistNames`、`albumTitle`、`durationMs` 等领域字段。
   - 共享契约不直接暴露原始缩写字段。

3. 队列元数据支持播放后异步 enrichment。
   - 先用稳定 `songId` 与最小可播放信息建立队列并开始播放。
   - 后台按 item id 分页回填标题、歌手、专辑、封面与时长。
   - 元数据回填不改变当前队列顺序与激活项语义。

4. 远端快照成为播放器页主信息的首选来源。
   - 当前曲目的封面、时长、标题、歌手优先来自远端快照和当前队列投影。
   - 播放器页不再额外依赖详情页或单独查询来补主视觉信息。

## Verification

- 为 `MusicInfo` / `LocalMusicInfo` 到共享播放契约的投影补单元测试
- 为 `/song/detail` mapper 与跨进程 extras / 快照映射补测试
- 为按 item id 异步回填元数据且不重建队列的行为补回归测试
