## Why

当前仓库已经具备在线播放磁盘缓存能力，但播放器前台仍然只展示播放进度，不展示“当前歌曲已经缓存到哪里”。这会带来两个问题：一是用户在弱网、切回前台或等待首包时，无法直观看到缓存是否在推进；二是后续即便要补预热能力，也缺少一个可以直接验证效果的前台反馈面。

本次 change 只收口“当前播放项缓存进度展示”这一件事，不把下一首预热、整首下载、缓存中心或整队列缓存视图混进来。

## What Changes

- 新增当前播放项缓存进度的权威上报链路，由 playback service 输出当前歌曲缓存状态并通过远端快照投影给 app。
- 新增当前播放项缓存进度可视化规则，在 minibar 与播放页的同一条进度条上同时展示“已播放”和“已缓存未播放”两段比例。
- 明确完整缓存时缓存条直接拉满；当总量暂时未知时允许使用估算比例展示，并在拿到真实总量后纠正。
- 明确本次不包含下一首预热、整首下载、缓存管理页扩展、通知栏缓存视图和队列级缓存可视化。

## Capabilities

### New Capabilities
- `online-cache-progress-visualization`: 当前播放项需要向前台投影可解释的缓存进度，并在播放器最小栏与播放页上稳定展示。

### Modified Capabilities
- `playback-state-authority`: 远端快照需要增加当前播放项缓存进度字段，app 不得自行探测缓存文件或重复计算缓存状态来源。

## Impact

- `playback-service` 当前项缓存快照采集与上报链路
- `playback-contract` / `playback-client` 远端快照 extras 与 mapper
- `app` 的 `PlayerRuntime`、`PlayerUiState`、minibar 和播放页进度条绘制
- 缓存进度上报、快照映射、UI 投影与进度条显示的定向测试
