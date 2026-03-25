## 1. 共享状态与跨进程契约

- [x] 1.1 在 `playback-contract` / `playback-client` 中新增稳定的音效预设模型、wire value、metadata extras 和远端快照映射
- [x] 1.2 扩展 `PlayerUiState`、`PlayerRuntime`、`PlaybackProcessState` 与相关共享状态，使当前音效预设成为和倍速并列的播放会话字段
- [x] 1.3 为播放服务补齐“设置音效预设”命令、结果回传与失败回退路径，保持与现有倍速 optimistic update / rollback 语义一致

## 2. 播放页设置入口与页面交互

- [x] 2.1 将播放页右上角三点按钮从临时 toast 改为半屏“更多操作”浮层，并在首层提供 `音效设置` 与 `倍速设置` 两个入口
- [x] 2.2 在当前半屏浮层内接入原有离散倍速档位选择，确保 `0.5X` 到 `2.0X` 的选择、高亮和默认值行为保持不变
- [x] 2.3 在播放器上下文内实现独立音效设置子页面，承载固定音效预设列表与当前预设高亮
- [x] 2.4 在播放页进度条下方增加当前音效名称展示，并确保默认状态显示 `原声` 且与进度区、播控区布局稳定兼容

## 3. 播放服务与 native 音频链路

- [x] 3.1 在 `PlaybackProcessRuntime`、`PlayerSessionPlayer` 与相关服务层逻辑中接入音效状态恢复、切歌沿用、Seek 保持和服务重连同步
- [x] 3.2 扩展 `NativePlayer` / JNI 接口，把音效预设从 Kotlin 层传到 native 播放器
- [x] 3.3 将 `ffmpeg_decoder.cpp` 中当前仅处理 `atempo` 的滤镜处理器升级为可组合的音效 + 倍速滤镜链，并为 `off`、`bass-boost`、`vocal-boost`、`bright`、`warm` 预设建立保守映射
- [x] 3.4 为滤镜不可用、参数非法或图初始化失败补齐安全降级，确保播放会话回退到 `原声` 而不是直接中断

## 4. 验证与回归

- [x] 4.1 为播放页三点浮层、倍速内联展开、音效设置子页面和进度条下方当前音效名称展示补充或更新 UI / ViewModel 测试
- [x] 4.2 为共享契约、metadata extras、远端快照映射、音效状态同步和失败回退补充或更新单元测试
- [ ] 4.3 为播放服务与 native 链路补充或更新“切歌 / Seek / 后台播放保持音效”、“倍速与音效同时生效”以及“滤镜失败回退原声”的回归验证
- [x] 4.4 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
