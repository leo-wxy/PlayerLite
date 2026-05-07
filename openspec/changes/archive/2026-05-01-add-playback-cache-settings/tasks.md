## 1. Artifact Status

- [x] proposal.md
- [x] design.md
- [x] specs/settings-page-playback-preferences/spec.md
- [x] specs/cache-management/spec.md
- [x] specs/background-playback-service/spec.md
- [x] specs/playlist-persistence/spec.md

## 2. Implementation

- [x] 2.1 新增播放/缓存策略偏好模型与持久化读写
- [x] 2.2 设置页播放与缓存分组展示四个策略开关
- [x] 2.3 SettingsViewModel 保存开关并同步可下发的播放进程配置
- [x] 2.4 播放客户端边界新增播放策略配置下发命令
- [x] 2.5 播放进程按弱网自动重试开关控制有限重试
- [x] 2.6 播放列表/会话恢复按启动恢复和断点续播开关执行
- [x] 2.7 补充设置页和播放服务定向测试
- [x] 2.8 补充 PlayerRuntime 启动恢复与断点续播交互定向测试
- [x] 2.9 补充弱网自动重试关闭后的服务侧行为与自定义命令解包测试

## 3. Verification

- [x] 3.1 `openspec validate add-playback-cache-settings --type change`
- [x] 3.2 `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest" --tests "*PlayerRuntimeInteractionTest"`
- [x] 3.3 `./gradlew :playback-service:testDebugUnitTest`
- [x] 3.4 `./gradlew :app:assembleDebug`
- [x] 3.5 `./gradlew :app:testDebugUnitTest`
- [x] 3.6 `git diff --check`
