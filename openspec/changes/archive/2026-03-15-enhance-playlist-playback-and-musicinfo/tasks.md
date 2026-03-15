> 已拆分为更小的子 change：
> `unify-playable-contract-and-queue-metadata`、
> `stabilize-online-playback-and-cache`、
> `add-detail-page-playback-entry`、
> `refresh-player-expanded-page`
>
> 本文件保留为历史拆分参考，不建议继续作为单个 change 的执行清单使用。

## 1. 共享模型与可播放契约

- [x] 1.1 在 `playback-contract` 中定义最小可播放基类/接口，并明确在线 `MusicInfo` 与本地 `LocalMusicInfo`（或等价本地模型）到该基类的投影边界
- [x] 1.2 调整现有 `MusicInfo` 字段命名与承载范围，补齐 `songId`、`durationMs`、`coverUrl`、歌单来源上下文、试听片段信息与请求头等共享字段
- [x] 1.3 为 `/song/detail` 新增显式 mapper，把 `id`、`name`、`ar`、`al`、`dt` 等原始字段转换为语义化领域字段，再映射到最小可播放契约

## 2. 播放列表状态与跨进程投影

- [x] 2.1 升级 app 侧播放列表状态与持久化迁移，让在线曲目和本地曲目都能围绕统一的最小可播放契约恢复与切换
- [x] 2.2 调整 MediaItem extras、远端快照和 `PlaybackTrack` 映射，确保 `durationMs`、`coverUrl`、`songId` 与请求头能够跨进程传递
- [x] 2.3 更新播放器页状态投影，让当前曲目的时长与封面直接来自远端快照，而不是依赖额外详情查询

## 3. 在线地址解析与缓存准备链路

- [x] 3.1 新增在线播放解析数据层，接通 `/song/url/v1`、`/song/url`、`/check/music`，并实现播放专用请求头构造与有效期读取
- [x] 3.2 在播放准备层接入 `CacheCore.lookup(resourceKey)` 完整性判断，只有缓存不完整时才继续解析拉流地址
- [x] 3.2.1 为在线磁盘缓存补充 clip-mode 可信标记，只允许可信 full 缓存直接命中；旧的无标记 full 缓存视为不可信并重建
- [x] 3.2.2 为在线播放准备补充“短资源误判”与 `contentLength` 纠偏防护：当返回时长或缓存探测时长明显短于稳定歌曲时长时，不再把结果当成完整音源缓存、复用或静默播完；当 provider 发现真实总长长于旧 hint 时，必须同步纠正 source / session / 持久化 metadata
- [x] 3.2.3 收紧 HTTP range provider 的 seek 读链：保留鉴权请求头透传，并在合法 offset 上遇到异常空读/立即 EOF 时主动断开重连重试，避免 seek 后长时间 buffering、无声或 `av_read_frame failed`
- [x] 3.2.4 收紧完整缓存判定：不能再只凭 `dataFileSizeBytes >= contentLength` 认定缓存完整，必须基于 `completedRanges` 连续覆盖判断，避免稀疏 data 文件把带洞缓存误判成 `cache-only`
- [x] 3.2.5 收紧 HTTPS Range 连接策略：HTTPS 读流必须保留原始域名做证书校验，不能把域名替换成裸 IP 再依赖 `Host` 头继续连接，避免 `SSLPeerUnverifiedException` 折叠成 `source.readDirect returned error`
- [x] 3.3 实现最多 10 项的 LRU URL 内存缓存，覆盖命中、过期与淘汰逻辑，并让回切上一首时优先复用未过期解析结果；试听解析结果不进入该缓存，也不占用 10 首容量
- [x] 3.4 在在线准备流程中补齐试听片段、无版权失败、`durationMsHint` 透传与本地/在线播放兼容分支

## 4. 歌单详情数据与播放入口

- [x] 4.1 扩展歌单详情数据层，按设计决定接入 `song/detail` 详情 enrichment、`/playlist/detail/dynamic` 动态信息与局部失败/重试承载
- [x] 4.2 新增详情页播放网关，把歌单详情、歌手详情与专辑详情里的“播放全部”/“点击歌曲播放”统一接到“必要时替换当前播放列表 + 用目标 index 指定激活项”的通道上，并补齐同上下文队列命中时只切换激活项的判断
- [x] 4.2.1 详情页进入播放时先以 `songId` 作为最小前提立即设置播放列表并开始播放，再统一通过 `/song/detail` 分页异步补齐封面、标题、歌手、专辑与时长，不因大列表阻塞首播
- [x] 4.3 改造歌单详情页、歌手详情页与专辑详情页 UI / ViewModel，补齐图标化“播放全部”入口、歌曲点击回调、歌单动态信息展示，以及成功发起播放后的播放量更新触发
- [x] 4.3.1 为专辑详情页播放列表构建补充封面兜底，保证歌曲行缺少专辑图时仍能把专辑封面同步进播放列表与播放页
- [x] 4.4 在个人中心页增加“本地歌曲”入口与独立页面，支持扫描本机音频、缓存扫描结果、下次打开优先展示缓存，以及右上角手动重新扫描

## 5. 播放列表行为回归与验证

- [x] 5.1 为字段 mapper、最小可播放基类投影、URL 解析回退、内存缓存命中/过期/LRU 淘汰补充单元测试，并覆盖试听解析结果不占用 URL 缓存容量，以及 `/song/detail` 分页 enrichment 对播放列表元数据的异步回填
- [x] 5.2 为歌单详情页、歌手详情页、专辑详情页、本地歌曲页与播放列表行为补充 ViewModel / Robolectric 测试，覆盖播放全部、指定曲目 index 播放、动态信息失败重试、缓存快显、手动重新扫描、删除当前激活项后的衔接，以及首次启动后第一轮切歌前的远端播放列表引导
- [x] 5.2.1 为播放页唱片暂停不复位、专辑页封面兜底、在线播放短资源误判与 `contentLength` 纠偏防护补充测试
- [x] 5.2.2 为控制器重连时的队列同步判定、MediaSession 播放页唤起，以及小屏播放页布局与唱片恢复连续性补充测试
- [x] 5.2.3 为播放中切歌竞态与在线准备的多余 rewind 补充测试，覆盖“独立 metadata probe 成功时不强制 rewind playback source”以及“旧曲目迟到 completion / 错误结果不会覆盖当前曲目状态”
- [x] 5.2.4 收紧播放页播控布局，针对窄宽度设备让播控区整体下移并缩小按钮间距/尺寸，覆盖播放模式、上一首、下一首与播放列表按钮不重叠
- [x] 5.2.5 为 seek 后 buffering 投影与 HTTP range provider 补充测试，覆盖“seek 后先进入 buffering 并冻结进度”、“网络 provider 跨过 2MiB 边界仍能连续读取并透传鉴权请求头”，以及“合法 offset 首次空读/立即 EOF 时会重连重试而不是直接返回空结果”
- [x] 5.2.6 为在线缓存完整性判定补充回归测试，覆盖“data 文件长度等于 `contentLength` 但 `completedRanges` 仍有洞时，不能误判为完整缓存”
- [x] 5.2.7 为 HTTPS provider 补充回归测试，覆盖“即使存在 IPv4 解析结果，HTTPS 连接也必须继续使用原始域名而不是改写为裸 IP”
- [x] 5.3 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`，并在需要时补充 `./gradlew :app:compileDebugAndroidTestKotlin`
