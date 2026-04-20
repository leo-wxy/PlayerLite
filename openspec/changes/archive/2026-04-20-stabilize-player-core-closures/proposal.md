## Why

当前仓库虽然已经具备在线播放、歌词、音质切换、最近播放和歌单等基础能力，但播放器长期版本的三个核心闭环还没有完全收口：播放恢复与切歌在弱网或服务重建后仍可能不稳，在线资源在版权失效、下架或 URL 过期时缺少统一兜底，队列、最近播放和单曲来源切换之间的行为边界也还不够清晰。继续往外扩功能面之前，需要先把“点了能播、播了稳定、失败可恢复、队列语义一致”这三条主链路做稳。

## What Changes

- 修改后台播放恢复、播放状态权威投影和播放切换规则，确保断点续播、切歌稳定、缓冲失败重试和弱网恢复收口到统一播放主链路。
- 修改受保护在线播放准备与 source runtime 规则，统一处理搜索落到可播上下文、URL 过期刷新、资源失效分类和主音源失败时的单曲级别备用源兜底。
- 修改在线音质切换规则，要求切换时按当前位置重准备，并在 URL 刷新或资源重解析后保持当前播放上下文连续。
- 修改共享播放契约、播放列表与最近播放规则，明确“下一首播放”“最近播放”“收藏 / 歌单”和“单曲来源切换”之间的边界，避免互相污染。
- 明确本次不扩展下载中心、跨设备同步、重发现流和多源聚合搜索。

## Capabilities

### New Capabilities

### Modified Capabilities
- `background-playback-service`: 服务重建后的断点续播、缓冲失败重试和弱网恢复需要更稳定的恢复与安全降级规则。
- `playback-state-authority`: app 对远端快照的采用时机、切歌过渡态和旧播放 completion 的隔离规则需要收口。
- `authenticated-online-playback-access`: 在线资源失效、URL 过期刷新和单曲级别资源兜底需要统一的在线播放准备语义。
- `audio-source-runtime-adapters`: 当前歌曲需要支持单曲级别的来源切换和主备 source 兜底，而不污染全局默认 source。
- `playback-audio-quality-switching`: 音质切换需要在 URL 重解析和资源刷新后保持当前位置与当前播放上下文。
- `playable-contract-and-queue-metadata`: 共享队列元数据需要保留在线歌曲稳定身份与单曲来源切换所需的上下文。
- `playlist-management`: “下一首播放”、当前播放队列和单曲来源切换之间的语义边界需要明确。
- `recent-playback-multi-tabs`: 最近播放的写入时机需要收口为实际成功开播，而不是点击即记录。

## Impact

- `playback-orchestrator`、`playback-client`、`playback-service` 与 `player` 的播放恢复、切歌、失败重试和状态同步链路
- 在线地址解析、source adapter、主备 source 兜底和 URL 刷新逻辑
- 当前歌曲音质切换与在线播放重准备路径
- `playlist-core`、共享播放契约、最近播放记录和相关 mapper / storage
- 播放、队列、最近播放、音质切换与在线资源准备相关的单元测试、Robolectric 测试和回归验证
