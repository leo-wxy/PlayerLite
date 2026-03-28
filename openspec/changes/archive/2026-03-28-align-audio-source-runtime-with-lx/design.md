## Context

当前仓库已经有“可导入、可展示、可切换”的音源管理壳，但真正进入播放链路的仍然只是 `preferredAudioSourceBaseUrl`。这意味着：

- 设置页管理的是地址，不是音源能力。
- playback-service 仍在固定的 `/song/url/v1`、`/song/url`、`/song/music/detail` 协议上替换 host。
- 在线队列项的 `playbackUri` 仍是空串占位，真可播地址在准备阶段临时解析，但解析入口尚未抽象成 source runtime。
- 未来如果继续补歌词、封面或更复杂的解析源，会被迫把当前 `baseUrl` 模型整体推翻。

LX Music 公开出来的稳定边界更接近“初始化 + action 分发 + source 返回结果”的运行时模型，而不是“一个可切换 baseUrl”。这次设计的目标，是先对齐这个运行时边界，再把当前仓库里已经存在的 `netease-compatible` 逻辑收编为首个原生 adapter。

## Goals / Non-Goals

**Goals:**
- 把当前音源从“地址配置”升级成“source package + native action-based adapter”。
- 将 playback-service 中的在线播放准备改为消费统一的 `SourceActionResult.MusicUrl`，而不是直接拼固定网易兼容接口。
- 首版支持两类原生 source：
  - `netease-compatible`
  - `http-mapping`
- 保持设置页的导入、展示、切换、错误状态能力，但把其底层数据模型切换到 `metadata + config + state`。
- 保留旧 manifest、旧 `preferred_audio_source_base_url` 和旧持久化 source 列表的读取兼容。

**Non-Goals:**
- 不引入 JS 执行引擎，不兼容 LX Music 脚本生态。
- 不让 source 接管搜索、歌单、专辑、歌手或在线详情页。
- 首版不实现 `ResolveLyric`、`ResolvePic`，只保留 action 入口。
- 不支持多步搜索/匹配/签名/抓取类复杂源。
- 不在本次变化中处理默认音质弹窗 UI 或重做播放器页的音质展示。

## Decisions

1. 先对齐 LX 的运行时边界，而不是对齐它的脚本语法。
   - 新增 `SourceMetadata`、`SourceState`、`SourceConfig`、`SourceAction`、`SourceActionContext`、`SourceActionResult` 与 `SourceAdapter` 抽象。
   - `SourceAdapter` 统一暴露 `init()` 和 `handle(action, context)` 两个入口，保证未来无论继续走原生 adapter 还是引入 JS runtime，都不需要重写播放主流程。
   - 备选方案是把当前实现先收成单一 `MusicUrlResolver` 接口，但这样后续增加歌词、封面等 action 时仍要再升格一次，因此不选。

2. 音源导入模型升级为 source package，resolver 只是其中一个能力配置。
   - manifest 顶层至少包含 `name`、`author`、`version`、`runtime`、`resolver`。
   - `runtime.type` 首版只支持 `native`。
   - `resolver.type` 首版只支持 `netease-compatible` 与 `http-mapping`。
   - 旧 manifest 中只有 `type/baseUrl/name/author/version` 的格式，在读取时自动映射为 `runtime.type = native` 且 `resolver.type = netease-compatible`。
   - 这样可以避免把“source”和“resolver”混成同一个层级，给未来扩 `lyricResolver`、`picResolver` 留出结构空间。

3. 当前 source 的激活以“配置有效 + adapter 可本地初始化”为成功条件，不做远端可用性探活。
   - 设置页切换 source 后，播放客户端通过稳定边界把 `activeSourceConfigJson` 下发到 playback-service。
   - playback-service 解析 config、构造 adapter、执行本地 `init()`；只有这一步成功，才确认切换生效。
   - `init()` 首版只做本地校验与轻量准备，不做远端网络探测，避免切换操作被瞬时网络波动放大。
   - 如果 config 无法解析、runtime type 不支持或 adapter 初始化失败，则命令返回失败，保持之前的当前 source 不变。
   - 备选方案是“先切换后再异步标记 initError”，但这会制造“已选中 source 但运行时并未真正接管”的双真相，因此不选。

4. 首版 action 面只开放 `ResolveMusicUrl`，并把默认音质作为偏好输入而不是硬前置依赖。
   - `SourceAction` 先保留 `ResolveMusicUrl`、`ResolveLyric`、`ResolvePic` 三个 action。
   - 首版仅 `ResolveMusicUrl` 可用；其余 action 返回显式 `UnsupportedOperationException`。
   - `ResolveMusicUrl` 的上下文必须带齐 `songId`、`title`、`artistText`、`albumTitle`、`durationMs`、`preferredAudioQuality`、`requestHeaders`、`previewClip`。
   - 当前默认音质继续通过既有 `PlaybackAudioQuality` 进入上下文，但 source 无法满足该音质时，允许降级为可播结果，不让 `/song/music/detail` 成为播放成功的硬前置。

5. 现有网易兼容实现全部收编到 `NeteaseCompatibleSourceAdapter`，不再散落在播放主流程里。
   - 当前写死在在线播放准备链路里的 `/song/url/v1`、`/song/url`、返回值解析、过期时间和试听片段逻辑，都迁入 `NeteaseCompatibleSourceAdapter`。
   - `OnlinePlaybackPreparationPlanner` 不再直接依赖固定网易兼容 remote data source，而是只依赖“当前 source adapter 返回 `MusicUrl` 结果”。
   - 这样后续增加新的 native source type 时，不需要再改动 `TrackPreparationCoordinator` 或播放状态机。

6. `http-mapping` 首版被严格限定为“单次 HTTP JSON 请求 -> 提取播放地址”。
   - 只支持 `GET` / `POST`。
   - 只支持单次请求。
   - 只支持 JSON 响应。
   - 只支持白名单模板变量：
     - `{songId}`
     - `{title}`
     - `{artistText}`
     - `{albumTitle}`
     - `{durationMs}`
     - `{quality}`
     - `{header.<name>}`
   - 只支持简单路径提取：`.` 与 `[index]`。
   - 不支持多步请求、脚本执行、HTML 抓取、签名表达式和通用 JSONPath。
   - 备选方案是把 mapping 做成通用 DSL，但这会很快演变成低配脚本系统，维护成本高于收益，因此不选。

7. 当前 source config 通过既有播放客户端边界跨进程下发，并采用“读旧写新”迁移。
   - `PlaybackSessionCommands`、`PlayerServiceController` 和 `PlayerMediaSessionService` 从 `preferredAudioSourceBaseUrl` 升级为 `activeSourceConfigJson`。
   - playback-service 持久化当前 source config，并在服务重建后恢复。
   - 旧偏好键 `preferred_audio_source_base_url` 在读取时兼容映射成默认的 `NeteaseCompatibleSourceConfig`。
   - 旧的 source 列表持久化数据仍可读，但新版本只写入新结构。

8. 在线 URL 缓存和相关重准备逻辑按“当前 source identity”隔离。
   - 当前 source 一旦切换，runtime 立即清空旧 source 下的在线播放 URL 内存缓存与音质目录缓存。
   - 如果当前曲目是在线歌曲，则沿用现有音质切换的重准备语义：
     - 播放态切换后继续播放
     - 暂停态切换后保持暂停和位置
     - 本地曲目不触发在线重准备
   - 这里继续保留现有缓存失效与重准备模式，降低状态机风险。

9. App 侧音质目录 UI 首版继续作为 best-effort 视图，不阻塞 source runtime 迁移。
   - App 内已有 `SongAudioQualityRepository` 仍可暂时保留，当前 change 不要求它先完成 source-aware 改造。
   - playback-service 中的 `ResolveMusicUrl` 以“优先拿到可播地址”为主，不把 App 侧音质目录 UI 的一致性作为首版门槛。
   - 这样可以把本次变化的主风险锁定在“真可播地址解析”上，而不是同步重做所有在线元数据链路。

## Risks / Trade-offs

- [首版只做本地初始化，不探远端可用性] → 切源体验更稳定、命令语义更清晰；远端不可用时通过实际解析失败和 source detail message 暴露问题，而不是把瞬时网络抖动放大成切源失败。
- [action 模型比当前需求更宽] → 只开放 `ResolveMusicUrl`，其余 action 保留接口但返回显式不支持，先把主干边界定稳。
- [`http-mapping` 约束较严] → 只覆盖单次 JSON API 源，避免 DSL 失控；复杂源留给后续专用原生 adapter 或 JS runtime。
- [App 侧音质目录 UI 暂不 source-aware] → 将默认音质视为偏好提示值，保证“先播起来”优先于“所有展示完全同步”。
- [读旧写新会增加一段过渡逻辑] → 把兼容读取集中在仓库层和 runtime 偏好读取层，避免业务层散落双格式判断。

## Migration Plan

1. 新建 source runtime 抽象、manifest/source package 解析模型和 `activeSourceConfigJson` 跨进程命令。
2. 把设置页与 source 仓库从 `baseUrl` 模型升级到 `metadata + config + state` 模型，并加上旧 manifest / 旧偏好的兼容读取。
3. 将现有网易兼容解析链路收编为 `NeteaseCompatibleSourceAdapter`，新增 `HttpMappingSourceAdapter`。
4. 让 playback runtime 持有当前 adapter，在线播放准备改为消费 `ResolveMusicUrl` 结果。
5. 保留并复用现有切源缓存失效与在线曲目重准备逻辑，完成测试覆盖后再考虑第二阶段 action。
6. 如需回滚，可强制播放层只构造内置 `NeteaseCompatibleSourceAdapter`，并忽略非内置 source config；设置页仍可继续保留 source package 展示数据。

## Open Questions

- 第二阶段是否需要把 App 侧 `SongAudioQualityRepository` 改造成 source-aware，以便播放器页展示的音质目录与当前 source runtime 完全一致。
- `http-mapping` 后续是否需要受控地增加 `form-urlencoded` 或多路径 fallback 支持，还是直接把复杂场景收口到专用原生 adapter。
