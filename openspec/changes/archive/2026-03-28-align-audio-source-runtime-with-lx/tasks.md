## 1. Source package 与设置页模型

- [x] 1.1 扩展设置页音源模型与仓库，使音源条目可表达 `SourceMetadata`、`SourceConfig`、`SourceState`，并兼容读取旧版 `baseUrl` manifest 与旧持久化音源列表
- [x] 1.2 将设置页当前音源偏好从 `preferred_audio_source_base_url` 升级为 `activeSourceConfigJson`，并保留旧偏好的“读旧写新”迁移
- [x] 1.3 更新设置页 ViewModel 与 UI，让当前音源切换、失败反馈和初始化状态展示基于新的 source package 模型工作

## 2. 跨进程 source config 边界

- [x] 2.1 用“设置当前 source config”命令替换现有“设置当前 baseUrl”命令，并补齐 `playback-contract`、`playback-orchestrator` 与 `PlayerMediaSessionService` 的桥接
- [x] 2.2 在 `PlaybackProcessRuntime` 中持久化并恢复最近一次成功生效的 `activeSourceConfigJson`，保持服务重建后的当前音源一致性
- [x] 2.3 让播放进程在切换 source 时只在 config 可解析、adapter 可创建且本地初始化成功后确认切换生效，失败时保留之前的当前音源

## 3. Native source adapter 与在线播放准备

- [x] 3.1 新增统一的 `SourceAdapter`、`SourceAction`、`SourceActionContext` 和 `SourceActionResult` 抽象，并实现 `SourceAdapterFactory`
- [x] 3.2 将现有网易兼容解析链路收编为 `NeteaseCompatibleSourceAdapter`，把固定 `/song/url/v1`、`/song/url` 逻辑移出播放主流程
- [x] 3.3 实现首版 `HttpMappingSourceAdapter`，支持单次 HTTP JSON 请求、白名单模板变量和简单路径提取 `playbackUrl`
- [x] 3.4 将在线播放准备改造为调用当前 source adapter 的 `ResolveMusicUrl`，并把默认音质作为偏好输入而不是播放成功的硬前置
- [x] 3.5 让当前 source 切换后失效旧 source 的在线播放解析缓存，并对当前在线歌曲执行重准备，同时保持本地歌曲不受影响
- [x] 3.6 为首版未实现的 `ResolveLyric`、`ResolvePic` 返回明确不支持结果，避免伪成功或静默吞掉调用

## 4. 回归验证

- [x] 4.1 为 manifest/source package 解析、旧偏好迁移、当前音源切换与失败保持原音源补充仓库 / ViewModel 测试
- [x] 4.2 为播放客户端边界与播放服务补充“设置当前 source config、恢复当前 source config、非法 config 返回失败”的命令处理测试
- [x] 4.3 为 `NeteaseCompatibleSourceAdapter`、`HttpMappingSourceAdapter` 与在线播放准备补充“解析真实播放地址、质量降级、非法映射失败、切源清缓存与在线曲目重准备”回归测试
- [x] 4.4 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
