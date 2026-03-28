## Context

当前在线歌曲详情链路由 [`SongDetailRepository`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/core/playback/SongDetailRepository.kt) 调用 `/song/detail`，再由 [`SongDetailJsonMapper`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/core/playback/SongDetailJsonMapper.kt) 映射成 [`MusicInfo`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/MusicInfo.kt)。这条链路只保留标题、歌手、专辑、封面和时长，当前没有任何“歌曲实际可用音质”数据结构，因此播放器既不知道一首歌有哪些可切换档位，也无法把用户选择的音质稳定下发到在线播放准备链路。

播放器的前后台同步模式已经比较成熟。前台 `app` 侧有 [`PlayerUiState`](/Users/wxy/Projects/player-lite/feature-player/src/main/java/com/wxy/playerlite/feature/player/model/PlayerUiState.kt)、[`PlayerRuntime`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/player/runtime/PlayerRuntime.kt) 和 [`PlayerViewModel`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/player/PlayerViewModel.kt)；跨进程命令通过 [`PlayerServiceBridge`](/Users/wxy/Projects/player-lite/playback-client/src/main/java/com/wxy/playerlite/playback/client/PlayerServiceBridge.kt) 和 [`PlaybackSessionCommands`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/PlaybackSessionCommands.kt) 下发；后台播放状态则由 [`PlaybackProcessRuntime`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/PlaybackProcessRuntime.kt)、[`RemotePlaybackSnapshot`](/Users/wxy/Projects/player-lite/playback-client/src/main/java/com/wxy/playerlite/playback/client/RemotePlaybackSnapshot.kt) 和 [`PlaybackMetadataExtras`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/PlaybackMetadataExtras.kt) 统一回传。现有音效和倍速都已经走这条链路，音质切换应尽量复用它，而不是新开一套旁路状态。

在线播放准备链路也已经有一部分“音质”语义，但现在还不是真正的用户可控音质。[`OnlinePlaybackUrlResolver`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/OnlinePlaybackSupport.kt) 的 `/song/url/v1` 调用已经接收 `requestedLevel`，缓存 key 也带 `level`；但 [`OnlinePlaybackPreparationPlanner`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/OnlinePlaybackSupport.kt) 仍把 `defaultLevel` 固定为 `exhigh`，旧接口 `/song/url` 与 `/check/music` 的回退也仍写死 `320_000`，所以当前代码虽然有 level 槽位，却没有接上“当前歌曲真实可用音质目录 + 用户选择 + 兼容回退”这条完整链路。

本次变更因此不是单点 UI 调整，而是一个跨 `app`、`playback-client`、`playback-contract`、`playback-service` 的能力扩展。设计必须同时满足几个约束：
- 音质信息必须来自 `/song/music/detail?id=<songId>`，并保留接口中的 `br`、`size`、`vd`、`sr` 等字段。
- 背景播放、自动切歌和前台重连时，音质选择不能因为 UI 不在前台就失效。
- 不能把当前歌曲不支持的音质伪装成可选项，也不能在回退时让 UI 长时间显示与实际播放不一致的档位。
- 改动要尽量沿用现有 more-actions、会话命令、session extras 和在线播放 planner 结构，避免为了音质切换重写播放器架构。

## Goals / Non-Goals

**Goals:**
- 为在线歌曲引入“真实可用音质目录”模型，使用 `/song/music/detail` 获取当前歌曲的可用档位和文件信息。
- 在播放器上下文内提供音质查看与切换入口，且选项只展示当前歌曲真实支持的音质。
- 在进度条下方提供音质 / 音效联合状态区，默认只显示当前音质；仅当存在非默认音效时才显示 `音质 · 音效`。
- 让联合状态区中的音质片段直达音质选择半屏浮层，并让联合状态区始终投影当前实际生效值。
- 让播放服务持有当前会话的“偏好音质”和“当前实际生效音质”，并通过现有跨进程状态链路同步到前台。
- 让在线播放准备、URL 解析、缓存 key 和兼容回退都基于当前选择的音质工作，而不再默认固定 `exhigh` / `320_000`。
- 在目标音质不可用、旧接口不支持或账号权限变化时，稳定回退到可用档位，并把最终结果反馈给 UI。
- 补齐音质详情映射、选择策略、在线播放回退和播放器设置页交互的验证面。

**Non-Goals:**
- 本次不为本地文件、`content://` 音源或离线导入歌曲做音质分析、转码或“伪音质”展示。
- 本次不支持用户手动输入任意 bitrate、任意 level 或自定义下载 / 缓存策略。
- 本次不预抓整个播放列表每首歌的音质详情，只处理当前活动在线歌曲和播放服务实际需要准备的歌曲。
- 本次不改动现有歌词、歌单、倍速或 MediaSession 宿主架构。
- 本次不改变既有音效设置承载方式；音效入口的页面 / 浮层形态继续由独立的音效 change 定义。
- 本次不新增服务端接口，也不切换当前仓库配置的 API Base URL。

## Decisions

1. 音质建模拆成“共享音质等级”与“按歌曲生成的音质目录”两层，而不是把原始接口 JSON 直接透传到 UI 或播放服务。
   - 在共享层新增稳定的 `PlaybackAudioQuality` 模型，负责表达跨进程要同步的等级标识、展示文案和排序语义；它的 `wireValue` 必须和 `/song/url/v1` 所使用的 `level` 保持一致。
   - 在共享或可复用域模型中新增 `SongAudioQualityOption` / `SongAudioQualityCatalog`，承载某首歌曲当前实际可用的音质选项，以及接口返回的 `br`、`size`、`vd`、`sr`、原始音质 key 等信息。
   - `PlaybackAudioQuality` 只负责“等级语义”和排序，不直接内嵌固定 bitrate 常量；旧接口回退时优先使用 `SongAudioQualityOption.br`，避免再次落回手写常量表。
   - 备选方案对比：
     - 直接把 `/song/music/detail` 原始字段塞进 `Bundle extras`：被拒绝。原因是类型不稳定、字段异构且会让跨进程序列化变重。
     - 只新增一个字符串 level，不保留每首歌的目录信息：被拒绝。原因是 UI 无法展示真实可用选项，旧接口回退也拿不到该歌曲对应的 bitrate。

2. `/song/music/detail` 由 app 和 playback-service 双端按需查询，各自维护小型内存缓存，而不是强行把完整目录通过 MediaSession extras 广播给前台。
   - `app` 侧新增音质详情仓库，按当前活动在线歌曲 `songId` 请求目录，并在播放器音质设置页展示。
   - `playback-service` 侧新增同源远端数据源，用于在 prepare、切换音质、自动切歌和后台继续播放时独立解析当前歌曲目录，保证后台服务在前台进程不活跃时仍然能做可用性判断和回退。
   - 两端都使用仓库里现有的认证请求模式：沿用 `JsonHttpClient` 和当前用户会话头部透传，而不是在 UI 层手拼 cookie / csrf。
   - 两端缓存都按 `songId` 建立短生命周期内存缓存，并在用户会话变化时清理，降低同一首歌重复进页、切回上一首、前后台各查一次带来的重复网络开销。
   - 备选方案对比：
     - 只在 app 侧查询目录，再把结果塞进播放命令：被拒绝。原因是后台自动切歌和服务独立恢复时没有稳定信息源。
     - 只在 service 侧查询，再把整份目录通过 snapshot 下发 UI：被拒绝。原因是 `Bundle extras` 不适合承载可变长度目录，前台设置页也会被动耦合到 session 序列化格式。

3. 播放状态里区分“偏好音质”和“当前实际生效音质”，不再用单一字段同时承担用户选择与回退结果。
   - 在 [`PlaybackProcessState`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/PlaybackProcessRuntime.kt) 中新增 `preferredAudioQuality` 和 `appliedAudioQuality`；前者表示当前播放会话想要的目标档位，后者表示当前歌曲最终实际用于播放的档位。
   - `preferredAudioQuality` 默认值沿用现有系统默认高音质策略，建议首版固定为 `exhigh` 对应的共享等级，并持久化到服务侧 `SharedPreferences`，这样服务重建后仍保持一致。
   - 在 [`PlaybackSessionCommands`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/PlaybackSessionCommands.kt) 增加音质切换命令，在 [`PlaybackMetadataExtras`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/PlaybackMetadataExtras.kt) 和 [`RemotePlaybackSnapshot`](/Users/wxy/Projects/player-lite/playback-client/src/main/java/com/wxy/playerlite/playback/client/RemotePlaybackSnapshot.kt) 中同步这两个字段，让前台知道“用户想要什么”和“当前实际播的是什么”。
   - 前台 `PlayerRuntime` 可以像当前音效 / 倍速一样做“本地先进入 pending 态、后台确认后收敛”的交互，但最终高亮和持续展示以远端确认的 `appliedAudioQuality` 为准；如果服务回退到别的档位，前台必须跟随，而不是继续显示用户最初点击的不可用档位。
   - 备选方案对比：
     - 只同步一个 `selectedAudioQuality`：被拒绝。原因是目标档位不可用或被回退时，UI 无法区分“用户选择”和“实际生效”。
     - 每首歌单独记忆所选音质：被拒绝。原因是当前 change 重点是切换能力和稳定回退，不是做 per-track 偏好数据库。

4. 在线播放 planner 改成“按当前歌曲目录挑出有效档位后再准备”，彻底去掉内部硬编码默认 level / bitrate 的假设。
   - 将 [`OnlinePlaybackPreparationPlanner`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/OnlinePlaybackSupport.kt) 从当前的 `defaultLevel` 常量模式，重构为按调用时传入的 `preferredAudioQuality` 和当前歌曲目录决定本次 `appliedAudioQuality`。
   - 选档策略采用固定有序梯度：
     - 先命中当前歌曲目录里与 `preferredAudioQuality` 完全一致的档位。
     - 如果不存在，则沿共享等级顺序向下寻找最近可用档位。
     - 如果更低档也没有，则退到当前歌曲目录中排序最高且可用的一个档位。
   - 生成 plan 后，`resourceKey`、URL memory cache key 和后续缓存 metadata 都使用 `appliedAudioQuality`，防止不同音质共用同一缓存 identity。
   - `/song/url/v1` 继续优先走 `level`；旧接口 `/song/url` 与 `/check/music` 的回退使用当前 `SongAudioQualityOption.br`。如果当前 `appliedAudioQuality` 属于旧接口无法可靠表达的高级音质家族，则先在目录内降到最近的经典 bitrate 档位再走旧接口，而不是伪造一个固定 `br`。
   - 备选方案对比：
     - 保持 planner 的 `defaultLevel = exhigh`，只在 UI 增加音质选项：被拒绝。原因是用户切换不会真正影响在线播放解析。
     - 旧接口始终继续写死 `320_000`：被拒绝。原因是会把高阶音质选择重新压扁成统一回退，等于能力没打通。

5. 音质切换在当前曲目播放中触发“轻量重新准备”，核心目标是保留播放连续性，而不是只影响下一首。
   - 当用户在当前在线歌曲上切换音质时，播放服务立即更新 `preferredAudioQuality`，重新查询当前歌曲目录并计算新的 `appliedAudioQuality`。
   - 如果新旧 `appliedAudioQuality` 相同，只更新状态并直接返回成功，不做多余的 source 重建。
   - 如果实际生效档位发生变化，则服务抓取当前进度，按新档位重新解析 URL / cache key，并以“当前曲目 + 当前进度”为目标重新 prepare；准备期间维持 `isPreparing` 和明确的 status text，让前台知道当前在重切音质。
   - 对本地音源、缺少 `songId` 的音源或不走在线播放 planner 的场景，音质设置入口不触发服务重准备，而是在 UI 层明确显示“当前音源不支持音质切换”。
   - 备选方案对比：
     - 音质切换只作用于下一首：被拒绝。原因是与用户对“切换能力”的直觉不符。
     - 每次切换都从 0 秒重新播放：被拒绝。原因是会显著破坏收听连续性。

6. 播放器 UI 改为“进度条下方联合状态区 + 音质选择半屏浮层”，联合状态区只强约束音质相关交互，不改写既有音效承载方式。
   - 进度条下方只保留一行联合状态区。左侧片段始终显示当前实际生效音质；只有当当前音效不是默认原声时，才在后面追加 ` · <音效名>`。
   - 这行里的音质片段是音质选择半屏浮层的直达入口；分隔符 ` · ` 本身不可点击。当当前歌曲不支持音质切换时，不伪造音质片段；如果当前存在非默认音效，则只显示音效名称；如果两者都不可展示，则隐藏整行联合状态区。
   - 音质浮层只展示当前歌曲真实可用的档位列表，并优先呈现 bitrate、sample rate、size 等辅助信息；`vd` 先保留在模型里，首版不做主展示字段。
   - 联合状态区在本 change 中只承担“当前实际生效值展示 + 音质片段直达音质浮层”；音效片段的承载方式和回访入口继续复用现有音效能力，由独立的音效 change 继续定义。
   - 当前曲目变化时，前台仓库清理旧目录并重新加载；若目录尚未返回，音质浮层展示 loading；若当前音源无 `songId` 或目录为空，则展示稳定空态或不支持说明。
   - 备选方案对比：
     - 把整行作为单一点击区，点开后再二选一：被拒绝。原因是多了一层中转，不符合“点击音质直接打开音质浮层”的要求。
     - 继续沿用单独的音质设置页：被拒绝。原因是会把当前歌曲真实可用档位和当前实际生效值之间的反馈链路拉长。

7. 验证重点不是“接口能调通”本身，而是“目录、选择、回退和实际播放”四条链路一致。
   - 纯 mapper 测试要覆盖 `/song/music/detail` 异构 key 到共享等级的映射，以及 `br` / `size` / `vd` / `sr` 缺失时的容错。
   - 播放服务层测试要覆盖偏好音质命中、向下回退、旧接口 bitrate 回退、切换当前曲目时保留偏好、自动切歌时重新计算 `appliedAudioQuality`。
   - 前台测试要覆盖仅音质显示、`音质 · 音效` 联合显示、仅音效显示、音质片段点击、loading / empty / unsupported 状态，以及远端回退后的 UI 收敛。
   - 集成验证至少要覆盖“当前曲目切换音质后重新准备”“缓存 key 不同音质隔离”“无法命中目标音质时仍可继续播放”“音质浮层与当前联合状态区显示保持一致”。

## Risks / Trade-offs

- [app 和 playback-service 双端都要查 `/song/music/detail`，会有重复请求] → 通过按 `songId` 的短生命周期内存缓存、当前曲目按需加载和会话切换清理，把重复请求控制在可接受范围内。
- [上游接口不同音质家族的 raw key 可能不稳定或字段缺失] → 共享 mapper 对未知 key 保留原始标识并安全降级排序；缺失 `br` / `sr` / `size` 时不让解析失败。
- [用户选择的偏好音质和当前歌曲实际生效档位可能不同，容易造成 UI 误解] → 状态层显式区分 `preferred` 与 `applied`，进度条下方联合状态区始终展示 `applied`，并在必要时给出简短回退提示。
- [播放中切换音质需要重新准备 source，可能带来短暂 buffering] → 通过“保留当前进度重新 prepare”的方式降低感知中断，并在 UI 上把这次行为明确表示为切换中。
- [如果缓存 key 未包含音质，会把不同档位写到同一缓存身份里] → 所有在线 resource key、memory cache key 和相关 metadata 都必须纳入 `appliedAudioQuality`。
- [旧接口对高级音质家族的表达能力有限] → 坚持 `/song/url/v1 level` 优先；只有旧接口回退时才降到可映射 bitrate 的相邻档位，而不是强行假装旧接口支持全部高级音质。
- [音质目录依赖当前登录态，不同账号权限变化可能导致旧缓存误导选择策略] → 音质目录缓存只做短时内存缓存，并在登录态变化时失效。
- [联合状态区同时承载展示和点击，容易出现命中区域含糊] → 分隔符不参与点击，音质片段命中区扩展最小点击面积，并通过 UI 测试锁定行为。

## Migration Plan

1. 在共享层新增音质等级模型、歌曲音质目录模型、会话命令常量和 metadata extras 读写能力。
2. 在 `app` 与 `playback-service` 分别接入 `/song/music/detail` 数据源、mapper 和短时缓存。
3. 扩展 `PlaybackProcessState`、`RemotePlaybackSnapshot`、`PlayerUiState` 和相关同步逻辑，引入 `preferredAudioQuality` / `appliedAudioQuality`。
4. 重构 `OnlinePlaybackPreparationPlanner` 与 `OnlinePlaybackUrlResolver`，让在线播放准备真正按当前音质目录和选择策略工作，并更新缓存 key。
5. 在播放器进度条下方加入音质 / 音效联合状态区，并接入音质选择半屏浮层，补齐 loading / empty / unsupported / applied 状态展示。
6. 增加 mapper、播放服务、前台状态和 UI 测试；按仓库默认要求跑 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`。
7. 如需回滚，优先隐藏前台音质入口并让播放服务忽略新的音质切换命令，同时把 planner 恢复为当前默认 `exhigh` 路径；由于新增字段都应有默认值，旧会话缺少这些字段时仍可继续播放。

## Open Questions

- 稀有高级音质家族的最终展示顺序和中文命名，是否完全沿用上游 key 语义，还是在产品层做统一命名，需要在 specs 阶段定死。
- 播放中切换音质时，产品是否需要单独的“切换中”文案或显式 loading 态文案；当前设计只约束行为，不强绑具体文案。
