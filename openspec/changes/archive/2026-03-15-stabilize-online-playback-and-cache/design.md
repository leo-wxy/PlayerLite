## Context

最近的在线播放问题并不是一个点，而是一串串起来的链路：URL 解析结果可能是试听或短片段，历史磁盘缓存可能带着错误的 `contentLength` 或不可信 full 标记，seek 后的 range 连接又可能遇到空读/立即 EOF，而运行时还会把旧曲目的 `PLAYING` 残留投到新曲目上。把这些问题和播放页视觉混在一起，只会让回归范围越来越失控。

## Decisions

1. 在线准备按“缓存完整性 -> URL LRU -> 远端解析”顺序决策。
   - 先看缓存是否可信完整。
   - 再看未过期的完整音源 URL 内存缓存。
   - 最后才走 `/song/url/v1` / `/song/url` 远端解析。

2. 只允许可信 full 缓存被直接复用。
   - full 缓存必须带可信 clip-mode 标记。
   - 稀疏 data 文件、旧无标记 full 缓存、明显短于稳定时长的缓存都不能当成完整音源。

3. seek / range provider 以“合法 offset 上的异常空读应重试”为原则。
   - 合法 offset 的首次空读或立即 EOF 不直接判成可信结束。
   - HTTPS 连接保留原始域名，避免因 IP 改写导致 TLS 校验失败。

4. 运行时只在“当前播放状态明确属于当前曲目”时才允许 skip。
   - `playCurrent()` 不再只看裸的 `PLAYING`。
   - 必须结合当前真正启动过播放的 `activePlaybackTrackId`。
   - 旧曲目的 completion / finally 不能覆盖当前曲目。

5. 播放服务启动语义拆成“预热连接”和“显式播放启动”。
   - `PlayerViewModel` 初始化阶段只预热 controller / session 连接，不再直接启动 playback service 进程。
   - 真正会导致播放的动作，例如播放、切歌补队列、详情页点播、恢复播放和 UI 测试入口，统一走 foreground-safe 的 service 启动。

6. 前台通知升级必须安全降级。
   - `startForeground()` 只有在显式播放启动已经发出后才尝试升级。
   - 如果系统拒绝前台升级，service 保留普通通知并记录诊断信息，而不是直接打死 `:playback` 进程。

## Verification

- 为 URL 解析、缓存完整性、短资源误判、range 空读重试和 HTTPS 域名保留补测试
- 为 `playCurrent()` 在 `preparing` 与“旧 PLAYING 残留”场景下必须重启当前曲目补回归测试
- 为预热连接、显式播放启动和前台通知安全降级补充测试
- 继续通过 `:playback-service:testDebugUnitTest` 与必要的设备端复测验证稳定性
