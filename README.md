# PlayerLite

一个基于 Android + FFmpeg + JNI 的轻量播放器示例工程。

当前工程已经拆分为两层：

- `app`：应用壳层（UI、文件选择、交互逻辑）
- `player`：播放器内核模块（Kotlin API + Native C++ + FFmpeg）

## 功能特性

- 本地音频文件选择与播放（`audio/*`）
- Native 解码 + Native `AudioTrack` 输出
- 播放控制：`play / pause / resume / stop / seek`
- 进度回调与播放状态查询
- 音频元信息读取：`codec / sampleRate / channels / bitRate / duration`
- Source 驱动架构（业务 Source -> JNI 桥接 -> Native 播放）

## 工程结构

```text
player-lite/
├── app/                         # UI 与业务入口
│   └── src/main/java/com/wxy/playerlite/MainActivity.kt
├── player/                      # 播放器模块
│   ├── src/main/java/com/wxy/playerlite/player/
│   │   ├── INativePlayer.kt
│   │   ├── NativePlayer.kt
│   │   └── source/
│   │       ├── IPlaysource.kt
│   │       ├── IDirectReadableSource.kt
│   │       └── LocalFileSource.kt
│   ├── src/main/cpp/
│   │   ├── player.cpp
│   │   ├── ffmpeg_player.*
│   │   ├── ffmpeg_decoder.*
│   │   ├── jni_play_source.*
│   │   └── CMakeLists.txt
├── third_party/
│   └── FFmpeg-n6.1.4/          # FFmpeg submodule
│       ├── out/<abi>/include
│       └── out-jniLibs/<abi>/libffmpeg.so
├── scripts/
│   └── build_ffmpeg_android.sh
└── settings.gradle.kts          # include(:app, :player)
```

## 架构说明

播放链路（简化）：

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

- 解码器 `FfmpegDecoder` 已与 Source 解耦，只处理 `AVFormatContext`。
- Source 绑定（自定义 AVIO）由 `FfmpegPlayer` 负责。
- `NativePlayer` 使用实例级 `PlayerContext`，避免多实例之间互相污染。
- 读取路径支持双通道：
  - 基线兼容：`IPlaysource.read(ByteArray, size)`
  - Android 优化：`IDirectReadableSource.readDirect(ByteBuffer, size)`（JNI 优先直通，失败自动回退）

## 构建

环境建议：

- Android Studio（可正常同步 AGP 9.x）
- Android SDK `36`
- NDK `27.0.12077973`

初始化 FFmpeg（submodule 方式）：

```bash
git submodule update --init --recursive
```

构建 FFmpeg Android 动态库（脚本来源于 `/Users/wxy/Downloads/FFmpeg-n6.1.4/build.sh`）：

```bash
bash scripts/build_ffmpeg_android.sh
```

命令：

```bash
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

// 生命周期结束
player.close()
source.close()
```

## 常见返回码（部分）

- `0`：成功
- `-2001`：已停止
- `-2003`：时长不可用
- `-2005`：同一播放器实例正在播放（防重入）
- `-3001`：`AudioTrack` 初始化失败
- `-4 / -5`：Source 初始化或打开失败
- `-6`：Native 上下文不可用

其余负值可能来自 FFmpeg 或内部流程错误，建议配合 `lastError()` 查看。

## 扩展建议

- 播放列表：优先在 `app` 层实现（按队列调度 `playFromSource`）
- 在线流 Source：实现 `IPlaysource`，如需性能再额外实现 `IDirectReadableSource`
- Buffer-fill 架构：可在 `player` 模块内加入 ring buffer + 解码/渲染双线程
