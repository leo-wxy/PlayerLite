# PlayerLite

一个面向 Android 的音频播放器示例工程，基于 Compose、Media3、FFmpeg、JNI 和 Native Cache Core 构建。项目当前已经覆盖播放器主链路、首页发现、搜索、歌手 / 歌单 / 专辑详情、歌曲详情、登录与用户中心等核心能力，并补齐了独立 `PlayerActivity` 播放器页、首页 / 详情页 `minibar`、播放页歌词、共享歌词摘要、系统 `MediaSession` 动态展示链路，以及当前音源管理、在线音质切换、默认音效 preset、横屏播放器独立沉浸布局、首页每日推荐站内入口与每日推荐歌曲页、首页横向歌曲推荐区块、搜索页共享主题统一和网页歌单导入等较完整的播放能力。在线播放链路已补充当前播放项缓存进度投影、完整缓存本地优先起播、有限 current-ahead / next-track 预热和预热状态隔离，`minibar` 与播放页可在同一进度条上展示已播放、已缓存未播放和未缓存状态，并支持 seek 后基于缓存 range 展示新的缓存段。项目结构已收敛为 10 个业务模块：发现流、详情能力、数据层和应用侧播放 API 分别聚合，`app` 继续只承担宿主、装配与入口适配职责。

## 主要能力

- 本地音频播放，支持 `content://`、`file://`、`http://`、`https://`
- 独立 `:playback-service` 进程后台播放，接入 `MediaSessionService`、系统通知、锁屏控制和外部控制器
- 播放列表管理与持久化恢复，支持删除、激活切换、拖拽排序
- 播放模式：列表循环、单曲循环、随机播放
- 网络音源 Range 播放、边播边缓存、缓存清理，以及当前播放项缓存进度展示
- 在线缓存进度由播放服务权威投影到前台，`minibar` 与播放页在同一进度条上展示缓存段；完整缓存时直接拉满，seek 后按新的 offset / length range 展示缓存区间
- 完整在线播放缓存优先作为本地缓存源使用，完整命中时不再等待在线 URL 解析或重新下载同一完整资源
- 在线读取链路提供有限 ahead 预读窗口和默认内存缓存预算，避免缓存条长期贴着播放进度移动，同时不把预读等同于整首下载
- 在线播放预热支持 `current-ahead` 和 `next-track` 两类目标，当前项未完整缓存时优先推进当前项有限 ahead window，当前项完整或无需继续写入后再评估下一首首段预热
- 预热状态通过独立 `PlaybackPrewarmSnapshot` 发布，区分 `Ready` 和 `Completed`，并隔离旧队列、旧音源或旧音质下的晚到结果
- 在线缓存与预热诊断日志收敛为状态边界日志，覆盖 resource key、缓存字节、目标字节、上下文签名和原因，减少正常读取循环的长期噪声
- 在线受保护播放登录前置卡口、当前音源管理与最近一次成功音源配置恢复
- 支持内置与自定义 JSON 音源配置，当前 native runtime 支持 `netease-compatible` 与 `http-mapping`
- 在线歌曲真实可用音质目录、偏好音质与当前实际生效音质分离、切换音质时按当前位置重准备
- 播放页“更多操作”半屏浮层，统一承载倍速设置、音效设置与当前播放器上下文内的设置入口
- 设置页集中管理播放与缓存偏好，包括启动恢复、断点续播、弱网自动重试、缓存失败提示、缓存容量上限、默认音质和在线播放预热预算
- 在线播放缓存不再受“仅 Wi-Fi 自动缓存”或“移动网络只播放不缓存”限制，移动网络下主动播放和预热仍写入受管磁盘缓存
- 默认音效预设：`原声`、`低音增强`、`人声增强`、`清亮高频`、`温暖柔和`
- 倍速与音效共用同一条 FFmpeg native 音频滤镜链路，支持同时生效与失败安全回退
- 共享 `design-system` 主题 token，统一首页、搜索、播放器展开页、播放列表与 `minibar` 的主强调色、次级信息和容器层级
- 独立 `PlayerActivity` 作为完整播放器唯一宿主，首页和详情页通过 `minibar` 进入播放器
- 播放展开页支持“歌曲 / 歌词”双页面切换、当前歌词摘要、完整歌词自动滚动与高亮
- 播放展开页支持进度条下方的音质 / 音效联合状态区，以及音质直达选择入口
- 独立 `PlayerActivity` 支持页面内方向模式控制，可在 `自动`、`锁定横屏`、`锁定竖屏` 间切换
- 横屏播放器提供独立沉浸式布局、右侧主视觉区和局部倒影骨架，横竖屏切换时保持同一播放上下文
- 首页 `minibar` 主体打开独立播放器页，播放列表按钮在当前首页直接展开本地播放列表浮层
- 首页 / 详情页 / 播放器页复用统一播放列表 half-sheet，支持删除、重排、随机顺序切换与播放模式切换
- 专辑 / 歌手 / 歌单详情页共享底部 `minibar` chrome，主体点击进入独立播放器页，播放列表按钮在当前页打开本地列表 sheet
- 歌手 / 专辑 / 歌单详情页采用 `hero + sticky tabs + HorizontalPager` 结构，支持头部与当前 tab 列表之间的连续纵向滚动接力
- 首页 `minibar`、详情页 `minibar` 与系统 `MediaSession` 可随播放进度动态展示当前歌词摘要，并稳定回退为 `歌名 - 歌手`
- 首页发现流、悬浮搜索入口、热搜 / 搜索建议 / 搜索结果页
- 独立歌曲详情页与统一歌曲对象入口：在线歌曲支持详情、收藏、分享、查看专辑 / 歌手，本地歌曲按来源降级承载基础信息与播放动作
- 首页 `song` 资源块采用“三首一列、横向滑动”的歌曲推荐区块，点击任意歌曲会按当前 block 的完整队列替换播放列表并从当前项开始播放
- 首页“每日推荐”快捷入口可直达站内每日推荐歌曲页，登录态下支持“播放全部”、单曲播放与推荐理由展示
- 搜索页基于共享 `design-system` token 统一背景、面板、分割线与强调色，历史搜索、热搜板、建议列表和结果列表保持同一套红白主题层级
- 搜索单曲结果主点击按当前可见结果列表直接开播，右侧三个点作为轻量详情入口直达歌曲详情页
- 首页歌曲、本地歌曲与每日推荐歌曲统一通过三点更多入口承载“下一首播放 / 查看歌曲详情”等次级动作；搜索结果继续保持“整行播放、三点直达详情”
- 歌手详情、歌单详情、专辑详情，以及首页/搜索/个人中心/喜欢页到详情页的跳转闭环
- 手机号/邮箱登录、用户会话恢复、个人中心主页、喜欢内容页、最近播放页与本地歌曲入口
- 个人中心采用“资料头部 + 喜欢 / 最近 / 本地快捷入口 + 自建歌单”结构
- 支持从网页链接导入歌单并落入现有播放列表 / 详情页链路

## 模块划分

- `:app`
  - 应用壳层、`Application`、`MainActivity` 双 Tab 主壳、`PlayerActivity` 宿主、`LikedContentActivity`、`RecentSongsActivity`、跨 feature 路由、Activity 适配器与 composition root
- `:feature-discovery`
  - 首页与搜索发现流，承载 screen、状态模型、ViewModel、repository、JSON mapper、动作模型与宿主依赖契约
- `:feature-details`
  - 歌单、专辑、艺人和歌曲详情，以及共享 detail scaffold、分页、滚动接力和 hero UI 能力
- `:feature-player`
  - 播放器 feature 展示层，承载 `PlayerScreen`、播放器页面态模型、歌词展示辅助、播放器入口 contract 与宿主 callbacks
- `:core-data`
  - 网络基础设施、用户会话，以及播放列表控制、持久化、active-index / shuffle / 重排规则
- `:playback-api`
  - 跨进程播放协议与共享模型、MediaController 客户端桥接、队列同步及 transport / settings / queue 编排
- `:playback-service`
  - 独立播放进程、`MediaSessionService`、播放运行时、在线缓存进度解析、完整缓存本地优先起播、预热调度与后台播放宿主实现
- `:player`
  - Kotlin 播放器 API 与 JNI 桥接，驱动 Native C++ + FFmpeg
- `:cache-core`
  - Native-first 缓存核心，提供 Range 缓存、会话能力、缓存进度事件、完整缓存 lookup 与有限 ahead 预读窗口
- `:design-system`
  - 共享主题 contract、语义色 token 与首页/搜索/播放器复用的视觉基础
- `build-logic`
  - 共享 Gradle convention plugin，集中治理 Android application / library / Compose 构建约束

## 支持的数据源

- `content://`
- `file://`
- `http://`
- `https://`

## 关键链路

播放器 UI 入口链路：

```text
HomeOverviewScreen / DetailMiniPlayerBar / MediaSession content intent
  -> PlayerEntry (:feature-player)
  -> PlayerActivity (:app host)
  -> PlayerScreen (:feature-player)
```

详情页滚动与播放入口链路：

```text
ArtistDetailActivity / AlbumDetailActivity / PlaylistDetailActivity
  -> feature-details
  -> MusicDetailScaffold / DetailVerticalScrollHandoff
  -> DetailMiniPlayerBar
  -> PlayerActivity / PlaylistBottomSheet (:feature-player)
```

应用侧播放编排链路：

```text
MainActivity / PlayerActivity / BasePlaybackDetailActivity
  -> PlayerViewModel / HomeViewModel (:feature-discovery)
  -> AppPlaybackGraph / PlayerRuntime (:app)
  -> playback-api
  -> PlayerServiceBridge (:playback-api)
  -> PlayerMediaSessionService (:playback-service)
  -> PlaybackProcessRuntime
  -> NativePlayer
  -> JNI / FFmpeg / AudioTrack
```

播放列表域链路：

```text
PlayerRuntime / PlayerViewModel / DetailPlaybackGateway
  -> PlaylistController (:core-data)
  -> PlaylistStorage / SharedPreferencesPlaylistStorage
  -> active-index / reorder / shuffle state
```

网络缓存链路：

```text
OkHttpRangeDataProvider
  -> CachedNetworkSource
  -> CacheCore / CacheSession
  -> FFmpeg playback pipeline
```

当前播放项缓存进度投影链路：

```text
CacheCore / CacheProgressEvents
  -> CachedNetworkSource
  -> PlaybackCacheProgressEmitter / PlaybackCacheProgressResolver
  -> PlaybackMetadataExtras / RemotePlaybackSnapshot
  -> PlayerRuntime / PlayerUiState
  -> SharedMiniPlayerBar / PlayerScreen
```

在线播放预热链路：

```text
PlaybackProcessRuntime
  -> PlaybackPrewarmCoordinator
  -> OnlinePlaybackPreparationPlanner
  -> CacheCore / CacheSession
  -> PlaybackPrewarmSnapshot
  -> PlaybackMetadataExtras / RemotePlaybackSnapshot
```

## 构建环境

- Android Studio（AGP 9.1.x）
- Android SDK `36`
- NDK `27.0.12077973`
- Java `11`
- Node.js `20+`

如果需要运行 OpenSpec 或 `.codex` 相关流程，请优先使用 Node 20 以上版本。

## 快速开始

初始化 FFmpeg submodule：

```bash
git submodule update --init --recursive
```

构建 FFmpeg Android 动态库：

```bash
bash scripts/build_ffmpeg_android.sh
```

安装 Debug 包：

```bash
./gradlew :app:installDebug
```

## 常用验证命令

```bash
./gradlew :core-data:testDebugUnitTest
./gradlew :playback-api:testDebugUnitTest
./gradlew :feature-discovery:testDebugUnitTest
./gradlew :feature-details:testDebugUnitTest
./gradlew :feature-player:testDebugUnitTest
./gradlew :cache-core:testDebugUnitTest
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

OpenSpec 全量校验：

```bash
PATH=/Users/wxy/.nvm/versions/node/v20.20.0/bin:$PATH openspec validate --all
```

## 架构说明

- `app` 已从“播放器 UI + 首页发现流 + 播放域核心 + 宿主壳”收口为应用入口、Activity 宿主、装配与 composition root；当前仍保留 `PlayerViewModel`、`PlayerRuntime` 与 `AppPlaybackGraph` 这类应用侧宿主逻辑。
- `feature-discovery` 聚合首页与搜索发现流，保留各自 package 和宿主依赖边界，减少重复的 Android Library 构建任务。
- `core-data` 聚合网络、用户会话和播放列表域，保持原有 package、持久化 key 与序列化协议不变。
- `playback-api` 聚合共享播放模型、客户端桥接和应用侧编排，`playback-service` 仍作为独立进程实现模块存在。
- `feature-player` 负责播放器页的 presentation 层、入口 contract 与宿主 callbacks，不再把播放器 UI 主体留在 `app/feature/player/ui`。
- `feature-details` 聚合歌单、专辑、艺人、歌曲详情和共享 detail shell，页面内部仍按 package 保持领域分区。
- 播放服务实现细节收口在播放层，宿主通过 `:playback-api` 接入缓存进度、预热快照、MediaSession command 和队列协议。
- 共享 Android 构建约束由 `build-logic` 提供，模块脚本主要保留命名空间、依赖和 native 等差异配置。

## GitHub 发布

仓库已预埋基于 GitHub Actions 的 tag 发布流程：

- 推送符合 `v*` 规则的 tag，例如 `v0.1.0`
- Actions 会从 `vX.Y.Z` tag 自动写入 APK 的 `versionName=X.Y.Z` 和对应 `versionCode`
- Actions 会自动执行 `./gradlew :app:assembleRelease`
- 构建成功后，`release APK` 会自动附加到对应的 GitHub Release

示例：

```bash
git tag v0.1.0
git push origin v0.1.0
```

## 调试提示

- 未登录启动时会先进入 `LoginActivity`，右上角可以直接跳过，继续使用本地播放
- 本地 HTTP Range 调试可直接使用仓库根目录的测试音频 `local-media-ui-test.mp3`
- 启动本地 Range 服务：

```bash
python3 scripts/range_http_server.py --port 18080 --directory .
```

- Android 模拟器中可通过 `http://10.0.2.2:18080/local-media-ui-test.mp3` 访问宿主机服务

## 网络接口来源

当前仓库中的网易云音乐相关网络接口基于：

- [`neteasecloudmusicapienhanced/api-enhanced`](https://github.com/neteasecloudmusicapienhanced/api-enhanced)

当前已接入或依赖的能力包括：

- 首页发现区块
- 每日推荐歌曲 `/recommend/songs`
- 默认搜索关键词与搜索结果
- 登录与用户资料
- 歌词接口 `/lyric?id=<songId>`
- 歌手/歌单/专辑详情相关接口
- 用户中心收藏歌手 `/artist/sublist`
- 用户中心收藏专栏 `/topic/sublist`
- 用户中心收藏 MV `/mv/sublist`
- 用户自建歌单 `/user/playlist/create`
- 用户收藏歌单 `/user/playlist/collect`
- 最近播放歌曲 `/record/recent/song`

## 目录概览

```text
player-lite/
├── app/
├── build-logic/
├── core-data/
├── feature-discovery/
├── feature-details/
├── feature-player/
├── design-system/
├── playback-api/
├── playback-service/
├── player/
├── cache-core/
├── scripts/
├── openspec/
└── README.md
```
