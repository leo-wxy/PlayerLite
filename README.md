# PlayerLite

PlayerLite 是一个 Android 音频播放器，使用 Jetpack Compose、Media3、FFmpeg、JNI 和 native cache core。播放服务运行在独立的 `:playback` 进程，应用侧通过 `MediaController` 与服务通信。

## 功能

- 播放 `content://`、`file://`、`http://` 和 `https://` 音频
- 后台播放、系统通知、锁屏控制和外部 MediaSession 控制
- 播放列表持久化、拖拽排序、随机播放、列表循环和单曲循环
- 在线音源解析、音质切换、倍速与音效处理
- 歌词加载、高亮、自动滚动和跨页面歌词摘要
- HTTP Range 边播边缓存、完整缓存优先起播和缓存区间进度展示
- 当前歌曲 ahead 预读与下一首首段预热
- 首页发现、搜索、歌手/专辑/歌单/歌曲详情、登录和用户中心

## 模块

| 模块 | 职责 |
| --- | --- |
| `app` | Application、Activity 宿主、路由和依赖装配 |
| `feature-discovery` | 首页、搜索及对应状态与数据映射 |
| `feature-details` | 歌手、专辑、歌单和歌曲详情 |
| `feature-player` | 播放器与播放列表 UI |
| `design-system` | 共享主题和语义样式 |
| `core-data` | 网络、用户会话和播放列表领域逻辑 |
| `playback-api` | 跨进程播放协议、共享模型和客户端桥接 |
| `playback-service` | MediaSessionService、播放编排、在线音源与预热 |
| `player` | Kotlin 播放器 API、JNI 和 FFmpeg 解码输出 |
| `cache-core` | native Range 缓存、内存窗口和磁盘持久化 |
| `build-logic` | Android/Compose Gradle convention plugins |

核心播放链路：

```text
Compose UI
  -> playback-api
  -> MediaSessionService (:playback process)
  -> NativePlayer
  -> FFmpeg / AudioTrack

Online source
  -> RangeDataProvider
  -> cache-core
  -> NativePlayer
```

## 环境

- Android SDK 36
- NDK `27.0.12077973`
- Java 11
- Node.js 20+，仅 OpenSpec 工具需要

## 构建

初始化 FFmpeg submodule 并构建 Android 动态库：

```bash
git submodule update --init --recursive
bash scripts/build_ffmpeg_android.sh
```

安装 Debug 包：

```bash
./gradlew :app:installDebug
```

常用验证：

```bash
./gradlew :cache-core:testDebugUnitTest
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

本地 HTTP Range 调试：

```bash
python3 scripts/range_http_server.py --port 18080 --directory .
```

模拟器通过 `http://10.0.2.2:18080/<文件名>` 访问宿主机文件。

## 发布

推送 `vX.Y.Z` tag 会触发 GitHub Actions 构建 Release APK。工作流会从 tag 写入 `versionName` 和 `versionCode`。

```bash
git tag -a v0.2.0 -m "v0.2.0"
git push origin v0.2.0
```

OpenSpec 设计与历史变更位于 [`openspec/`](openspec/)，更详细的实现记录位于 [`docs/plans/`](docs/plans/)。
