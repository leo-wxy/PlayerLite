## Context

当前仓库已经在设置页侧补了音源列表、默认音质和缓存管理的初步 UI / ViewModel / 持久化能力，但这些能力和播放进程仍存在明显断层：

- 设置页已经能保存 `preferredAudioSourceBaseUrl`、默认音质和歌曲缓存上限，但播放服务没有完整接住这些跨进程命令。
- 在线播放解析链路仍在 `PlaybackProcessRuntime` 内写死 `JsonHttpClient(baseUrl = API_BASE_URL)`，导致当前启用音源并不会真实参与 `/song/music/detail`、`/song/url/v1`、`/song/url` 的请求。
- 设置页当前的音源条目模型只够支撑“新增一条地址”，还不够表达 manifest 元数据、启用状态、当前音源和初始化错误。
- `clearManagedCache()` 已经补了等待新快照的逻辑，但仍需要和播放进程中的清理命令、缓存核心容量配置一起收口成完整链路。

这次变化横跨 `app`、`playback-client`、`playback-orchestrator`、`playback-service` 和 `cache-core` 使用方式，属于典型跨模块配置下发 + 在线解析链路改造。

## Goals / Non-Goals

**Goals:**
- 让设置页中的歌曲缓存上限、默认音质和当前音源都变成真实可作用于播放进程的偏好，而不是停留在本地表单。
- 把音源定义成原生 manifest 模型，支持本地/在线导入、名称/作者/版本展示、启用状态、当前音源标识和初始化错误反馈。
- 让在线播放的音质目录查询和 URL 解析都跟随当前启用音源的 baseUrl。
- 在切换当前音源后清理旧音源留下的 URL / 音质目录内存缓存，并对当前在线歌曲重准备。
- 保持默认音质继续复用现有 `PlaybackAudioQuality` 和 `setPreferredAudioQuality` 链路，不新增平行模型。
- 让歌曲缓存上限只控制 `cache_core`，并立即下发到播放进程。

**Non-Goals:**
- 不引入 LX Music 式 JS 脚本执行引擎，也不执行任意 JS 音源脚本。
- 首版不做 QQ / KG 等跨平台搜歌匹配，不把“音源”扩展成跨平台聚合搜索框架。
- 不新增独立云端同步、账号间共享或远程下发音源配置。
- 不为歌词缓存新增独立的容量上限设置。
- 不把当前变更扩展成完整的音源商店或插件市场。

## Decisions

1. 音源采用原生 manifest 模型，而不是 JS 脚本模型。
   - 在设置页侧将音源建模为固定字段：`name / author / version / baseUrl / type / enabled / active / initError / importUrl`。
   - 导入时只接受 `netease-compatible` 类型，manifest 负责提供展示元数据和解析基址，不执行任意代码。
   - 这样可以直接复用现有 Kotlin 网络层和播放链路，不需要额外引入 JS 引擎、请求桥接与脚本安全边界。
   - 备选方案是直接兼容 LX Music 式 JS 音源，但当前仓库没有现成 JS 运行时，成本和风险都过高，因此排除。

2. 当前音源配置通过现有播放客户端边界下发到播放服务，不新增旁路状态通道。
   - `PlaybackSessionCommands` 新增并真正接入“设置歌曲缓存上限”和“设置当前音源 baseUrl”命令。
   - `PlayerServiceBridge`、`PlayerServiceController`、`PlayerMediaSessionService`、`PlaybackProcessRuntime` 统一处理这两类命令。
   - 播放进程自身持久化 `preferredAudioSourceBaseUrl` 和 `playbackCacheLimitBytes`，确保服务重建后仍能恢复当前偏好。
   - 这样继续保持 `app` 不直接依赖 `playback-service` 实现细节，符合现有 `playback-service-boundary` 能力的边界要求。

3. 在线解析客户端改为“按当前音源动态构造”，而不是进程启动时固定一个全局 `JsonHttpClient`。
   - `PlaybackProcessRuntime` 保存当前启用音源 baseUrl，并把它提供给在线播放解析链路。
   - `OnlinePlaybackUrlResolver` 与 `CachedSongAudioQualityCatalogProvider` 通过 source-aware remote data source 访问 `/song/music/detail`、`/song/url/v1`、`/song/url`。
   - URL 内存缓存和音质目录缓存都按“当前音源 identity + songId + level + clipMode”隔离，或在切换当前音源时整体清空，防止旧音源结果串用到新音源。
   - 备选方案是保留固定 `JsonHttpClient`，在切换音源后强制重启整个播放服务；这虽然简单，但会破坏当前会话连续性，因此不选。

4. 切换当前音源时，对当前在线歌曲执行“清缓存 + 重准备”，保持播放/暂停语义不变。
   - 如果当前曲目具备 `songId`，说明它走在线播放解析链路；此时切换音源后必须清掉旧 URL / 音质目录缓存，并按当前状态重新准备这首歌。
   - 若切换前正在播放，则重准备完成后继续播放；若切换前暂停，则保留暂停态和当前位置。
   - 本地文件与 `content://` 音源不触发在线播放重准备，只更新当前音源配置。
   - 这样可以保证“当前音源真实参与解析”与“当前会话不中断”两者同时成立。

5. 歌曲缓存上限继续由 `CacheCoreConfig.diskCacheMaxBytes` 控制，设置页只负责修改目标值和下发。
   - 播放进程维护当前 `playbackCacheLimitBytes`，初始化 `CacheCore` 时使用该值。
   - 用户在设置页修改上限后，播放进程立即更新内存中的目标配置并尝试重配 `CacheCore`；如果重配失败，则保留已持久化值并在后续播放进程重连或重准备时继续生效。
   - 继续依赖 `cache-core` 现有的自动淘汰逻辑，不再在设置页堆第二套手工淘汰策略。
   - 备选方案是设置页只持久化、不即时通知播放进程，但这与用户预期的“改完立刻生效”不一致，因此不选。

6. 缓存清理成功反馈仍以后验快照为准，而不是以前置命令回执为准。
   - `SettingsViewModel.clearManagedCache()` 继续在播放缓存清理命令 accepted 后轮询快照，直到 `cache_core` 真实清空或达到重试上限。
   - 歌词缓存仍由设置页本地同步删除，但最终 UI 成功反馈以播放缓存和歌词缓存的新快照共同收敛为准。
   - 这个策略比“命令 accepted 就直接显示清理成功”更符合真实状态，也更容易写稳定测试。

## Risks / Trade-offs

- [切换音源时要重新准备当前在线歌曲] → 通过复用现有音质切换时的重准备语义，保持播放/暂停状态和当前位置，避免引入全新状态机。
- [CacheCore 运行中重配上限可能失败或受底层实现限制] → 先保证新值被持久化并下发到播放进程，重配失败时明确回退到“重连后生效”，不要 silently ignore。
- [音源列表 richer 状态会让设置页 UI 更复杂] → 继续沿用现有 `AccountCardSurface` 和 section 结构，只增强音源 row 信息密度，不新开额外页面。
- [旧音源的 URL / 音质目录缓存和新音源串用] → 通过 source-scoped cache key 或切换时强制清空内存缓存，避免跨音源污染。
- [本地/在线导入都支持后，错误路径变多] → manifest 校验统一收口在仓库层，ViewModel 只消费明确错误文案，避免 UI 直接拼异常。

## Migration Plan

1. 扩展设置页音源模型和仓库，补齐 manifest 元数据、本地/在线导入、当前音源和错误状态字段。
2. 打通 `PlayerServiceBridge` → `PlayerMediaSessionService` → `PlaybackProcessRuntime` 的缓存上限与当前音源命令处理。
3. 将播放服务的在线播放解析客户端改造成当前音源驱动，并加上音源切换时的 URL / 音质目录缓存失效。
4. 复用当前音质切换的重准备模式，在切换音源时对当前在线歌曲执行重准备。
5. 补充设置页、播放服务和在线播放解析的回归测试，再运行仓库要求的单测与组装验证。
6. 如需回滚，可先隐藏设置页中的 richer 音源管理入口并强制回退内置默认音源；默认音质和缓存上限仍保持现有链路可用。

## Open Questions

- CacheCore native 实现在运行中再次 `init` 时是否完全支持无损更新容量配置；若不支持，是否需要显式转为“持久化并在下次重准备时重建”策略。
- `initError` 在首版是否只记录导入/解析失败，还是也要覆盖播放服务实际请求阶段遇到的远端网络错误。
