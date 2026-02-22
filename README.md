# PlayerLite

一个基于 Android + FFmpeg + JNI 的轻量播放器示例工程，当前已实现播放列表、持久化恢复与现代 Compose UI 播控。

## 模块划分

- `app`：应用层（Compose UI、ViewModel、播放列表业务）
- `player`：播放器内核（Kotlin API + Native C++ + FFmpeg）

## 功能特性

- 本地音频文件选择与播放（`audio/*`）
- Native 解码 + Native `AudioTrack` 输出
- 播放控制：`上一首 / 播放 / 暂停 / 继续 / 下一首 / seek`
- 播放完成后自动切换下一首（若存在）
- 播放列表管理：新增、删除、激活项切换、长按拖拽排序
- 播放列表半浮层（Bottom Sheet）与右下角快捷入口
- 播放列表持久化（`playlist_state_v1`）与启动恢复
- 持久化数据校验与安全回退（损坏/版本不兼容时回退空列表）
- 音频元信息读取：`codec / sampleRate / channels / bitRate / duration`
- 进度回调与播放状态查询
- Source 驱动架构（业务 Source -> JNI 桥接 -> Native 播放）

## 工程结构

```text
player-lite/
├── app/
│   └── src/main/java/com/wxy/playerlite/
│       ├── MainActivity.kt
│       ├── feature/player/
│       │   ├── PlaybackState.kt
│       │   ├── PlayerUiState.kt
│       │   ├── PlayerViewModel.kt
│       │   └── ui/
│       │       ├── PlayerScreen.kt
│       │       └── components/
│       │           ├── PlaybackControls.kt
│       │           └── PlaylistSheet.kt
│       └── playlist/
│           ├── PlaylistController.kt
│           ├── PlaylistModels.kt
│           └── PlaylistStorage.kt
├── player/
│   ├── src/main/java/com/wxy/playerlite/player/
│   │   ├── INativePlayer.kt
│   │   ├── NativePlayer.kt
│   │   └── source/
│   │       ├── IPlaysource.kt
│   │       ├── IDirectReadableSource.kt
│   │       └── LocalFileSource.kt
│   └── src/main/cpp/
│       ├── player.cpp
│       ├── ffmpeg_player.*
│       ├── ffmpeg_decoder.*
│       ├── jni_play_source.*
│       └── CMakeLists.txt
├── app/src/test/java/com/wxy/playerlite/playlist/
│   └── PlaylistControllerTest.kt
├── openspec/
│   └── specs/
│       ├── playlist-management/spec.md
│       └── playlist-persistence/spec.md
├── third_party/
│   └── FFmpeg-n6.1.4/
└── scripts/
    └── build_ffmpeg_android.sh
```

## 架构说明

UI 与业务主链路（简化）：

```text
MainActivity
  -> PlayerViewModel (StateFlow<PlayerUiState>)
  -> PlaylistController (列表状态/持久化)
  -> NativePlayer
  -> JNI / FFmpeg
  -> AudioTrack
```

Native 播放链路（简化）：

```text
IPlaysource (Kotlin)
  -> JniPlaySource (JNI bridge)
  -> IPlaySource (C++)
  -> FfmpegPlayer (C++)
  -> FfmpegDecoder (C++)
  -> AudioTrackConsumer (C++, via JNI)
  -> AudioTrack
```

说明：

- `PlayerViewModel` 负责 UI 状态、播放流程与播放列表协同。
- `PlaylistController` 使用版本化 JSON 进行防抖持久化与恢复校验。
- `NativePlayer` 使用实例级 `PlayerContext`，避免多实例互相污染。
- 读取路径支持双通道：
  - 基线兼容：`IPlaysource.read(ByteArray, size)`
  - Android 优化：`IDirectReadableSource.readDirect(ByteBuffer, size)`（JNI 优先直通，失败自动回退）

## 构建

环境建议：

- Android Studio（AGP 9.x）
- Android SDK `36`
- NDK `27.0.12077973`

初始化 FFmpeg（submodule）：

```bash
git submodule update --init --recursive
```

构建 FFmpeg Android 动态库：

```bash
bash scripts/build_ffmpeg_android.sh
```

构建与测试：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

可选安装：

```bash
./gradlew :app:installDebug
```

## 使用示例（Kotlin）

```kotlin
val player: INativePlayer = NativePlayer()
val source = LocalFileSource(file)

source.setSourceMode(IPlaysource.SourceMode.NORMAL)
if (source.open() == IPlaysource.AudioSourceCode.ASC_SUCCESS) {
    val meta = player.loadAudioMetaDisplayFromSource(source)
    val code = player.playFromSource(source)
}

player.close()
source.close()
```

## 常见返回码（部分）

- `0`：成功
- `-2001`：已停止
- `-2003`：时长不可用
- `-2005`：同一播放器实例正在播放（防重入）
- `-2006`：当前状态不允许 seek
- `-3001`：`AudioTrack` 初始化失败
- `-4 / -5`：Source 初始化或打开失败
- `-6`：Native 上下文不可用

其余负值可能来自 FFmpeg 或内部流程错误，建议配合 `lastError()` 排查。

## 后续可优化方向

- 播放列表拖拽交互继续打磨（边缘自动滚动、回弹、触觉反馈）
- 播放列表能力扩展（循环模式、随机模式、批量操作）
- 在线流 Source（HTTP/HLS）与本地缓存策略
- `player` 模块引入更细粒度解码/渲染监控指标
