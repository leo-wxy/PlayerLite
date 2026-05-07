## Context

设置页当前已有 `SettingsPlaybackPreferencesRepository` 保存默认音质、歌曲缓存上限和当前音源配置，并通过 `SettingsPlaybackController` 下发到后台播放服务。播放侧已经存在播放会话持久化、启动恢复、有限重试和缓存上限重配置能力，本次优先把这些隐式行为变成可配置开关。

## Goals / Non-Goals

**Goals:**
- 在设置页提供四个稳定开关：启动后恢复上次播放、断点续播、仅 Wi-Fi 自动缓存、弱网自动重试。
- 复用现有播放偏好存储，不新增数据库或迁移。
- 对已有播放行为最小侵入：能立即控制的行为立即接入，不能安全接入的行为先落偏好。
- 保持设置页分组列表风格，不引入复杂二级页面。
- 补充 ViewModel / Compose 定向测试。

**Non-Goals:**
- 不实现完整预热能力。
- 不实现移动网络弹窗拦截。
- 不改变手动播放时的边播边缓存基础链路。
- 不新增播放模式、歌词、隐私清理等其他设置。

## Decisions

### 决策一：新增一个播放策略偏好模型

使用 `PlaybackBehaviorPreferences` 聚合四个布尔开关，避免把多个 boolean 零散散落在 UI、repository 和 controller 中。设置页 UI 消费该模型，ViewModel 提供单个字段更新入口，repository 负责读写。

### 决策二：恢复设置分成“恢复队列”和“恢复位置”

“启动后恢复上次播放”控制是否应用已持久化的上次播放上下文；“断点续播”控制是否从上次位置恢复。这样用户可以保留队列但从 0 开始，也可以完全关闭启动恢复。

### 决策三：弱网自动重试下发到播放进程

播放进程已有有限重试计数和状态发布。新增偏好后，播放进程在 `shouldRetryPlayback` 中读取当前开关；关闭时直接进入失败态，不再自动重试。

### 决策四：仅 Wi-Fi 自动缓存先作为自动缓存策略偏好

当前边播边缓存是用户主动播放链路的一部分，强行按网络类型禁用会影响在线播放稳定性。该开关首版只约束后续自动缓存/预热任务，不阻断当前手动播放的读取缓存链路。

## UI Structure

```text
SettingsPlaybackPreferencesSection
  -> 默认音质
  -> 歌曲缓存上限
  -> 调整缓存上限
  -> 播放策略
       -> 启动后恢复上次播放 Switch
       -> 断点续播 Switch
       -> 仅 Wi-Fi 自动缓存 Switch
       -> 弱网自动重试 Switch
```

## Data Flow

```text
SettingsActivity
  -> SettingsViewModel.updatePlaybackBehaviorPreference(...)
  -> SettingsPlaybackPreferencesRepository.writePlaybackBehaviorPreferences(...)
  -> SettingsPlaybackController.setPlaybackBehaviorPreferences(...)
  -> PlayerServiceController custom command
  -> PlaybackProcessRuntime.setPlaybackBehaviorPreferences(...)
```

## Verification

- `openspec validate add-playback-cache-settings --type change`
- `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest"`
- `./gradlew :playback-service:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
