## Why

在线地址解析、缓存完整性、seek/range 读流和播放状态机当前是最容易引发“卡缓冲”“-5”“source rewind failed”“切歌后不自动播放”的一组问题。把这条链路独立成 change，可以把运行时稳定性和可验证边界从 UI 与详情页能力里拆出来。

## What Changes

- 收口 `/song/url/v1`、`/song/url`、试听信息与请求头构造
- 在在线播放准备链路里补齐完整缓存判定、clip-mode 可信标记与 URL LRU 复用
- 修正 `contentLength`、短资源误判和稀疏缓存误判问题
- 收紧 HTTP range provider 的 seek、空读、EOF 与 HTTPS 域名处理
- 为播放服务增加“当前播放状态属于哪一首歌”的护栏，避免旧 `PLAYING` 残留把新曲目误 skip
- 让 seek / buffering 与切歌后的自动播放行为更稳定、更可解释

## Capabilities

### Modified Capabilities
- `authenticated-online-playback-access`: 在线地址解析、缓存完整性、试听与 range 读取稳定性
- `playback-state-authority`: seek / buffering 投影、切歌状态归属与自动播放行为

## Impact

- `playback-service` 的在线准备、缓存和运行时状态机
- `cache-core` 的缓存完整性与 metadata 修正链路
- `HttpRangeDataProvider` / `CachedNetworkSource`
- 相关播放稳定性测试与 live debug 验证
