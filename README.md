# PlayerLite

一个面向 Android 的音频播放器示例工程，基于 Compose + Media3 + FFmpeg + JNI + Native Cache Core 构建，当前已具备独立后台播放进程、系统媒体控制、本地/Content URI/HTTP(S) 音源播放、播放列表持久化恢复，以及网络音频缓存能力。

## 当前能力

- 本地音频选择与播放，支持系统文件选择器返回的 `content://` 音源
- 自动申请并持久化 `content://` 读权限，应用重启后仍可恢复可播列表
- 后台独立 `:playback` 进程播放，基于 `MediaSessionService` 接入系统媒体通知、锁屏控制与外部控制器
- 系统媒体卡片点击可回到 App
- Native FFmpeg 解封装/解码，Native `AudioTrack` 输出 PCM
- 播放控制：播放、暂停、继续、上一首、下一首、seek、播放倍速调整
- 倍速控制：支持 `0.5X` ~ `2.0X`、`0.1X` 步进，native 侧变速不变调
- 主控区入口：左侧倍速入口、右侧播放列表入口，统一图标化 UI 风格与 badge 展示
- 播放完成后自动切到下一首（若存在），最后一首播放完成后停止
- 播放列表管理：新增、删除、激活项切换、拖拽排序、Bottom Sheet 展示
- 播放列表持久化与启动恢复，不可读项会在恢复阶段校验并过滤
- 输出链路信息透传展示：输入/输出采样率、声道数、编码格式、是否发生重采样
- `http/https` 网络音源播放，支持 Range 请求、边播边缓存、seek 取消在途读取
- 网络缓存支持内存 + 磁盘复用，并提供清理缓存入口
- 内置 UI 测试流入口，便于验证本地 Range 服务与网络播放链路

## 模块划分

- `:app`
  - Compose UI、`PlayerViewModel`、播放页状态、播放列表与应用侧运行时编排
- `:playback-service`
  - 独立播放进程宿主，提供 `MediaSessionService`、`PlayerServiceBridge`、播放进程运行时与网络音源接入
- `:player`
  - 播放器内核，暴露 Kotlin API，并通过 JNI 驱动 Native C++ + FFmpeg 播放
- `:cache-core`
  - Native-first 缓存核心，提供 `CacheCore`、`CacheSession`、`RangeDataProvider` 等能力，当前用于 `http/https` 音源缓存

## 支持的数据源

- `content://`：来自系统文件选择器，依赖持久化读权限
- `file://`：可直接读取的本地文件
- `http://` / `https://`：通过 Range 请求驱动的网络音源，接入 `cache-core` 做缓存和 seek

说明：

- seek 是否可用取决于当前 Source 能力；部分顺序读取源可能不支持快速 seek
- 网络音源当前聚焦 `http/https`，尚未在 README 范围内承诺 HLS/RTSP 等协议能力

## 核心架构

App 控制链路：

```text
MainActivity
  -> PlayerViewModel
  -> PlayerRuntime
  -> PlayerServiceBridge
  -> PlayerMediaSessionService (:playback)
  -> PlaybackProcessRuntime
  -> TrackPreparationCoordinator
  -> NativePlayer
  -> JNI / FFmpeg
  -> AudioTrack
```

Source 与解码链路：

```text
IPlaysource / IDirectReadableSource
  -> JniPlaySource
  -> FfmpegPlayer
  -> FfmpegDecoder
  -> AudioTrackConsumer
  -> AudioTrack
```

网络缓存链路：

```text
OkHttpRangeDataProvider
  -> CachedNetworkSource
  -> CacheCore / CacheSession
  -> memory + disk cache
  -> FFmpeg playback pipeline
```

补充说明：

- App 侧通过 `PlayerServiceBridge` 与独立播放进程通信，避免 UI 进程直接持有底层播放生命周期
- `NativePlayer` 使用实例级 native context，避免多实例间状态污染
- JNI 读取链路优先尝试 `ByteBuffer` 直写，失败后自动回退到 `byte[]` 路径
- 播放状态、seek 能力、倍速、输出链路信息等会通过 `MediaSession` extras/metadata 与 `playbackParameters` 回传给 UI

## 目录概览

```text
player-lite/
├── app/
├── playback-service/
├── player/
├── cache-core/
├── third_party/FFmpeg-n6.1.4/
├── scripts/
├── openspec/
├── local-media-ui-test.mp3
└── README.md
```

关键路径：

- `app/src/main/java/com/wxy/playerlite/feature/player/`
- `app/src/main/java/com/wxy/playerlite/core/playlist/`
- `playback-service/src/main/java/com/wxy/playerlite/playback/`
- `playback-service/src/main/java/com/wxy/playerlite/playback/process/source/`
- `player/src/main/java/com/wxy/playerlite/player/`
- `player/src/main/cpp/`
- `cache-core/src/main/java/com/wxy/playerlite/cache/core/`
- `cache-core/src/main/cpp/`

## 构建环境

建议环境：

- Android Studio（AGP 9.1.x）
- Android SDK `36`
- NDK `27.0.12077973`
- Java `11`

初始化 FFmpeg submodule：

```bash
git submodule update --init --recursive
```

构建 FFmpeg Android 动态库：

```bash
bash scripts/build_ffmpeg_android.sh
```

常用验证命令：

```bash
./gradlew :cache-core:testDebugUnitTest
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

可选安装：

```bash
./gradlew :app:installDebug
```

## 运行与调试

### 1. 本地音频播放

- 启动 App 后点击选文件按钮
- 通过系统文件选择器选择音频文件（`audio/*`）
- 选中的音频会加入播放列表，并由 App 侧保存持久化读权限与列表状态
- 主控区左侧可调节倍速，右侧可打开播放列表
- 当播放列表中当前曲目自然播放完成且仍有下一首时，系统会自动切到下一首继续播放

### 2. 本地 HTTP Range 调试

仓库根目录已提供测试音频 `local-media-ui-test.mp3`，可配合脚本启动一个支持 Range 的本地服务：

```bash
python3 scripts/range_http_server.py --port 18080 --directory .
```

说明：

- Android 模拟器内通过 `http://10.0.2.2:18080/local-media-ui-test.mp3` 访问宿主机服务
- App 内的 UI 测试入口按钮会直接下发这个地址到后台播放进程
- 可用清理缓存按钮验证网络缓存清空后的重建流程

## 使用示例（播放器内核）

如果你只想直接使用 `:player` 模块能力，可按下面方式驱动本地文件播放：

```kotlin
val player: INativePlayer = NativePlayer()
val source = LocalFileSource(file)

source.setSourceMode(IPlaysource.SourceMode.NORMAL)
if (source.open() == IPlaysource.AudioSourceCode.ASC_SUCCESS) {
    val meta = player.loadAudioMetaDisplayFromSource(source)
    val result = player.playFromSource(source)
}

player.close()
source.close()
```

说明：

- 在完整 App 中，播放控制主路径通常走 `PlayerServiceBridge` -> `:playback` 进程，而不是 UI 直接调用 `NativePlayer`
- `loadAudioMetaDisplayFromSource()` 可用于读取 `codec / sampleRate / channels / bitRate / duration`

## 常见返回码（部分）

- `0`：成功
- `-2001`：已停止
- `-2003`：时长不可用
- `-2005`：同一播放器实例正在播放（防重入）
- `-2006`：当前状态不允许 seek
- `-3001`：`AudioTrack` 初始化失败
- `-4 / -5`：Source 初始化或打开失败
- `-6`：Native 上下文不可用

其余负值可能来自 FFmpeg、Source 或缓存/播放流程内部错误，建议结合 `lastError()` 与日志一起排查。

## 当前版本重点

- 已完成独立后台播放进程与 `MediaSession` 集成
- 已完成播放列表管理与持久化恢复
- 已引入 `:cache-core`，打通 `http/https` 网络播放缓存主链路
- 已支持清理缓存、自定义测试流入口与输出链路信息展示
- Native Source 读取链路已支持 direct buffer 优先与兼容回退

## 后续可演进方向

- 扩展更多网络协议与更完整的流媒体场景
- 丰富缓存可观测性与缓存命中分析工具
- 增强播放列表能力（循环、随机、批量操作）
- 完善 UI 自动化与真机网络回归测试覆盖
