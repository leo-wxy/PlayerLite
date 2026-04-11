## 1. 模块与基础装配

- [x] 1.1 新增 `:feature-home` 模块并接入 `settings.gradle.kts`、`build-logic` 与基础依赖
- [x] 1.2 为 `:feature-home` 建立最小对外边界，包括首页 screen / route 入口、service factory 与 host dependencies 契约
- [x] 1.3 调整宿主装配代码，使 `app` 能通过 `:feature-home` 暴露的稳定入口获取首页依赖而不是直接构造内部实现

## 2. 首页实现迁移

- [x] 2.1 将首页页面实现、首页专属 composable、UI model 与 layout spec 迁入 `:feature-home`
- [x] 2.2 将 `HomeViewModel` 及其首页状态模型迁入 `:feature-home`
- [x] 2.3 将首页 repository、remote data source、JSON mapper 与首页发现数据解析实现迁入 `:feature-home`
- [x] 2.4 在 `:feature-home` 内定义首页自有动作模型与内容目标模型，替代对宿主内部路由类型的直接依赖

## 3. 宿主切换与边界收口

- [x] 3.1 更新 `MainActivity` 与首页 Tab 宿主 wiring，改为消费 `:feature-home` 暴露的 screen / callbacks
- [x] 3.2 更新 `PlayerLiteApplication`、`AppContainer` 或等价宿主层代码，通过 Home host dependencies / service factory 提供首页所需宿主能力
- [x] 3.3 删除 `app` 中已迁移的首页实现、内部 mapper 和相关重复类型，确保 `app` 不再保留首页核心实现副本

## 4. 验证与回归

- [x] 4.1 补齐并更新首页模块迁移后的单元测试或 Robolectric 测试，覆盖首页发现流、首页搜索入口、首页歌曲播放闭环与首页动作翻译
- [x] 4.2 运行 `./gradlew :playback-service:testDebugUnitTest`
- [x] 4.3 运行 `./gradlew :app:testDebugUnitTest`
- [x] 4.4 运行 `./gradlew :app:assembleDebug`
