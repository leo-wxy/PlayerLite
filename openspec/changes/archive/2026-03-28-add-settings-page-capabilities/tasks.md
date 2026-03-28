## 1. 设置页入口与账户状态基建

- [x] 1.1 新增 `SettingsActivity`、设置页基础路由与返回关系，并从用户中心头部接入稳定的设置入口
- [x] 1.2 新增 `SettingsViewModel` 与设置页状态模型，拆分账户、缓存、音源三个 section 的 UI 状态
- [x] 1.3 让设置页账户 section 复用 `UserRepository.logout()` 与 `loginStateFlow`，覆盖登录态展示、退出登录确认和状态同步降级

## 2. 缓存统计与清理能力

- [x] 2.1 新增 `SettingsCacheRepository`，统计 `cache_core` 与 `lyrics` 的总占用和分项占用
- [x] 2.2 新增 `SettingsCacheController`，复用播放服务控制器下发缓存清理命令，并在命令成功下发后清理歌词缓存
- [x] 2.3 把缓存刷新、清理中、清理成功/失败反馈接入 `SettingsViewModel`，保证设置页可重复刷新和重试

## 3. 音源注册表与持久化

- [x] 3.1 新增 `ManagedAudioSource` 模型、音源本地存储实现和独立偏好命名空间
- [x] 3.2 新增 `AudioSourceRepository`，覆盖音源列表读取、追加保存、去重校验和 `http/https` 地址校验
- [x] 3.3 将音源列表加载、新增表单提交和应用重启恢复接入 `SettingsViewModel`

## 4. 设置页界面与用户中心收口

- [x] 4.1 基于 `AccountVisualStyle` 实现设置页 UI，按“账户”“缓存”“音源”三组承载设置内容与二级页返回 chrome
- [x] 4.2 在设置页中补齐游客态/登录态账户 section、缓存统计与清理反馈、音源列表空态与新增表单交互
- [x] 4.3 从用户中心主内容区移除内联退出登录按钮，保留现有快捷入口区，并让设置入口成为账户设置的统一入口

## 5. 回归验证

- [x] 5.1 为缓存统计/清理与音源注册表补充单元测试，覆盖分项统计、清理结果、地址校验、去重和持久化恢复
- [x] 5.2 为 `SettingsViewModel` 与设置页/用户中心 UI 补充状态测试，覆盖设置入口展示、退出登录同步、缓存清理反馈和新增音源后的列表刷新
- [x] 5.3 按仓库要求运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`
