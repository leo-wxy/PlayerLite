## 1. Artifact Status

- [x] proposal.md
- [x] design.md
- [x] specs/playback-prewarm/spec.md
- [x] specs/background-playback-service/spec.md
- [x] specs/cache-management/spec.md
- [x] specs/settings-page-playback-preferences/spec.md

## 2. Implementation

- [x] 2.1 新增在线播放预热偏好模型与持久化读写
- [x] 2.2 设置页播放与缓存分组展示预热开关和预热预算摘要
- [x] 2.3 SettingsViewModel 保存预热偏好并下发播放进程
- [x] 2.4 播放客户端边界新增预热策略配置下发命令
- [x] 2.5 播放进程新增预热调度器，支持当前项 ahead window，并在当前项缓存完成后启动下一首首段预热
- [x] 2.6 预热任务服从仅 Wi-Fi 自动缓存、移动网络只播放不缓存和缓存容量策略
- [x] 2.7 队列、音源、音质、播放目标变化时取消旧预热任务
- [x] 2.8 预热结果写入受管缓存并被后续播放准备链路复用
- [x] 2.9 新增预热状态快照回调，区分 Ready、Completed、Skipped、Failed、Canceled 等状态
- [x] 2.10 播放模式候选规则接入预热调度，覆盖顺序、列表循环、单曲循环和随机候选
- [x] 2.11 补充设置页、播放命令边界、当前项缓存完成门控、预热状态快照、播放模式候选和缓存预算定向测试

## 3. Verification

- [x] 3.1 `openspec validate add-playback-prewarm --type change`
- [x] 3.2 `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest"`
- [x] 3.3 `./gradlew :playback-service:testDebugUnitTest`
- [x] 3.4 `./gradlew :app:testDebugUnitTest`
- [x] 3.5 `./gradlew :app:assembleDebug`
- [x] 3.6 `git diff --check`
