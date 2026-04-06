## 1. 每日推荐数据与依赖装配

- [x] 1.1 新增每日推荐歌曲 remote data source、repository、mapper 与 song-centric UI model，基于 `/recommend/songs` 映射歌曲基础信息、推荐理由和播放所需字段，并复用 `requiresAuth = true` 的登录态请求语义
- [x] 1.2 在 `AppContainer` 中接入每日推荐歌曲 repository 及页面所需依赖，继续复用现有 `JsonHttpClient`、`UserRepository` 和 `DetailPlaybackGateway` 装配方式

## 2. 每日推荐歌曲页实现

- [x] 2.1 新增每日推荐歌曲 `ViewModel` 与状态模型，覆盖未登录引导、首次加载、空态、失败重试和会话失效回退未登录
- [x] 2.2 新增每日推荐歌曲 `Activity` / Compose Screen，承载标题栏、登录引导、加载/空/失败状态和歌曲列表展示，并展示封面、标题、歌手、专辑及推荐理由
- [x] 2.3 将每日推荐歌曲列表接入 `DetailPlaybackGateway`，支持单曲点击播放、播放全部和播放启动失败提示，并在成功替换队列后打开播放器

## 3. 首页每日推荐入口闭环

- [x] 3.1 扩展 `ContentEntryAction` 与宿主分发逻辑，新增站内“每日推荐歌曲页”内部 action，并补齐对应 `Intent` factory / `AndroidManifest.xml` 注册
- [x] 3.2 调整首页发现流 mapper，对首页“每日推荐”快捷入口优先映射为站内每日推荐歌曲页跳转，其他 `DRAGON_BALL` 资源继续保持现有通用映射语义
- [x] 3.3 在 `MainActivity` / 首页壳层接入新的内容动作处理，确保首页“每日推荐”快捷入口点击后稳定进入站内每日推荐歌曲页

## 4. 验证与回归

- [x] 4.1 为每日推荐歌曲 repository / mapper / `ViewModel` 补充单元测试，覆盖登录前置、成功映射、空结果、失败重试和会话失效降级
- [x] 4.2 为每日推荐歌曲页面和首页入口映射补充 UI 或状态验证，覆盖登录引导、列表展示、推荐理由呈现、播放触发和首页入口跳转分支
- [x] 4.3 运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug` 完成回归验证
