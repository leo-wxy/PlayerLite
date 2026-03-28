## Why

当前设置页已经能保存音源、默认音质和缓存相关偏好，但这些配置大多还停留在“表单与本地存储”层，尚未真正驱动播放服务。尤其是音源切换后，在线播放解析仍写死使用默认 API baseUrl，缓存上限也没有即时下发到播放进程，导致用户在设置页里做出的关键配置和实际播放行为不一致。

## What Changes

- 把设置页中的播放偏好从静态配置升级为可立即作用于播放进程的真实控制项，包括歌曲缓存上限、默认音质和当前启用音源。
- 将音源建模为可导入、可展示 manifest 元数据、可启用、可设为当前音源、可反馈初始化错误的本地注册表，而不是继续停留在手填基础地址。
- 让播放服务中的 `/song/music/detail`、`/song/url/v1` 与 `/song/url` 请求改走当前启用音源的 baseUrl，使音源真实参与音质目录查询和播放链接解析。
- 在切换当前音源后清空在线播放 URL / 音质目录的内存缓存，并对当前在线歌曲触发重新准备，避免沿用旧音源解析结果。
- 修正设置页清理缓存后的刷新时序，确保等待播放缓存实际清空后再回读快照，而不是仅以命令成功下发作为“已清空”。

## Capabilities

### New Capabilities
- `settings-page-playback-preferences`: 定义设置页对歌曲缓存上限、默认音质和可管理音源的展示、导入、启用与即时生效规则。

### Modified Capabilities
- `authenticated-online-playback-access`: 让受保护在线播放解析遵循当前启用音源的 baseUrl，并在音源切换后失效旧解析缓存与当前在线准备结果。

## Impact

- `app` 设置页 UI、`SettingsViewModel`、偏好存储与音源仓库需要补齐音源 manifest 元数据、本地/在线导入、当前音源切换与 richer 状态展示。
- `playback-client`、`playback-contract`、`playback-orchestrator` 和 `playback-service` 需要补齐缓存上限与当前音源 baseUrl 的跨进程命令处理。
- `playback-service` 在线解析链路需要从固定 API baseUrl 改为可切换音源 baseUrl，并在切换后清空 URL / 音质目录内存缓存、重新准备当前在线歌曲。
- 测试需要覆盖缓存清理等待真实完成、缓存上限即时下发、音源导入/切换、播放服务接收音源切换命令，以及切换音源后重新解析在线播放结果。
