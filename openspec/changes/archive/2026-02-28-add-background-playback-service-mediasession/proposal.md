## Why

当前播放器主要依赖前台界面生命周期，应用退到后台或界面被回收后，播放控制与状态可见性不足，影响连续收听体验。引入独立播放 Service 与 MediaSession 可以让播放在后台稳定运行，并与系统级媒体控制能力对齐（通知栏、耳机按键、系统媒体中心）。

## What Changes

- 新增基于 Android `Service` 的后台播放宿主，承载播放器生命周期与播放控制入口。
- 引入 `MediaSession` 集成，向系统暴露标准播放状态、元数据和传输控制动作（播放/暂停、上一首、下一首）。
- 建立应用 UI 层与后台 Service 的状态同步和命令通道，确保前后台切换时控制一致。
- 增加后台播放场景下的生命周期与恢复策略（例如应用切后台、系统回收后重建）。
- 为后台播放与 MediaSession 交互补充测试覆盖和验证路径。

## Capabilities

### New Capabilities
- `background-playback-service`: 定义独立播放 Service 的启动、绑定、前台服务通知、生命周期与控制契约。
- `media-session-integration`: 定义 MediaSession 的状态映射、媒体按键处理、系统媒体控制互通与元数据更新行为。

### Modified Capabilities
- None.

## Impact

- 影响模块：`app` 播放控制层、播放器状态管理、系统通知与媒体会话桥接。
- 影响系统集成：Android 前台服务、通知渠道、音频焦点、MediaSession/系统媒体中心。
- 影响交互路径：UI 控件、通知栏控制、耳机/蓝牙设备媒体按键。
- 需要补充测试：后台切换稳定性、Service 重建后状态一致性、MediaSession 控制链路正确性。
