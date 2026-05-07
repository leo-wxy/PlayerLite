## Why

当前设置页已经承载默认音质、缓存上限和音源管理，但播放恢复、断点续播、缓存策略和弱网重试仍是隐式行为，用户无法按自己的网络与使用习惯控制。在线音乐能力继续增强后，这些开关需要成为可见、可持久化、可下发到播放链路的基础设置。

## What Changes

- 在设置页“播放与缓存”分组新增四个播放/缓存策略开关：
  - 启动后恢复上次播放
  - 断点续播
  - 仅 Wi-Fi 自动缓存
  - 弱网自动重试
- 将这些设置持久化到现有播放偏好存储中，并在设置页打开时恢复展示。
- 将可立即影响播放进程的设置通过现有播放客户端边界下发。
- 将“启动恢复”和“断点续播”接入现有播放列表/播放会话恢复逻辑。
- 将“弱网自动重试”接入现有播放服务有限重试逻辑。
- “仅 Wi-Fi 自动缓存”首版作为自动缓存/预热策略偏好落地，不改变当前用户主动播放时的边播边缓存基础能力。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `settings-page-playback-preferences`: 设置页新增播放/缓存策略开关，并持久化与展示当前值。
- `cache-management`: 增加仅 Wi-Fi 自动缓存策略偏好，约束后续自动缓存/预热行为。
- `background-playback-service`: 弱网自动重试开关控制播放服务的有限重试行为。
- `playlist-persistence`: 启动恢复和断点续播开关控制恢复队列与恢复位置的应用方式。

## Impact

- 影响范围：
  - `app/feature/main` 设置页 UI、ViewModel、偏好 repository、UI 测试。
  - `app/feature/player/runtime` 播放列表/会话恢复入口。
  - `playback-service` 播放客户端边界、MediaSession 自定义命令、播放进程 runtime retry 行为。
- 不改动：
  - 音源 manifest 格式。
  - 缓存文件结构。
  - 当前默认音质、音源切换和缓存上限语义。
  - 用户主动在线播放时的基础边播边缓存能力。
