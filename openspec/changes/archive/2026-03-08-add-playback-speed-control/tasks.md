## 1. 倍速状态模型与命令契约

- [x] 1.1 定义 `0.5X` 到 `2.0X`、步进 `0.1X` 的固定倍速档位映射与展示文案工具，统一 UI、service 与 native 使用的速度索引/数值转换。
- [x] 1.2 扩展 `PlayerUiState`、`RemotePlaybackSnapshot`、`PlaybackProcessState` 与 `PlaybackMetadataExtras`，增加当前倍速状态字段并保持前后台读取一致。
- [x] 1.3 新增 `PlaybackSessionCommands` 倍速命令及其参数约定，打通 `PlayerServiceBridge` 到 `PlayerMediaSessionService` 的倍速设置调用。

## 2. 主界面入口与弹窗交互

- [x] 2.1 在歌曲控制栏左侧新增倍速入口，并显示当前生效的倍速文案。
- [x] 2.2 实现点击入口后弹出的倍速设置弹窗，在弹窗中用 `SeekBar` 风格的离散 `Slider` 展示 16 档倍速。
- [x] 2.3 将弹窗内的倍速选择接入 `PlayerViewModel` / `PlayerRuntime`，实现拖动预览与确认后下发倍速命令的交互流程。
- [x] 2.4 将播放列表入口迁移到歌曲控制栏右侧，使用与倍速入口一致的主控区 UI 样式，并移除旧浮动入口。

## 3. 后台播放会话与状态同步

- [x] 3.1 在 `PlaybackProcessRuntime` 中保存当前会话倍速，并在 prepare、play、pause/resume、seek 与切歌时保持沿用当前倍速。
- [x] 3.2 在 `PlayerMediaSessionService` 与 `PlayerSessionPlayer` 中回传当前倍速到 session extras / 元数据，保证界面重连后可恢复展示。
- [x] 3.3 处理倍速命令成功与失败后的状态同步，避免 UI 文案、service 状态与真实播放速度不一致。

## 4. Native 变速不变调实现

- [x] 4.1 扩展 `INativePlayer`、`NativePlayer`、JNI 与 C++ 播放接口，增加设置当前播放倍速的 native 控制能力。
- [x] 4.2 验证并补齐 FFmpeg `libavfilter` / `atempo` 构建接入，在现有“解码/重采样 -> PCM 消费”链路中插入变速不变调处理阶段。
- [x] 4.3 实现播放中切换倍速即时生效，并确保 seek、暂停恢复与重新开始播放后继续应用当前倍速。

## 5. 测试与回归验证

- [x] 5.1 补充倍速档位映射、session 命令分发、session extras 读写与 UI 状态同步的单元测试。
- [x] 5.2 补充播放中切换倍速、切歌后沿用倍速、seek 后保持倍速以及变速不变调链路的关键回归测试。
- [x] 5.3 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`，修复与本变更相关的失败项后再次验证。

## 6. 自然播放完成后的自动切歌

- [x] 6.1 在 `PlaybackProcessRuntime` 中补充“自然播放完成后下一步动作”的决策逻辑：非最后一首自动切到下一首并继续播放，最后一首停止播放。
- [x] 6.2 确保自动切歌复用现有 prepare/play 链路并继续沿用当前会话倍速，不引入循环播放或随机播放语义。
- [x] 6.3 补充后台播放完成态回归测试，覆盖“自动切歌”“末曲停止”“自动切歌后沿用倍速”三类场景，并重新运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`。
