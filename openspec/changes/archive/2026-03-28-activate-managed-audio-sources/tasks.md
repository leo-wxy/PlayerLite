## 1. 设置页播放偏好与音源状态

- [x] 1.1 扩展 `ManagedAudioSource`、`SettingsSourcesUiState` 和音源仓库，使音源条目可表达 manifest 元数据、启用状态、当前音源与初始化错误
- [x] 1.2 在设置页 UI 中补齐在线导入、本地导入、当前音源切换和 richer 音源条目展示
- [x] 1.3 让 `SettingsViewModel` 串起默认音质、歌曲缓存上限和当前音源的持久化、即时下发与反馈文案

## 2. 跨进程播放设置命令

- [x] 2.1 为歌曲缓存上限和当前音源 baseUrl 补齐 `PlaybackSessionCommands` 支持、客户端桥接和 `PlayerMediaSessionService` 命令处理
- [x] 2.2 在 `PlaybackProcessRuntime` 中持久化并恢复 `playbackCacheLimitBytes` 与 `preferredAudioSourceBaseUrl`
- [x] 2.3 让播放进程在接收到设置变更时更新内存配置，并保持服务重建后的恢复一致性

## 3. 在线解析与重准备链路

- [x] 3.1 将在线播放解析与音质目录查询改造为基于当前启用音源 baseUrl 的动态 remote data source
- [x] 3.2 为 URL 内存缓存和音质目录缓存补齐当前音源隔离或切换时清空逻辑
- [x] 3.3 在切换当前音源时，对当前在线歌曲执行“清缓存 + 重准备”，并保持播放/暂停与位置语义
- [x] 3.4 让歌曲缓存上限映射到 `CacheCoreConfig.diskCacheMaxBytes`，并在播放进程中即时应用
- [x] 3.5 校正设置页清理缓存反馈链路，确保等待播放缓存真实清空后的新快照再报成功

## 4. 回归验证

- [x] 4.1 为音源导入、当前音源切换、默认音质和缓存上限补充设置页仓库 / ViewModel / UI 测试
- [x] 4.2 为播放客户端和播放服务补充“设置当前音源 / 设置缓存上限”命令处理测试
- [x] 4.3 为在线播放解析补充“当前音源驱动 baseUrl、切换音源清空缓存、当前在线歌曲重准备”回归测试
- [x] 4.4 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
