# PlayerLite

一个面向 Android 的音频播放器示例工程，基于 Compose、Media3、FFmpeg、JNI 和 Native Cache Core 构建。项目当前已经覆盖播放器主链路、首页发现、搜索、歌手/歌单/专辑详情、登录与用户中心等核心能力，并补齐了播放页歌词、首页 minibar 歌词摘要以及系统 `MediaSession` 动态展示链路。

## 主要能力

- 本地音频播放，支持 `content://`、`file://`、`http://`、`https://`
- 独立 `:playback` 进程后台播放，接入 `MediaSessionService`、系统通知、锁屏控制和外部控制器
- 播放列表管理与持久化恢复，支持删除、激活切换、拖拽排序
- 播放模式：列表循环、单曲循环、随机播放
- 网络音源 Range 播放、边播边缓存、缓存清理
- 播放展开页支持“歌曲 / 歌词”双页面切换、当前歌词摘要、完整歌词自动滚动与高亮
- 首页 `minibar` 与系统 `MediaSession` 可随播放进度动态展示当前歌词摘要，并稳定回退为 `歌名 - 歌手`
- 首页发现流、悬浮搜索入口、搜索结果页
- 歌手详情、歌单详情、专辑详情，以及首页/搜索/个人中心到详情页的跳转闭环
- 手机号/邮箱登录、用户会话恢复、个人中心基础承载

## 模块划分

- `:app`
  - Compose UI、`MainActivity` 主壳、首页/搜索/详情页、用户中心、播放列表业务状态
- `:network-core`
  - `OkHttp + kotlinx.serialization` 网络基础设施、统一错误映射
- `:user`
  - 登录、用户资料、会话持久化与恢复
- `:playback-client`
  - 前台到播放服务的 `MediaController` 桥接
- `:playback-contract`
  - 跨模块共享的播放协议、DTO、Session command
- `:playback-service`
  - 独立播放进程、`MediaSessionService`、播放运行时
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

播放器主链路：

```text
MainActivity
  -> PlayerViewModel / HomeViewModel
  -> PlayerRuntime
  -> PlayerServiceBridge
  -> PlayerMediaSessionService (:playback)
  -> PlaybackProcessRuntime
  -> NativePlayer
  -> JNI / FFmpeg / AudioTrack
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
./gradlew :cache-core:testDebugUnitTest
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

OpenSpec 主 spec 校验：

```bash
PATH=/Users/wxy/.nvm/versions/node/v20.20.0/bin:$PATH openspec validate --specs
```

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

## 目录概览

```text
player-lite/
├── app/
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
