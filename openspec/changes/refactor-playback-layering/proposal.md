## Why

当前播放器的前台 `PlayerRuntime` 与后台 `PlaybackProcessRuntime` 同时承担了播放相关状态和决策，导致状态来源不够单一，新增倍速、自动切歌这类能力时需要跨前后台同时改动，容易出现 UI 显示时机、进度同步与状态覆盖问题。现在需要收紧边界，让后台播放进程成为唯一播放真相源，并把模块职责拆清楚，为后续播放模式等能力演进降低复杂度。

## What Changes

- 将后台 `playback-service` 明确为播放状态与播放决策的唯一权威层，统一负责队列、当前曲目、seek、倍速、自然播完后的推进策略等行为。
- 将 `app` 侧 `PlayerRuntime` 收紧为 UI 投影层，仅保留弹窗显隐、拖拽态、乐观 UI 与远端快照映射，不再持有播放决策职责。
- 新增 `:playback-contract` 模块，承载跨进程共享的播放协议、Session command、metadata extras 与共享 DTO。
- 新增 `:playback-client` 模块，承载 `MediaController` 连接、命令派发与远端快照访问，避免客户端桥接逻辑继续放在 `:playback-service` 内。
- 在不改变既有播放能力表现的前提下，调整依赖方向与代码组织，保持倍速、播放列表、自动切歌与后台播放行为一致。

## Capabilities

### New Capabilities
- `playback-state-authority`: 定义后台播放服务作为唯一播放真相源，以及前台 UI 通过远端快照进行状态投影的一致性要求。

### Modified Capabilities

## Impact

- Gradle 模块：新增 `:playback-contract`、`:playback-client`，并调整 `:app`、`:playback-service` 的依赖关系。
- 前台运行时：`/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/player/runtime/`
- 后台播放进程：`/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/`
- 播放客户端桥接与共享协议：从 `:playback-service` 迁出到新模块。
- 测试：需要补充模块边界、状态投影与回归验证，确保现有倍速、进度、列表与自动切歌行为保持不变。
