## Context

当前播放器已经拆分为三层：`app` 模块负责 Compose UI 与 `PlayerRuntime` 状态编排，`playback-service` 通过 `MediaSessionService` 承载后台播放与系统控制，`player` 模块中的 `NativePlayer` + FFmpeg/JNI 负责实际解码与 `AudioTrack` 输出。现有链路支持播放、暂停、Seek、播放列表与后台会话同步，但还没有“播放倍速”这一跨 UI、服务、native 的统一能力。

除了倍速外，当前后台播放运行时在歌曲自然播放结束后只会停留在当前曲目末尾，不会自动推进到下一首。由于系统媒体控制与真实播放状态都以 `playback-service` 为准，如果要补齐自动切歌，最稳妥的接入点也应放在后台播放运行时，而不是只在前台 UI 做补偿。

这次变更同时具备几个约束：
- 倍速入口必须位于歌曲控制栏左侧，交互形态是点击后弹出弹窗，在弹窗内用 `SeekBar` 效果进行设置。
- 播放列表入口需要收敛到歌曲控制栏右侧，并使用与倍速入口、播控按钮一致的统一 UI 风格，不再保留独立浮动入口。
- 可选档位固定为 `0.5X` 到 `2.0X`，步进 `0.1X`，属于离散档位而不是任意连续值。
- 实际变速效果必须在 native 播放链路中完成，并且要满足“变速不变调”，避免直接修改采样率或 `AudioTrack` 播放速率带来的音高变化。
- 当前项目已经内置 FFmpeg 原生依赖，native 解码主链路是“解复用 -> 解码 -> 重采样 -> AudioTrack 消费”，适合在 PCM 输出阶段插入时间拉伸处理。

## Goals / Non-Goals

**Goals:**
- 为播放器增加倍速入口，并在歌曲控制栏左侧提供当前倍速的可见设置入口。
- 将播放列表入口调整到歌曲控制栏右侧，与左侧倍速入口形成左右对称的主控制区布局。
- 在弹窗中以离散 `SeekBar` 交互提供 16 档倍速（`0.5X` ~ `2.0X`）。
- 让 `playback-service` 成为当前倍速状态的单一事实来源，保证 UI、MediaSession 和 native 播放内核一致。
- 在 native 层实现变速不变调，并支持在当前播放会话中切换后生效。
- 保证切歌、重新 prepare、后台控制与前台 UI 恢复时，当前倍速档位不会意外丢失。
- 在当前播放列表会话中，当曲目自然播放完成且仍有下一首时自动切歌并继续播放。
- 在最后一首自然播放完成时停止播放，不提前引入循环、单曲循环或随机等播放模式。

**Non-Goals:**
- 不支持超出 `0.5X` ~ `2.0X` 范围的任意自定义倍速输入。
- 不在本次引入独立的“变调”控制、均衡器或其他音频 DSP 能力。
- 不替换现有 `NativePlayer` / FFmpeg / `AudioTrack` 播放架构。
- 不默认承诺跨应用重启持久化倍速设置；首版以当前播放会话一致性为主。
- 不在本次引入循环播放、随机播放、单曲循环等播放模式；最后一首播完只停止播放。

## Decisions

1. 使用“离散速度索引”作为跨层传输模型，而不是直接用浮点值做唯一状态。
   - 方案：定义固定档位表（`0.5f` 到 `2.0f`），UI `SeekBar` 以 `0..15` 的整数索引工作，状态层同时保留“索引 + 展示文案 + 实际 speed float”三种派生值。
   - 原因：离散索引天然适合 `SeekBar`、避免 Binder/JNI 之间的浮点比较误差，也方便在 UI 上高亮当前档位。
   - 备选方案：
     - 直接传递 `Float`：实现简单，但存在精度漂移、比较困难和 UI 档位映射重复计算问题。
     - 直接传递字符串（如 `1.2X`）：展示方便，但不适合作为 native 命令参数。

2. UI 使用“控制栏左侧按钮 + 弹窗内离散 Slider/SeekBar”的交互组合，提交时机放在拖动结束。
   - 方案：在现有歌曲控制栏左侧新增倍速按钮，按钮直接显示当前倍率（如 `1.0X`）；点击后弹出 Compose 对话框，内部使用带步进的 `Slider` 实现 `SeekBar` 效果。拖动过程中仅更新弹窗内预览值，`onValueChangeFinished` 后再下发正式命令。
   - 原因：当前 UI 为 Compose，实现原生 `SeekBar` 语义的 `Slider` 最贴合现有技术栈；“拖动结束再提交”可以减少 service 命令洪泛与 native 滤镜频繁重配造成的爆音/抖动。
   - 备选方案：
     - 直接在主界面常驻 SeekBar：会挤占控制栏空间，不符合“弹窗设置”的要求。
     - 使用底部弹层或菜单：操作成本更高，且与“歌曲控制栏左侧入口”这一显式要求不完全匹配。

2.1 播放列表入口与倍速入口统一收口到播控区域两侧。
   - 方案：保留现有底部播放列表 `BottomSheet` 交互，但把入口从独立浮动按钮迁移到 `PlaybackControls` 右侧，样式与左侧倍速入口保持统一（按钮高度、轮廓和色彩体系一致），并在按钮上展示播放列表能力识别信息。
   - 原因：当前界面已经有左侧倍速入口，右侧放播放列表入口能让播控区成为完整的主操作带，减少漂浮按钮造成的视觉割裂，也符合“统一 UI 样式、放在右侧展示”的要求。
   - 备选方案：
     - 保留浮动入口：实现最少，但与统一播控样式目标冲突。
     - 直接把底部列表常驻展开：会占用内容空间，不符合当前单页播放器结构。

3. 倍速状态由 `playback-service` 持有并通过 MediaSession 同步，app 侧只负责展示和发命令。
   - 方案：在 `PlaybackProcessState`、`RemotePlaybackSnapshot`、`PlayerUiState` 中新增当前倍速字段；增加新的会话自定义命令（如 `ACTION_SET_PLAYBACK_SPEED`），由 `PlayerServiceBridge` 发送到 `PlayerMediaSessionService`，再由 `PlaybackProcessRuntime` 更新运行时状态并驱动 native。当前倍速同时写入 `PlaybackMetadataExtras`，便于 UI 重连或后台恢复后读取。
   - 原因：仓库已经明确以 `MediaSessionService` 作为后台播放宿主，倍速如果只保存在 app 本地状态，会在后台播放、控制器重连或系统媒体控制场景下产生状态漂移。
   - 备选方案：
     - UI 直接操作 app 进程内 `NativePlayer`：与后台播放现状冲突，且无法覆盖服务进程中的真实播放实例。
     - 仅在 `PlayerUiState` 中记录倍速：前后台切换和控制器重建后容易丢失。

4. native 层采用 FFmpeg `libavfilter` 的 `atempo` 滤镜在 PCM 输出阶段实现“变速不变调”。
   - 方案：在当前“解码/重采样 -> PcmConsumer”之间插入 native tempo stage，使用 FFmpeg `atempo` 处理已解码 PCM；播放器对外新增 `setPlaybackSpeed(speed: Float)` 接口，在播放中切换速度时优先通过滤镜命令动态更新，必要时回退到重新构建 tempo graph。速度范围正好落在 `atempo` 单实例支持的 `0.5` 到 `2.0` 之间。
   - 原因：`atempo` 满足 native 实现与变速不变调要求，且项目已内置 FFmpeg 依赖，不必再引入新的 DSP 第三方库；相比调整 `AudioTrack` 播放速率或直接重采样，不会导致音高一起变化。
   - 备选方案：
     - 修改 `AudioTrack` playback rate：实现最短，但会变调，不满足需求。
     - 仅通过 `swr` 改采样率：同样会造成音高变化。
     - 新引入 SoundTouch/RubberBand：可行，但会增加新的 native 依赖、构建和授权验证成本。

5. 倍速在运行时上按“当前会话记忆”处理，切歌、prepare、seek 后继续沿用最近一次确认的速度。
   - 方案：`PlaybackProcessRuntime` 保存当前目标速度，切换媒体项、重新 prepare、重新 play 时都先把该速度应用给 native 播放器；播放中更新倍速则立即同步到 native，空闲/准备中更新则只改状态，待下次真正开始播放时生效。
   - 原因：倍速是播放体验偏好，用户在同一播放会话里通常期望它持续生效；如果切歌后恢复到 `1.0X`，会造成行为不一致。
   - 备选方案：
     - 每首歌都重置为 `1.0X`：实现更简单，但用户体验差。
     - 仅在播放中允许修改：会让准备态或暂停态调整倍速变得不必要地受限。

6. 倍速相关可观测性与测试围绕“状态同步”和“native 生效”两条主线设计。
   - 方案：在会话 extras 中增加当前倍速字段，UI 文案和服务状态都使用同一份格式化逻辑；单元测试覆盖档位映射、命令透传、状态回填，集成验证覆盖播放中切换、Seek 后保留速度、切歌后继承速度，以及 native 失败时的回退提示。
   - 原因：这是跨模块变更，仅测试 UI 或仅测试 native 都不足以证明链路完整。
   - 备选方案：
     - 只验证 UI 显示：无法证明后台服务和 native 已真正应用倍速。
     - 只验证 native 接口：无法发现会话状态不同步的问题。

7. 自然播放完成后的自动切歌决策统一收口到 `PlaybackProcessRuntime`。
   - 方案：在后台播放运行时的 `playCurrent.onCompleted` 中区分“自然播放完成”“播放失败/停止”两类结果；只有 `playCode == 0` 时才判断是否存在下一首，若存在则切换活动索引并自动继续 `playCurrent()`，若当前已是最后一首则保持停止。
   - 原因：`PlaybackProcessRuntime` 维护真实播放队列、当前曲目索引、倍速状态与后台会话，是最适合作为“播完后下一步动作”的单一决策点。这样 UI、通知栏和 MediaSession 都能跟随后台真实状态变化，不会出现双端重复推进。
   - 备选方案：
     - 在 app 前台 `PlayerRuntime` 中推进到下一首：前后台状态容易分叉，且后台播放时无效。
     - 让 `PlayerSessionPlayer`/Media3 自己管理自动切歌：当前项目的完成态与 source 生命周期仍由 runtime 主导，直接放在 session 层不如 runtime 清晰。

8. 自动切歌沿用当前会话的倍速，但不引入新的播放模式语义。
   - 方案：自动切到下一首时继续沿用当前 `PlaybackProcessState.playbackSpeed`，并复用现有 prepare/play 流程；若已是最后一首，则停止播放并保留“播放完成”结果。
   - 原因：用户已经在当前会话中确认的倍速属于会话偏好，自动切歌不应打断它；而“最后一首停止”又能与未来的“循环/随机/单曲循环”播放模式形成清晰边界。
   - 备选方案：
     - 自动切歌时重置为 `1.0X`：会打断当前会话的一致性。
     - 末曲自动回到第一首：会提前引入循环播放语义，不符合当前需求。

## Risks / Trade-offs

- [播放中频繁拖动倍速导致 native 滤镜频繁切换，出现爆音或顿挫] → UI 只在拖动结束时正式提交速度；native 更新放在帧边界处理，失败时保留上一档有效速度。
- [FFmpeg 构建未正确暴露 `libavfilter/atempo` 所需符号] → 在实现前先验证当前 `libffmpeg.so` 的 `avfilter` 能力，必要时补齐 FFmpeg 打包配置，并以 `:app:assembleDebug` 作为必跑验证。
- [UI 状态与服务真实速度不一致] → 由 `PlaybackProcessRuntime` 作为唯一权威状态源，UI 只展示远端确认值；命令失败时回滚本地临时选择并提示错误。
- [切歌、Seek 或重建播放任务时速度被意外重置] → 把当前速度挂在运行时持久状态上，而不是绑在单次 `play()` 调用或单个 source 对象上。
- [native tempo 处理带来 CPU 开销增加] → 档位范围限定在 `0.5X`~`2.0X`，优先复用 FFmpeg 原生实现，先覆盖语音/本地音频的主路径，再根据验证结果评估后续优化。
- [播放列表入口从浮动按钮迁移后可发现性下降] → 在播控右侧保留明确图标与数量展示，并保持与左侧倍速入口对称布局，降低识别成本。
- [自然播放完成与手动 stop/切歌的结果被混淆，导致误触发自动切歌] → 只在 `playCode == 0` 的自然完成分支推进下一首，其他分支沿用现有停止/错误处理。
- [自动切歌后前台列表索引、MediaSession 当前曲目和后台真实 source 不一致] → 由 `PlaybackProcessRuntime` 先推进活动索引，再复用现有 `playCurrent()` 统一切歌流程，让 session 状态跟随 runtime 单源更新。

## Migration Plan

1. 在 `app`、`playback-service`、`player` 三层新增倍速状态模型、档位映射工具和 MediaSession 自定义命令常量。
2. 为播放器控制栏增加左侧倍速入口、弹窗与 `SeekBar` 式交互，并把选择结果通过 `PlayerServiceBridge` 下发到服务。
3. 在 `PlaybackProcessRuntime` / `PlayerMediaSessionService` 中接入倍速命令处理、状态保存与 session extras 回传。
4. 扩展 `INativePlayer` / `NativePlayer` / JNI / C++ 播放链路，在重采样后接入 `atempo` tempo stage，实现变速不变调。
5. 在 `PlaybackProcessRuntime` 完成态处理中增加自然播放完成后的自动切歌决策与执行逻辑，最后一首结束时停止。
6. 将播放列表入口迁移到 `PlaybackControls` 右侧并移除旧浮动入口，保持 `BottomSheet` 行为不变但统一主控区视觉风格。
7. 增加单元测试与链路验证，运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`。
8. 如需回滚，可先恢复旧浮动入口并保留 `BottomSheet` 实现，同时让服务忽略倍速命令，native 默认退回 `1.0X` 直通播放，并保留现有“播完即停”的行为。

## Open Questions

- 首版是否需要把当前倍速持久化到本地，并在应用或服务重启后恢复？当前设计默认“不持久化，只保证当前会话一致”。
- 如果播放中切换倍速失败，产品期望是立即回退到上一个已确认档位，还是保留新档位文案并显示错误提示？实现前需要统一交互口径。
- 后续引入播放模式能力时，自动切歌后的“下一步动作”是否需要抽象成独立策略接口（顺序播放 / 列表循环 / 单曲循环 / 随机）？当前设计先按顺序播放、末曲停止收口。
