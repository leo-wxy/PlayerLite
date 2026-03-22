## 1. 构建约束与环境配置收口

- [x] 1.1 新增 `build-logic` convention plugin，并在 `settings.gradle.kts` 中接入统一的 Android application / library / Compose 配置约束
- [x] 1.2 将现有模块脚本迁移到共享 convention，移除重复的 `compileSdk`、`minSdk`、Java 版本与通用测试配置
- [x] 1.3 为 `app` 增加统一环境配置入口（例如 `BuildConfig` + 配置对象），并移除 `AppContainer` 中硬编码的 API base URL

## 2. 播放服务边界收口

- [x] 2.1 在播放层定义统一的播放服务定位常量与解析策略，并更新 `playback-service` manifest 以提供稳定服务入口
- [x] 2.2 调整 `PlayerServiceBridge`，移除调用方传入具体 `serviceClass` 的要求，改为通过播放客户端边界解析服务组件
- [x] 2.3 更新 `app` 中播放器页、主壳与相关调用点，确保不再直接引用 `com.wxy.playerlite.playback.process.*` 实现类

## 3. 共享 detail 支撑与跨模块契约

- [x] 3.1 将 detail feature 迁移所需的共享播放队列模型迁出 `app` 内部包，提供 detail feature 可直接依赖的稳定模型边界
- [x] 3.2 将 `DetailPlaybackGateway` 契约迁出 `app`，并保留 `app` 中的运行时实现以接入现有 `PlayerRuntime`
- [x] 3.3 新增 `feature-detail-support` 模块，迁移共享 detail UI 支撑（scaffold、paging footer、scroll handoff、hero brush、状态卡等）

## 4. 详情 feature 模块化

- [x] 4.1 新增 `feature-playlist-detail` 模块，迁移歌单详情的 repository、mapper、UI state、ViewModel 与 screen composable，保留 `app` 中的薄 Activity 适配器
- [x] 4.2 新增 `feature-album-detail` 模块，迁移专辑详情的 repository、mapper、UI state、ViewModel 与 screen composable，保留 `app` 中的薄 Activity 适配器
- [x] 4.3 新增 `feature-artist-detail` 模块，迁移艺人详情的 repository、mapper、UI state、ViewModel 与 screen composable，保留 `app` 中的薄 Activity 适配器
- [x] 4.4 更新 detail 相关跨页面路由、`AppContainer` 装配与宿主调用点，并删除 `app` 中已迁移的实现副本

## 5. 壳层清理与结构回归

- [x] 5.1 清理 `app` 中已不应继续承载的 feature 实现代码，使 `app` 仅保留壳层、Activity 入口、宿主适配与 composition root
- [x] 5.2 对齐 `openspec` 相关文档与实现结果，确保模块边界、播放边界与环境配置治理落地一致

## 6. 验证与冒烟

- [x] 6.1 运行定向 Robolectric / 单测回归：`*HomeOverviewScreenRobolectricTest*`、`*UserCenterScreenRobolectricTest*`、`*PlaylistDetail*`、`*AlbumDetail*`、`*ArtistDetail*`、`*Player*`
- [x] 6.2 运行 `./gradlew :app:assembleDebug`，确认模块迁移、播放边界与 convention plugin 改造后构建通过
- [ ] 6.3 手动冒烟：首页 / 我的切换与 minibar、搜索进入各详情页、播放暂停切歌、打开播放列表、登录态和鉴权相关详情链路
