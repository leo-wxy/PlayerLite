## Context

当前设置页已经支持歌曲缓存上限、默认音质和播放策略开关。播放服务使用 `CacheCoreConfig.diskCacheMaxBytes` 控制磁盘上限，在线播放计划在发现完整缓存时已经会使用 `useCacheOnlyProvider = true`，因此“完整缓存优先本地播放”应作为默认行为保留，不需要额外开关。

首版缓存策略聚焦两件实际可落地的控制：移动网络下是否写入磁盘缓存，以及缓存失败是否提示。缓存清理策略首版只展示现状和契约，不引入收藏优先保留，避免在没有 cache entry priority metadata 的情况下做伪设置。

## Decisions

- 缓存策略模型复用播放偏好存储 `player_playback_preferences`，新增字段：
  - `mobile_network_play_without_disk_cache`
  - `show_cache_failure_notifications`
- 设置页将这两个字段放在“播放与缓存”分组中，和已有 `cacheOnlyOnWifi` 区分：
  - `cacheOnlyOnWifi` 约束后续自动缓存/预热任务。
  - `mobileNetworkPlayWithoutDiskCache` 约束用户主动在线播放时是否写入磁盘缓存。
- 播放进程新增缓存策略命令，只下发播放进程需要即时生效的字段。
- 移动网络策略只影响磁盘写入，不阻断播放；用户主动播放必须仍然可起播。
- 缓存失败提示只覆盖缓存链路异常，不替代现有播放失败态。
- 清理策略首版保持容量上限 + LRU，不做收藏优先保留。

## Data Flow

1. 设置页加载 `SettingsPlaybackPreferencesRepository.readCachePolicyPreferences()`。
2. 用户切换缓存策略后，ViewModel 先持久化，再通过 `SettingsPlaybackController` 下发到播放进程。
3. 播放进程保存策略并在准备在线播放 session 时读取当前网络类型。
4. 若开启“移动网络只播放不缓存”且当前网络为移动网络，在线播放 session 使用内存模式或等价非磁盘模式。
5. 若缓存初始化或写入相关错误发生，播放进程在开启提示时发布明确状态文案，前台按现有状态提示链路展示。

## Error Handling

- 设置下发失败时，设置页保留已持久化值，并提示“播放进程重连后生效”。
- 网络状态不可判断时，不默认拦截磁盘缓存；按正常缓存策略执行，避免误伤播放。
- 缓存失败提示不得把播放失败伪装成缓存失败；只有缓存初始化、缓存写盘或 cache provider 明确失败时才提示。

## Testing

- 设置页测试覆盖新增开关展示和 ViewModel 持久化。
- 播放边界测试覆盖缓存策略命令下发。
- 播放服务测试覆盖移动网络策略关闭磁盘缓存和缓存失败提示开关持久化。
- 回归验证 `:app:testDebugUnitTest`、`:playback-service:testDebugUnitTest`、`:app:assembleDebug`。

## Non-Goals

- 不实现收藏歌曲优先保留。
- 不新增手动“清理时保留收藏缓存”选项。
- 不把完整缓存优先本地播放做成用户开关。
- 不实现后台预热时长或预热队列策略。
