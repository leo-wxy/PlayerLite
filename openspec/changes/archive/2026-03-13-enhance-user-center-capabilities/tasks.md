## 1. 用户中心数据与鉴权接入

- [x] 1.1 新增用户中心 remote data source、repository 与 mapper，接入收藏歌手、收藏专栏和用户歌单接口，并统一映射为用户中心内容卡片模型
- [x] 1.2 让用户中心受限接口复用现有 `JsonHttpClient` + `AuthHeaderProvider` 鉴权链路，确保请求自动携带当前会话中的 `Cookie` 且能识别会话失效
- [x] 1.3 在 `AppContainer` 中补齐用户中心 repository 的创建、缓存与对外访问入口，保持与现有在线能力相同的依赖装配方式

## 2. 用户中心状态与加载编排

- [x] 2.1 新增 `UserCenterViewModel`、`UserCenterTab` 与分 Tab 内容状态模型，覆盖默认 Tab、Tab 切换、首次加载、缓存复用和局部重试
- [x] 2.2 将用户中心内容状态与现有 `UserSessionUiState` 解耦，保证未登录时不发起受限请求、已登录时按需加载内容、会话失效时统一降级
- [x] 2.3 调整 `MainActivity` 的用户中心分支，同时接入头部会话状态和内容区状态，把登录、退出登录、Tab 切换和重试回调传给 `UserCenterScreen`

## 3. 用户中心页面与交互重构

- [x] 3.1 重构 `UserCenterScreen` 的已登录内容区，在现有头部资料区下方实现偏云音乐风格的 Tab 切换栏与单内容面板
- [x] 3.2 为收藏歌手、收藏专栏和用户歌单实现统一的卡片式内容展示，并保证切换 Tab 时页面主结构稳定、不叠加三段长列表
- [x] 3.3 补齐加载中、空内容、普通失败和未登录引导的页面分支，确保普通失败只影响当前 Tab 面板而不会破坏头部资料区

## 4. 回归验证

- [x] 4.1 为用户中心数据层补充单元测试，覆盖三类接口的成功映射、`Cookie` 透传和会话失效降级行为
- [x] 4.2 为 `UserCenterViewModel` 与用户中心页面补充状态/UI 测试，覆盖默认 Tab、Tab 切换缓存、空态、局部重试和未登录不请求受限内容
- [x] 4.3 按仓库要求运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`，并视 UI 变更情况补充 `./gradlew :app:compileDebugAndroidTestKotlin`
