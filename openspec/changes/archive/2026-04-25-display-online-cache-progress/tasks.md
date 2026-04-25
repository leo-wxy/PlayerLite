## 1. Artifact Status

- [x] proposal.md
- [x] design.md
- [x] specs/online-cache-progress-visualization/spec.md
- [x] specs/playback-state-authority/spec.md

## 2. Implementation

- [x] 2.1 在 `playback-contract` / `playback-client` 中新增当前播放项缓存进度 extras、snapshot 字段与 mapper
- [x] 2.2 在 `playback-service` 中基于当前 resource key / cache snapshot 上报当前项缓存进度，并处理完整缓存与估算比例
- [x] 2.3 在 `app` 的 `PlayerRuntime` / `PlayerUiState` 中投影当前项缓存进度展示字段
- [x] 2.4 更新 minibar 进度条绘制，让已缓存未播放部分以低透明度同色显示
- [x] 2.5 更新播放页进度条绘制，让缓存比例直接画在同一进度条上，并在完整缓存时拉满
- [x] 2.6 为 extras 读写、snapshot mapper、runtime 投影和 UI 进度条补定向测试
- [x] 2.7 将缓存进度从单点比例补充为基于 offset / length 的 range 语义，支持 seek 后从新读取位置展示缓存段
- [x] 2.8 调大在线读取预读窗口与默认内存缓存预算，让缓存进度明显领先播放进度而不是贴着播放点移动

## 3. Verification

- [x] 3.1 `./gradlew :playback-service:testDebugUnitTest`
- [x] 3.2 `./gradlew :app:testDebugUnitTest`
- [x] 3.3 `./gradlew :app:assembleDebug`
- [x] 3.4 `./gradlew :cache-core:testDebugUnitTest :app:assembleDebug`
