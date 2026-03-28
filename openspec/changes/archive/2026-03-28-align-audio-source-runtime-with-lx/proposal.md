## Why

当前仓库里的“自定义音源”本质上仍是 `baseUrl` 切换器：设置页保存的是地址，播放服务在固定的 `/song/url/v1`、`/song/url`、`/song/music/detail` 协议上替换 host。这个模型和 LX Music 公开出来的“初始化 + action 分发 + 由音源返回结果”的处理边界差异过大，继续沿当前方向扩展，会把未来的歌词、封面甚至 JS runtime 接入都推向返工。

## What Changes

- 把自定义音源从“地址配置项”升级成“source package + native action-based adapter”模型，支持独立的元数据、运行时配置和初始化状态。
- 新增统一的 `SourceAdapter` / `SourceAction` / `SourceActionResult` 抽象，首版只实现 `ResolveMusicUrl`，保留 `ResolveLyric` 与 `ResolvePic` action 但不实现。
- 支持两类原生解析源：
  - `netease-compatible`：收编现有基于 `/song/url/v1`、`/song/url` 的实现。
  - `http-mapping`：支持单次 HTTP JSON 请求并从响应中提取真实播放地址。
- 用“当前 source config”替代“当前 `baseUrl`”作为跨进程偏好与命令载体，让 playback-service 通过 adapter factory 构造当前音源能力，而不是直接拼固定接口。
- 保留旧 manifest、旧 `preferred_audio_source_base_url` 偏好和旧持久化音源列表的读取兼容，采用“读旧写新”策略平滑迁移。

## Capabilities

### New Capabilities
- `audio-source-runtime-adapters`: 定义 source package、原生 action-based source adapter、初始化状态和首版 `ResolveMusicUrl` 能力边界。

### Modified Capabilities
- `authenticated-online-playback-access`: 将受保护在线播放的真实播放地址解析从固定接口 host 替换升级为“当前音源 adapter 解析”。
- `playback-service-boundary`: 扩展稳定的播放客户端边界以承载当前 source config 的跨进程下发与恢复，而不是继续只传 `baseUrl`。

## Impact

- `app` 的设置页模型、仓库和 ViewModel 需要从 `baseUrl` 模型升级到 `metadata + config + state` 模型，并支持 source package 导入与旧数据兼容读取。
- `playback-contract`、`playback-orchestrator` 与 `playback-service` 需要把当前音源下发命令从 `preferredAudioSourceBaseUrl` 升级为 `activeSourceConfigJson`。
- `playback-service` 的在线播放准备链路需要引入 `SourceAdapterFactory`、`SourceAdapter`、`SourceActionContext` 和 `SourceActionResult`，并把现有网易兼容解析逻辑收编为具体 adapter。
- 测试需要覆盖 manifest 新旧格式兼容、source config 跨进程命令、adapter 初始化与失败状态、`ResolveMusicUrl` 成功链路，以及切换当前 source 后的缓存失效与在线曲目重准备。
