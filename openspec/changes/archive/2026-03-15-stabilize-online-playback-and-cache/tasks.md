## 1. 在线地址解析与缓存可信度

- [x] 1.1 收口 `/song/url/v1`、`/song/url`、试听片段信息、请求头构造与 10 项完整音源 URL LRU 复用
- [x] 1.2 为 full 磁盘缓存补齐可信 clip-mode 标记、稀疏缓存识别与短资源误判防护
- [x] 1.3 为 `contentLength` 与真实总长不一致的历史缓存补充 source / session / metadata 纠偏逻辑

## 2. Seek / range 读链稳定性

- [x] 2.1 收紧 HTTP range provider 的 seek 后空读、立即 EOF 与瞬时 0 字节读取处理
- [x] 2.2 收紧 HTTPS 连接策略，确保 range 读取保留原始域名完成证书校验
- [x] 2.3 当独立 metadata probe 已成功时，不再要求 playback source 执行无意义 rewind

## 3. 播放状态机护栏

- [x] 3.1 为 `PlaybackProcessRuntime` 增加当前活跃播放曲目的显式标记，避免旧 `PLAYING` 残留把新曲目误 skip
- [x] 3.2 收紧旧 completion / finally 的忽略规则，保证切歌后目标曲目继续自动播放
- [x] 3.5 收紧换队列命令链：当旧状态已经处于继续播放语义时，`setMediaItems(...)` 替换列表后直接拉起当前目标曲目，避免卡在 `prepare ready` 但未起播
- [x] 3.3 拆分“预热连接”和“显式播放启动”，初始化阶段不再直接启动 service，显式播放动作统一走 foreground-safe 启动
- [x] 3.4 为前台通知升级增加安全降级，在 `startForeground()` 被拒绝时保留普通通知并避免打死 `:playback` 进程

## 4. 验证

- [x] 4.1 为 URL 解析、缓存完整性、seek/range provider 与播放状态机护栏补充测试
- [x] 4.2 为预热连接、显式播放启动和前台通知安全降级补充测试
- [x] 4.3 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
