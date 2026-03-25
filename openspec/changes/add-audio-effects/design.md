## Context

当前播放器已经有一条稳定的“会话状态 -> 后台播放进程 -> native 播放器 -> FFmpeg `avfilter`”链路，但这条链路目前只承载倍速控制。`PlayerUiState`、`RemotePlaybackSnapshot`、`PlaybackProcessState`、`PlaybackMetadataExtras` 和 `NativePlayer` 已经围绕 `playbackSpeed` 建好了跨进程同步与回放恢复机制，而 native 侧 `ffmpeg_decoder.cpp` 里也已经用 `atempo` 建了可更新的滤镜图。

播放页当前已经有右上角三点按钮，但行为仍是临时 toast；同时页面内已经存在播放列表 sheet 等半屏浮层模式，可作为“更多操作”入口的现成承载。新增音效因此不是单点 UI 变更，而是要同时改动 `app`、`playback-client`、`playback-contract`、`playback-service` 和 `player` 模块，并保证音效在暂停、恢复、Seek、切歌、后台播放和服务重连时保持一致；首版还要求直接基于 FFmpeg 音频滤镜实现，不能再引入第二套独立 DSP 管线。

## Goals / Non-Goals

**Goals:**
- 提供一组固定且可直接理解的默认音效预设，至少包含“原声”和若干增强型听感模式。
- 将播放页右上角三点按钮收口为“更多操作”半屏浮层，并把 `音效设置` 与 `倍速设置` 统一纳入这一入口。
- 让 `音效设置` 使用播放器内独立子页面承载，让 `倍速设置` 在当前半屏浮层内直接展开原有离散档位选择。
- 在播放页进度条下方展示当前音效名称，保证用户在离开设置面板后仍能持续看到当前生效状态。
- 让音效与倍速一样，成为当前播放会话的稳定状态，并通过现有跨进程播放链路同步。
- 把音效与倍速收敛到同一条 FFmpeg 音频滤镜链中，避免 Java 层和 native 层各维护一套音频处理逻辑。
- 在滤镜不可用、参数非法或图初始化失败时优先保证继续播放，并安全回退到原声音频。

**Non-Goals:**
- 首版不做用户自定义 EQ、拖杆式多段均衡器或任意滤镜字符串输入。
- 首版不做按歌曲、歌单或音源类型的个性化音效记忆。
- 首版不依赖服务端下发音效配置，也不新增服务端 API。
- 首版不新建独立的 `AudioEffectActivity` 或额外播放设置 Activity。
- 首版不改变现有歌词、MediaSession 标题展示或播放列表结构。

## Decisions

1. 音效能力建模为共享的“固定预设目录”，而不是自由参数集合。
   - 在共享层新增稳定的 `PlaybackAudioEffectPreset` 模型，提供固定 `wireValue` / `id`、展示文案和默认选项 `off`。
   - Kotlin 层只传递预设标识，不直接持有 FFmpeg 滤镜表达式；native 层根据预设标识查表得到具体滤镜 recipe。
   - 首版预设按“产品语义”组织，而不是按底层滤镜名组织。建议的稳定 ID 为：`off`、`bass-boost`、`vocal-boost`、`bright`、`warm`。
   - 备选方案对比：
     - 让 UI 直接传 FFmpeg filter 字符串：被拒绝。原因是无类型约束、容易传入非法表达式、难以做跨进程兼容和回归测试。
     - 直接做多段均衡器数据模型：被拒绝。原因是超出当前 change 范围，也会显著放大 UI、持久化和 native 参数校验复杂度。

2. 跨进程同步路径直接复用现有倍速链路，不新增第二套状态通道。
   - 在 `PlayerUiState`、`PlayerRuntime`、`RemotePlaybackSnapshot`、`PlaybackProcessState`、`PlaybackMetadataExtras` 中新增音效字段，并保持默认值为 `off`。
   - 在 `PlayerServiceBridge` / `PlaybackSessionCommands` / `PlayerMediaSessionService` 中新增与 `setPlaybackSpeed` 对称的“设置音效预设”命令。
   - 前台 UI 继续使用“本地先更新、后台确认失败再回退”的交互模式；后台快照和前台状态都以播放进程的最终确认结果为准。
   - `playCurrent()`、`prepareCurrent()` 和服务重连后的状态恢复，都沿用当前“先恢复会话状态，再把状态重放给 native 播放器”的模式，同时重放倍速和音效。
   - 备选方案对比：
     - 只在 `app` 侧保存音效选择：被拒绝。原因是后台播放、服务重建和外部媒体控制下会出现状态分叉。
     - 只把音效塞进 `MediaItem.extras` 不做显式命令：被拒绝。原因是用户在当前曲目播放过程中切换音效时，仍然需要即时控制通道。

3. native 侧把当前 `AudioTempoProcessor` 演进为通用 `AudioFilterProcessor`，统一管理效果链和倍速链。
   - 滤镜图从当前的 `abuffer -> atempo -> abuffersink` 扩展为 `abuffer -> effect filters -> atempo -> abuffersink`。
   - 倍速仍通过 `atempo` 节点的 `process_command` 动态更新，避免每次调速都重建整张图。
   - 音效预设变化、输入 PCM 配置变化或 seek 后需要重新建图时，由 `AudioFilterProcessor` 在下一帧解码数据进入前重建图；`off` 预设不插入额外效果滤镜，保持原始行为。
   - 预设与 FFmpeg 滤镜的映射采用“查表 + 保守参数”策略：
     - `off`：no-op
     - `bass-boost`：优先使用 `bass`
     - `bright`：优先使用 `treble`
     - `vocal-boost`：优先使用 `equalizer` 或 `superequalizer` 做中频/中高频增强
     - `warm`：使用轻度低频增强加轻度高频衰减的组合
   - 具体增益值和中心频段在实现阶段通过设备听感验证微调，但 filter family 和 preset ID 在本 change 内固定。
   - 备选方案对比：
     - 每次切换音效都重启整首播放：被拒绝。原因是会引入明显中断，也会破坏 seek 和 auto-next 的连续性。
     - 在 Java/Kotlin 层做 PCM 后处理：被拒绝。原因是会复制现有 native 能力，并增加 CPU 和维护成本。

4. 音效状态以“播放进程确认成功后再生效”为原则，播放不中断优先于效果绝对可用。
   - `PlaybackProcessRuntime` 只有在 native 返回成功时才提交新的音效状态；前台失败时回退到上一个已确认预设，行为与当前倍速设置失败时保持一致。
   - 如果运行时发现请求的滤镜在当前 FFmpeg 构建中不可用，或图配置失败，native 必须回退到 `off` 对应的 no-op 链路，而不是直接让播放失败。
   - 播放进程在安全降级时更新 `statusText`，并把最终状态回推到前台快照，避免 UI 长时间显示一个实际未生效的音效。
   - 备选方案对比：
     - 失败时仍保留用户选择但静默输出原声：被拒绝。原因是 UI 与实际输出不一致，调试和用户理解都更差。
     - 图配置失败直接终止播放：被拒绝。原因是与 proposal 中“至少回退原声、不破坏现有播放”的目标冲突。

5. 播放页设置入口统一收口到三点半屏浮层，音效与倍速使用不同层级深度承载。
   - 播放页右上角三点按钮打开半屏“更多操作”浮层，首层至少展示 `音效设置` 和 `倍速设置` 两个入口。
   - 点击 `倍速设置` 后，在当前半屏浮层内直接展开原有离散档位选择，不再打开单独弹窗或跳转无关页面。
   - 点击 `音效设置` 后，进入播放器上下文内的独立音效设置子页面，不新起独立 Activity；用户完成选择后返回播放器主页面上下文。
   - 播放页主界面在进度条下方保留稳定间距，持续展示当前音效名称；默认 `off` 预设显示为 `原声`，而不是留空或仅在设置页中可见。
   - 备选方案对比：
     - 把音效和倍速都直接塞进半屏浮层：被拒绝。原因是音效后续还需要承载预设列表和状态展示，继续堆在首层会让浮层层级失控。
     - 为音效单独新建 Activity：被拒绝。原因是会打碎播放器上下文，带来更重的状态恢复和返回路径。

6. 测试按“页面交互 + 状态一致性 + native 组合滤镜 + 降级行为”四层补齐。
   - UI 层补充三点按钮打开半屏浮层、倍速入口内联展开、音效设置子页面进入/返回、进度条下方音效名称展示的交互验证。
   - 共享层补充 preset 映射、未知 `wireValue` 回退、metadata extras 读写和 remote snapshot 映射测试。
   - 播放进程层补充音效设置、切歌后重放、Seek 后保持、服务重连恢复，以及音效和倍速共存的状态测试。
   - native / 集成层补充“切换预设不打断播放”“滤镜不可用时回退 `off`”“`atempo` 与 effect chain 同时存在时仍可持续输出”的验证。

## Risks / Trade-offs

- [音效调校具有主观性] → 首版只提供 4 到 5 个保守预设，默认仍为 `off`，并通过真机听感验证避免过度增强。
- [FFmpeg 构建未必包含所有候选滤镜] → 只优先选择 `bass`、`treble`、`equalizer`、`superequalizer` 这类官方标准音频滤镜；运行时对 filter availability 做检查，不可用则回退 `off`。
- [三点浮层同时承载入口态和倍速展开态，状态切换更复杂] → 入口态和倍速展开态使用明确的页面状态枚举，不让半屏浮层靠多个零散布尔值拼接。
- [播放中重建滤镜图可能带来瞬时爆音或听感跳变] → 预设切换只在明确用户动作时触发，并在帧边界重建图；失败时不重试复杂补丁逻辑，直接回退 no-op。
- [UI 状态与实际输出可能漂移] → 沿用“native 成功后再提交会话状态”的规则，并在后台快照里暴露最终确认值。
- [与现有倍速链路耦合后，回归面会扩大] → 先保持 `atempo` 语义不变，只把 effect chain 插到其前面；测试重点覆盖 speed + effect 并行场景。

## Migration Plan

1. 在共享层引入音效预设模型、wire value 和默认 `off`，同时扩展 metadata extras、remote snapshot 和播放进程状态模型。
2. 在播放页补齐三点半屏浮层、音效设置子页面、倍速内联展开和进度条下方当前音效名称展示。
3. 在前台和播放服务之间新增“设置音效预设”命令，按现有倍速模式完成 optimistic update / rollback。
4. 在 native 层把 `AudioTempoProcessor` 升级为通用滤镜处理器，并先接通 `off` + 一个最简单预设，验证无回归后再补齐其他预设。
5. 完成页面、状态、进程和 native 侧验证后开放 UI 入口。
6. 如需回滚，优先隐藏前台音效入口并让三点浮层只保留倍速设置，同时强制使用 `off`；由于默认值兼容，旧会话读取到缺失字段时仍应保持原声播放。

## Open Questions

- 当前 change 是否要求把音效选择持久化到“服务恢复后的最近播放会话”，还是只要求在当前存活会话内保持一致。
- `vocal-boost` 预设最终采用 `equalizer` 组合还是 `superequalizer` 更合适，需要结合本仓库当前 FFmpeg 构建可用性再定。
