## Context

当前播放器代码在前台 `app` 与后台 `playback-service` 之间存在职责重叠：`PlayerRuntime` 仍保留了部分播放协调与本地状态推导，而 `PlaybackProcessRuntime` 已经承担了后台播放、队列推进、倍速、自动切歌与 Session 状态发布。与此同时，`PlayerServiceBridge` 与共享播放协议仍放在 `:playback-service` 模块内，导致客户端访问层、共享契约层与服务实现层混在一起，模块语义不够清晰。

最近的倍速显示、进度外推与自动切歌修复表明，播放相关逻辑越接近后台唯一真相源，状态一致性越容易保证。因此这次重构需要同时解决两件事：一是把播放状态权威层彻底收口到后台服务；二是把模块边界拆清楚，避免 `app` 直接依赖服务实现细节。

## Goals / Non-Goals

**Goals:**
- 让 `playback-service` 成为播放状态与播放决策的唯一权威层。
- 将 `PlayerRuntime` 收紧为 UI 投影层，仅保留 UI 局部状态、乐观更新与远端快照映射。
- 新增 `:playback-contract` 与 `:playback-client`，把共享协议和客户端桥接从 `:playback-service` 中迁出。
- 保持现有用户可见行为不变，包括后台播放、倍速、不变调、播放列表与自然播完自动切歌。

**Non-Goals:**
- 本轮不把同步机制改为事件驱动，仍保留当前的轮询同步模型。
- 本轮不重写 native 播放内核、FFmpeg 管线或 `cache-core` 边界。
- 本轮不继续拆分 `playlist` 存储为独立模块，播放列表持久化仍暂留在 `app` 范围内。
- 本轮不引入新的播放模式能力（单曲循环、列表循环、随机播放等）。

## Decisions

### 1. 将共享协议与客户端桥接拆出服务模块

新增两个模块：
- `:playback-contract`：承载 `MusicInfo`、Session custom command、metadata extras、共享播放 DTO 与跨模块协议。
- `:playback-client`：承载 `PlayerServiceBridge`、`RemotePlaybackSnapshot` 及 `MediaController` 命令访问封装。

这样 `:app` 只依赖客户端访问层与共享协议层，而不再语义上依赖后台服务实现包。` :playback-service ` 则依赖共享协议层并继续实现服务端逻辑。

**Alternatives considered**
- 保持现有模块不变，只在包结构上整理：改动小，但职责边界仍然模糊，后续继续膨胀的概率高。
- 直接再拆更多模块（如 `playlist-core`）：长期可能更纯粹，但本轮范围过大，回归风险偏高。

### 2. 后台服务成为唯一播放真相源

`PlaybackProcessRuntime` 继续作为播放会话的唯一权威层，负责：
- 当前播放队列与 active item
- 上下首能力与自然播完后的推进策略
- 当前倍速、seek 位置、真实播放状态
- 对 `PlayerSessionPlayer` 暴露的会话状态

这意味着播放相关决策不再由 `PlayerRuntime` 做本地判断后再“同步过去”，而是由 `app` 发出意图，后台运行时更新权威状态，再由前台根据远端快照做 UI 投影。

**Alternatives considered**
- 彻底删除 `PlayerRuntime`：虽然边界最硬，但当前 UI 仍需要拖拽态、弹窗态、pending speed 等局部状态，直接删除会让 ViewModel 过重。
- 继续保留双 runtime 分担：短期改动更少，但不能从根上解决状态一致性与职责漂移问题。

### 3. `PlayerRuntime` 收紧为 UI 投影层

`PlayerRuntime` 本轮保留的职责：
- 弹窗显隐、列表面板显隐
- seek 拖拽态、本地进度平滑
- 倍速乐观更新与失败回滚的 UI 层状态
- 前台播放列表展示态与本地持久化协调
- 将远端快照映射到 `PlayerUiState`

`PlayerRuntime` 本轮移除或弱化的职责：
- 直接持有播放决策
- 依赖本地播放协调器判断真实播放状态
- 作为播放真相源决定 active item、完成态或上下首能力

**Alternatives considered**
- 保持当前 `PlayerRuntime` 结构，只补更多同步逻辑：会继续加剧状态镜像和补丁式修复。

### 4. `PlayerSessionPlayer` 保持适配层定位

`PlayerSessionPlayer` 继续负责两类事情：
- 将 `PlaybackProcessRuntime` 状态映射为 Media3 `State`
- 将 Media3 命令转发到后台 runtime

新的播放业务规则不继续堆进这个类里，避免 Session 适配层演变为另一份业务层。

### 5. 渐进迁移而不是一次性重写同步机制

本轮仍保留 `PlayerViewModel` 通过 `PlayerServiceBridge` 的轮询同步模型，这样可以把重构风险控制在“职责与模块边界”范围内。等服务权威边界稳定后，再评估是否把桥接同步改为 `MediaController.Listener` 驱动的事件流。

## Risks / Trade-offs

- **[Risk] UI 与播放列表编辑状态仍部分保留在 `app`** → 通过明确“播放会话状态由后台权威、UI 列表编辑与持久化由前台负责”的边界降低歧义，并为后续 `playlist-core` 抽离留出空间。
- **[Risk] 模块迁移会带来 import、测试与 Gradle 配置回归** → 先迁协议与桥接，再迁调用点，最后跑完整构建与回归测试。
- **[Risk] 轮询模型仍可能存在刷新延迟** → 本轮只解决职责边界，不同时重写同步机制，避免引入更大范围的不稳定。
- **[Trade-off] 本轮不会得到“最理想的最终架构”** → 但能以较低风险换来更清晰的真相源和更健康的模块依赖，为下一步事件驱动或播放模式策略抽象打基础。

## Migration Plan

1. 新增 `:playback-contract` 与 `:playback-client`，迁移共享协议与客户端桥接代码。
2. 调整 `settings.gradle.kts` 与模块依赖，确保 `:app` 不再语义上依赖 `:playback-service` 内部实现类型。
3. 收紧 `PlayerRuntime`，让播放命令统一经 `PlayerServiceBridge` 进入后台服务。
4. 校正 `PlaybackProcessRuntime` 与 `PlayerSessionPlayer` 的职责边界，确保会话状态以后台 runtime 为唯一来源。
5. 增加回归测试并运行现有播放器相关测试与构建任务。

## Open Questions

- 播放列表持久化后续是否需要抽成独立 `playlist-core` 模块，而不是继续放在 `app` 中。
- `PlayerServiceBridge` 在边界收紧后，下一轮是否要升级为事件驱动同步，以进一步减少轮询补偿逻辑。
