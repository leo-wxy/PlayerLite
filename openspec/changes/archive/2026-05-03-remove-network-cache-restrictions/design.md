## Context

设置页此前同时存在“仅 Wi-Fi 自动缓存”和“移动网络只播放不缓存”两个网络类型开关，播放进程会读取这些偏好来决定是否写入磁盘缓存、是否允许预热。现在产品方向变为移动网络也需要缓存能力，网络类型不再是缓存写入的开关条件。

## Goals / Non-Goals

**Goals:**
- 移除用户可见的网络类型缓存限制设置。
- 确保移动网络下主动在线播放仍写入 `cache_core` 磁盘缓存。
- 确保预热不再因为 Wi-Fi/移动网络类型被跳过。
- 保留缓存失败提示、缓存容量上限、预热开关和预热预算。
- 旧偏好即使已经持久化，也不再影响新版本播放行为。

**Non-Goals:**
- 不新增移动网络流量提醒或按网络类型限速。
- 不改变缓存容量淘汰策略。
- 不改变预热预算的默认值和 UI 形态。

## Decisions

### 决策一：移除设置入口和应用侧偏好模型

删除 `cacheOnlyOnWifi` 和 `mobileNetworkPlayWithoutDiskCache` 在设置页模型、仓库与 ViewModel 中的字段和写入路径。这样设置页不再能产生网络类型缓存限制，避免旧 UI 状态误导用户。

备选方案是隐藏 UI 但保留模型字段；这会让旧持久化值仍有机会在内部链路生效，因此不采用。

### 决策二：缓存策略命令只保留缓存失败提示

`setCachePolicyPreferences` 只传递 `showCacheFailureNotifications`。播放进程收到缓存策略命令后只更新失败提示偏好，不再更新移动网络缓存限制。

备选方案是继续传递 `mobileNetworkPlayWithoutDiskCache=false`；这会保留已废弃 contract，并让测试继续依赖无效参数，因此不采用。

### 决策三：播放准备链路始终允许网络源使用磁盘缓存

`TrackPreparationCoordinator` 不再注入 `shouldUseDiskCacheForNetworkPlayback`。只要播放计划不是纯缓存命中失败路径，在线播放源都使用 `CachedNetworkSource`，由 `cache_core` 负责写入与容量控制。

备选方案是在 runtime 中返回固定 `true`；这能修行为但留下无效网络判断和旧偏好读取，后续维护成本更高。

### 决策四：预热策略只看播放上下文和预算

`PlaybackPrewarmContext` 移除网络策略字段。预热是否启动只由预热开关、预算、当前项缓存完整度、队列候选和缓存容量机制决定。

备选方案是保留 `PlaybackPrewarmPolicy` 并让其永远允许；这会让“缓存策略允许自动缓存”的概念继续干扰阅读。

## Risks / Trade-offs

- 移动网络流量增加 → 通过缓存预算、容量上限和后续可能的流量提示控制，不再用网络类型直接禁用缓存。
- 旧版本持久化了禁用移动网络缓存 → 新版本忽略旧 key，不做迁移清理，回滚到旧版本时旧值仍可能生效。
- API 签名变化影响测试 fake 和内部调用 → 同步更新 orchestrator、client、service 和测试替身，避免编译期遗漏。
