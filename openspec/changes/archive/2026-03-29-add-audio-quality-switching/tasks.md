## 1. 音质模型与远端数据源

- [x] 1.1 在共享层定义稳定的音质等级模型、歌曲音质目录模型与 `/song/music/detail` 映射规则，统一 `level`、展示文案以及 `br` / `size` / `vd` / `sr` 字段承载。
- [x] 1.2 在 `app` 与 `playback-service` 分别接入 `/song/music/detail` 远端数据源、鉴权请求头透传和按 `songId` 的短时内存缓存。
- [x] 1.3 扩展 `PlaybackSessionCommands`、`PlaybackMetadataExtras`、`RemotePlaybackSnapshot`、`PlayerUiState` 等共享状态，增加偏好音质与当前实际生效音质字段。

## 2. 后台播放会话与在线播放解析

- [x] 2.1 在 `PlaybackProcessRuntime` 与相关服务命令处理链路中接入偏好音质持久化、实际生效音质计算以及成功 / 回退后的状态回传。
- [x] 2.2 重构 `OnlinePlaybackPreparationPlanner` / `OnlinePlaybackUrlResolver`，让 `/song/url/v1`、旧接口 bitrate 回退与选档策略都基于当前歌曲真实音质目录工作。
- [x] 2.3 更新在线 `resourceKey`、地址缓存和完整缓存复用判断，使其同时按歌曲标识与当前实际生效音质隔离。
- [x] 2.4 处理当前曲目切换音质时的重新准备逻辑，保证支持音质切换的在线歌曲在当前位置重准备，不支持的音源稳定退化。

## 3. 播放页联合状态区与音质浮层

- [x] 3.1 在前台 runtime / ViewModel 中增加音质目录加载、loading / empty / unsupported 状态以及音质浮层开关控制。
- [x] 3.2 在播放页进度条下方实现联合状态区，支持“仅音质”“音质 · 音效”“仅音效”和整行隐藏四种展示结果。
- [x] 3.3 将联合状态区的音质片段接入音质选择半屏浮层，展示当前歌曲真实可用的音质列表与辅助信息，并支持当前实际生效项高亮。

## 4. 音质浮层交互与联合状态区收敛

- [x] 4.1 让联合状态区中的音质片段可直接打开音质选择半屏浮层，并确保音质浮层与其他播放器设置承载保持互斥显示。
- [x] 4.2 让联合状态区始终展示当前实际生效值，确保音质回退、音效恢复原声和前后台重连后的文案同步收敛。

## 5. 测试与验证

- [x] 5.1 补充或更新音质目录 mapper、共享契约、metadata extras 与远端快照映射的单元测试。
- [x] 5.2 补充或更新播放服务 / 在线播放解析测试，覆盖选档命中、音质回退、缓存按音质隔离、切换音质重准备和不支持音源退化。
- [x] 5.3 补充或更新播放页 UI / ViewModel 测试，覆盖联合状态区的四种展示、音质片段点击、音质浮层互斥以及回退后的显示收敛。
- [x] 5.4 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`，修复与本变更相关的失败项后再次验证。
