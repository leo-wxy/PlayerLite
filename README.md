# PlayerLite

一个面向 Android 的音频播放器示例工程，基于 Compose、Media3、FFmpeg、JNI 和 Native Cache Core 构建。项目当前已经覆盖播放器主链路、首页发现、搜索、歌手 / 歌单 / 专辑详情、登录与用户中心等核心能力，并补齐了独立 `PlayerActivity` 播放器页、首页 / 详情页 `minibar`、播放页歌词、共享歌词摘要、系统 `MediaSession` 动态展示链路，以及当前音源管理、在线音质切换、默认音效 preset 和网页歌单导入等较完整的播放能力。近期进一步完成了项目结构重构：播放器展示层拆到 `:feature-player`，播放列表域拆到 `:playlist-core`，应用侧播放编排拆到 `:playback-orchestrator`，`app` 退回到宿主、装配与入口适配职责。

## 主要能力

- 本地音频播放，支持 `content://`、`file://`、`http://`、`https://`
- 独立 `:playback-service` 进程后台播放，接入 `MediaSessionService`、系统通知、锁屏控制和外部控制器
- 播放列表管理与持久化恢复，支持删除、激活切换、拖拽排序
- 播放模式：列表循环、单曲循环、随机播放
- 网络音源 Range 播放、边播边缓存、缓存清理
- 在线受保护播放登录前置卡口、当前音源管理与最近一次成功音源配置恢复
- 支持内置与自定义 JSON 音源配置，当前 native runtime 支持 `netease-compatible` 与 `http-mapping`
- 在线歌曲真实可用音质目录、偏好音质与当前实际生效音质分离、切换音质时按当前位置重准备
- 播放页“更多操作”半屏浮层，统一承载倍速设置、音效设置与当前播放器上下文内的设置入口
- 默认音效预设：`原声`、`低音增强`、`人声增强`、`清亮高频`、`温暖柔和`
- 倍速与音效共用同一条 FFmpeg native 音频滤镜链路，支持同时生效与失败安全回退
- 共享 `design-system` 主题 token，统一首页、搜索、播放器展开页、播放列表与 `minibar` 的主强调色、次级信息和容器层级
- 独立 `PlayerActivity` 作为完整播放器唯一宿主，首页和详情页通过 `minibar` 进入播放器
- 播放展开页支持“歌曲 / 歌词”双页面切换、当前歌词摘要、完整歌词自动滚动与高亮
- 播放展开页支持进度条下方的音质 / 音效联合状态区，以及音质直达选择入口
- 首页 `minibar` 主体打开独立播放器页，播放列表按钮在当前首页直接展开本地播放列表浮层
- 首页 / 详情页 / 播放器页复用统一播放列表 half-sheet，支持删除、重排、随机顺序切换与播放模式切换
- 专辑 / 歌手 / 歌单详情页共享底部 `minibar` chrome，主体点击进入独立播放器页，播放列表按钮在当前页打开本地列表 sheet
- 歌手 / 专辑 / 歌单详情页采用 `hero + sticky tabs + HorizontalPager` 结构，支持头部与当前 tab 列表之间的连续纵向滚动接力
- 首页 `minibar`、详情页 `minibar` 与系统 `MediaSession` 可随播放进度动态展示当前歌词摘要，并稳定回退为 `歌名 - 歌手`
- 首页发现流、悬浮搜索入口、搜索结果页
- 歌手详情、歌单详情、专辑详情，以及首页/搜索/个人中心/喜欢页到详情页的跳转闭环
- 手机号/邮箱登录、用户会话恢复、个人中心主页、喜欢内容页、最近播放页与本地歌曲入口
- 个人中心采用“资料头部 + 喜欢 / 最近 / 本地快捷入口 + 自建歌单”结构
- 支持从网页链接导入歌单并落入现有播放列表 / 详情页链路

## 模块划分

- `:app`
  - 应用壳层、`Application`、`MainActivity` 双 Tab 主壳、`PlayerActivity` 宿主、`LikedContentActivity`、`RecentSongsActivity`、跨 feature 路由、Activity 适配器与 composition root
- `:playlist-core`
  - 播放列表域核心，承载 `PlaylistController`、`PlaylistStorage`、状态编解码、active-index / shuffle / 重排规则
- `:playback-orchestrator`
  - 应用侧播放编排层，承载队列同步、transport / settings / queue 控制器、detail 播放 gateway 与远端播放状态协同
- `:feature-player`
  - 播放器 feature 展示层，承载 `PlayerScreen`、播放器页面态模型、歌词/百科展示辅助、播放器入口 contract 与宿主 callbacks
- `build-logic`
  - 共享 Gradle convention plugin，集中治理 Android application / library / Compose 构建约束
- `:feature-detail-support`
  - 详情页共享 UI 支撑：`MusicDetailScaffold`、paging footer、vertical scroll handoff、hero brush、状态卡与 detail chrome 基础能力
- `:feature-playlist-detail`
  - 歌单详情页 feature：repository、mapper、UI state、ViewModel、screen composable
- `:feature-album-detail`
  - 专辑详情页 feature：repository、mapper、UI state、ViewModel、screen composable
- `:feature-artist-detail`
  - 艺人详情页 feature：repository、mapper、UI state、ViewModel、screen composable
- `:feature-search`
  - 独立搜索页模块，承载搜索首页、结果页、详情路由与搜索状态管理
- `:design-system`
  - 共享主题 contract、语义色 token 与首页/搜索/播放器复用的视觉基础
- `:network-core`
  - `OkHttp + kotlinx.serialization` 网络基础设施、统一错误映射
- `:user`
  - 登录、用户资料、会话持久化与恢复等账户 / 会话能力
- `:playback-client`
  - 前台到播放服务的 `MediaController` 桥接与稳定播放客户端边界
- `:playback-contract`
  - 跨模块共享的播放协议、DTO、Session command、队列与播放域共享模型
- `:playback-service`
  - 独立播放进程、`MediaSessionService`、播放运行时与后台播放宿主实现
- `:player`
  - Kotlin 播放器 API 与 JNI 桥接，驱动 Native C++ + FFmpeg
- `:cache-core`
  - Native-first 缓存核心，提供 Range 缓存与会话能力

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
  -> feature-artist-detail / feature-album-detail / feature-playlist-detail
  -> MusicDetailScaffold / DetailVerticalScrollHandoff
  -> DetailMiniPlayerBar
  -> PlayerActivity / PlaylistBottomSheet (:feature-player)
```

应用侧播放编排链路：

```text
MainActivity / PlayerActivity / BasePlaybackDetailActivity
  -> PlayerViewModel / HomeViewModel
  -> AppPlaybackGraph / PlayerRuntime (:app)
  -> playback-orchestrator
  -> PlayerServiceBridge (:playback-client)
  -> PlayerMediaSessionService (:playback-service)
  -> PlaybackProcessRuntime
  -> NativePlayer
  -> JNI / FFmpeg / AudioTrack
```

播放列表域链路：

```text
PlayerRuntime / PlayerViewModel / DetailPlaybackGateway
  -> PlaylistController (:playlist-core)
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
./gradlew :playlist-core:testDebugUnitTest
./gradlew :playback-orchestrator:testDebugUnitTest
./gradlew :feature-player:testDebugUnitTest
./gradlew :cache-core:testDebugUnitTest
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

OpenSpec 主 spec 校验：

```bash
PATH=/Users/wxy/.nvm/versions/node/v20.20.0/bin:$PATH openspec validate --specs
```

## 架构说明

- `app` 已从“播放器 UI + 播放域核心 + 宿主壳”收口为应用入口、Activity 宿主、装配与 composition root；当前仍保留 `PlayerViewModel`、`PlayerRuntime` 与 `AppPlaybackGraph` 这类应用侧宿主逻辑。
- `playlist-core` 承载播放列表域规则和持久化契约，不再把 `PlaylistController` 留在 `app/core/playlist`。
- `playback-orchestrator` 负责应用侧播放编排，不把队列同步、transport、settings 和 detail 播放协同继续堆在页面层。
- `feature-player` 负责播放器页的 presentation 层、入口 contract 与宿主 callbacks，不再把播放器 UI 主体留在 `app/feature/player/ui`。
- 歌单 / 专辑 / 艺人详情页已拆分到各自独立模块，共享的 detail shell 能力收敛到 `:feature-detail-support`。
- 播放服务实现细节收口在播放层，宿主通过 `:playback-client` / `:playback-contract` 接入；`playback-contract` 当前也承担播放域共享模型层，而不仅是轻量常量层。
- 共享 Android 构建约束由 `build-logic` 提供，模块脚本主要保留命名空间、依赖和 native 等差异配置。

## GitHub 发布

仓库已预埋基于 GitHub Actions 的 tag 发布流程：

- 推送符合 `v*` 规则的 tag，例如 `v0.1.0`
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
├── playlist-core/
├── playback-orchestrator/
├── feature-player/
├── feature-detail-support/
├── feature-playlist-detail/
├── feature-album-detail/
├── feature-artist-detail/
├── feature-search/
├── design-system/
├── network-core/
├── user/
├── playback-client/
├── playback-contract/
├── playback-service/
├── player/
├── cache-core/
├── scripts/
├── openspec/
└── README.md
```
