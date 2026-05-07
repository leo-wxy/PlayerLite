## 1. Artifact Status

- [x] proposal.md
- [x] design.md
- [x] specs/settings-page-playback-preferences/spec.md
- [x] specs/cache-management/spec.md
- [x] specs/background-playback-service/spec.md

## 2. Implementation

- [x] 2.1 新增缓存策略偏好模型与持久化读写
- [x] 2.2 设置页播放与缓存分组展示移动网络只播放不缓存、缓存失败时提示、清理策略说明
- [x] 2.3 SettingsViewModel 保存缓存策略并下发播放进程
- [x] 2.4 播放客户端边界新增缓存策略配置下发命令
- [x] 2.5 播放进程持久化缓存策略并按移动网络策略禁用磁盘写入
- [x] 2.6 播放进程按缓存失败提示开关发布或静默缓存失败反馈
- [x] 2.7 补充设置页、播放边界和播放服务定向测试
- [x] 2.8 补充缓存失败回调与移动网络禁写盘 source 选择定向测试
- [x] 2.9 补充播放服务自定义命令解包与缓存读取失败回调测试

## 3. Verification

- [x] 3.1 `openspec validate add-cache-policy-settings --type change`
- [x] 3.2 `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest"`
- [x] 3.3 `./gradlew :playback-service:testDebugUnitTest`
- [x] 3.4 `./gradlew :app:testDebugUnitTest`
- [x] 3.5 `./gradlew :app:assembleDebug`
- [x] 3.6 `git diff --check`
- [x] 3.7 `./gradlew :playback-client:testDebugUnitTest`
- [x] 3.8 `./gradlew :playback-orchestrator:testDebugUnitTest`
