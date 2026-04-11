## Context

当前仓库已经把搜索、歌手 / 专辑 / 歌单详情、播放列表域和部分播放编排能力拆到了独立模块，但首页能力仍然集中在 `app` 内部。具体表现为：

- `MainActivity` 同时持有 `HomeViewModel`、首页 route 回调和首页壳层集成逻辑。
- 首页页面、`HomeViewModel`、首页 repository、remote data source、JSON mapper、首页 UI model 和首页 layout spec 仍位于 `app/src/main/java/com/wxy/playerlite/feature/main/`。
- `AppContainer` 直接构造首页 repository 内部实现，而不是通过 feature 暴露的稳定工厂或宿主契约接入。
- 首页 mapper 当前还直接依赖 `ContentEntryAction`、`SearchRouteTarget` 等宿主或其他 feature 的动作模型，使首页能力难以作为真正独立的 feature 模块迁移。

这与现有 `app-shell-composition-root` 主 spec 的方向不一致：`app` 应该主要承担应用壳层、路由与依赖装配职责，而不是继续保存首页 feature 的核心实现。另一方面，`feature-search` 已经形成一套较可复用的样板：feature 自己承载 UI、状态与 repository 实现，宿主通过 host dependencies 与工厂接入。

本次设计目标是在不改变首页用户可见行为的前提下，把首页从 `app` 中抽离成独立 feature，并让 `app` 通过稳定入口完成接入。

## Goals / Non-Goals

**Goals:**

- 新增独立的 `:feature-home` 模块，承载首页页面实现、状态管理、repository、remote data source、mapper 和首页专属 UI model。
- 让 `app` 通过稳定入口接入首页能力，而不是继续直接依赖首页内部实现类。
- 将首页内部使用的内容跳转与播放动作抽象成 feature 自有的动作模型，消除对 `ContentEntryAction`、`SearchRouteTarget` 等宿主内部类型的直接依赖。
- 保持现有首页发现流、悬浮搜索框、横向歌曲推荐区块、每日推荐入口和播放闭环行为不变。

**Non-Goals:**

- 不在本次变更中顺带重构 `PlayerRuntime`、`AppPlaybackGraph` 与 `playback-orchestrator` 的边界。
- 不把用户中心页、自定义设置页或最近播放页一起迁入新的 Home feature。
- 不改动首页已有的用户可见交互、接口契约和视觉风格。
- 不在本次变更中引入新的重量级 DI 框架。

## Decisions

### Decision 1: 新增 `:feature-home`，而不是继续在 `app` 内按包整理

首页已经具备独立 feature 的完整要素：页面、状态、数据访问、JSON 映射、交互动作和宿主接入点。继续只在 `app` 里按包整理，无法真正约束依赖方向，也无法阻止后续把首页新逻辑继续堆回 `app`。

因此本次明确新增 `:feature-home` 模块，并将以下内容迁入该模块：

- `HomeOverviewScreen` 及首页专属 composable
- `HomeViewModel`
- `HomeDiscoveryRepository`、`DailyRecommendedSongsRepository` 之外的首页发现数据实现
- 首页 remote data source、JSON mapper、UI model 与 layout spec
- 首页 feature 自有动作模型与宿主依赖契约

保留在 `app` 的内容：

- `MainActivity` 主壳
- 双 Tab 宿主与全局 shell
- Activity 级导航和 route 适配
- composition root / 应用级依赖装配

备选方案是继续把首页留在 `app`，仅通过文件整理缓解复杂度。该方案成本更低，但不能改变 `app` 继续膨胀的趋势，因此不采用。

### Decision 2: 首页通过“feature 自有宿主契约 + service factory”接入，而不是让 `app` 直接构造内部实现

当前 `AppContainer` 直接构造 `DefaultHomeDiscoveryRepository`、`NeteaseHomeDiscoveryRemoteDataSource` 等首页内部实现。这会让宿主与 feature 内部类强耦合，不符合 `app-shell-composition-root` 要求。

本次采用与 `feature-search` 相同方向的接入模式：

- `:feature-home` 暴露 `HomeFeatureServiceFactory`
  用于基于 `JsonHttpClient` 创建 `HomeDiscoveryRepository` 等首页对外服务。
- `:feature-home` 暴露 `HomeHostDependencies` / `HomeHostDependenciesProvider`
  由宿主提供首页运行所需的稳定依赖，如 repository 或 route handler。
- `app` 只依赖上述工厂与契约，而不直接引用首页内部的 remote data source、mapper 或实现类。

这样做的原因是：

- 可以保持当前无 DI 框架的轻量装配方式；
- 与 `feature-search` 已有模式一致，迁移成本低；
- 后续若要继续把用户中心或其他壳层 feature 抽离，能复用同一种宿主接入模式。

备选方案是直接在 `app` 中手写 `HomeViewModelFactory` 并继续 new 首页实现类。该方案虽然可行，但只把耦合点从页面挪到了工厂，不解决 feature 边界问题，因此不采用。

### Decision 3: 首页 feature 使用宿主中立的动作模型，不直接依赖 `ContentEntryAction` 或 `SearchRouteTarget`

当前首页 mapper 直接组装 `ContentEntryAction`，并依赖 `SearchRouteTarget` 作为部分路由目标。这会导致首页一旦迁出 `app`，就反向依赖宿主或其他 feature。

本次改为在 `:feature-home` 内定义首页自有动作模型，例如：

- `HomeAction.OpenContent(HomeContentTarget)`
- `HomeAction.ReplaceQueueAndOpenPlayer`
- `HomeAction.InsertNext`

其中 `HomeContentTarget` 只描述首页需要的稳定目标语义，如：

- `Artist(id)`
- `Album(id)`
- `Playlist(id)`
- `DailyRecommendedSongs`
- `ExternalUri(uri)`
- `Unsupported`

宿主 `app` 负责将 `HomeContentTarget` 翻译成实际 Activity 跳转或外部链接打开行为。

这样做的好处是：

- `:feature-home` 不再依赖 `app` 内部 route 类型；
- 首页 mapper 能在 feature 内闭合；
- 后续如果把首页路由进一步抽成共享 contract，也不会再反向污染 feature-home。

备选方案是把现有 `ContentEntryAction` 直接挪到一个共享模块。本次不采用，因为这会把首页抽离扩展成更大的“全站路由 contract 重构”，超出本次范围。

### Decision 4: 首页页面保留在主壳内渲染，但由 `:feature-home` 提供稳定 screen / state 边界

首页不是独立 Activity，而是主壳双 Tab 的一个页面。因此本次不把首页改成独立 Activity，也不改变主壳结构。迁移后仍然由 `MainActivity` 承载首页 Tab，但首页内容本身来自 `:feature-home`：

- `MainActivity` 保留 shell state、Tab 切换与顶层 host callbacks
- `:feature-home` 提供 `HomeScreen` / `HomeRoute` 与 `HomeViewModel`
- 首页页面只接收宿主提供的必要 callback，如：
  - 打开搜索页
  - 处理首页内容跳转
  - 处理播放相关动作

这样可以把首页从 `app` 中抽离，同时不引入新的导航层复杂度。

备选方案是把首页改为独立 Activity 或独立导航 graph。该方案会对现有双 Tab 壳层造成不必要扰动，因此不采用。

### Decision 5: 本次只抽首页发现能力，不把用户中心或每日推荐详情页一起打包迁移

当前 `feature/main` 目录同时包含首页、用户中心、设置入口和若干壳层 UI。虽然长期看可以进一步拆开，但本次先只处理首页：

- 首页发现流相关页面与数据实现迁入 `:feature-home`
- 用户中心与其子页面保持现状
- 每日推荐歌曲详情页暂不要求一起迁入 `:feature-home`

原因是首页已经足够形成独立 feature，且它是当前 `app` 中体量最大、与主 spec 偏差最明显的部分。若把用户中心和首页同时迁移，改动面会显著扩大，影响这次重构的可控性。

## Risks / Trade-offs

- [Risk] 首页动作模型从宿主类型切到 feature 自有类型后，宿主翻译层可能出现映射遗漏  
  → Mitigation：先为首页现有全部动作类型建立一一对应的 host translation 表，并补齐 repository / route 层测试。

- [Risk] 迁移后 `MainActivity`、`AppContainer` 和首页模块可能短期同时存在新旧接法，造成过渡期复杂度上升  
  → Mitigation：按“模块创建 → 工厂接入 → 页面切换 → 删除旧实现”顺序小步迁移，避免长期双轨。

- [Risk] 首页模块如果直接依赖过多现有 app 内部类型，会使新模块名义独立、实际仍强耦合  
  → Mitigation：本次把 route target、首页动作模型和宿主依赖契约一起抽象出来，先切断最强的反向依赖。

- [Risk] 仅抽首页、不抽用户中心，会让 `feature/main` 在一段时间内处于半拆分状态  
  → Mitigation：接受短期过渡态，并在文档中明确本次只处理首页，避免顺手扩大范围。

- [Risk] 新增模块会增加 Gradle 配置和依赖维护成本  
  → Mitigation：复用现有 `build-logic`、`design-system` 和 `feature-search` 的模块模板，保持新增模块最小化。

## Migration Plan

1. 新增 `:feature-home` 模块并接入 Gradle 配置
   - 增加 `settings.gradle.kts` include
   - 建立模块基础依赖：`design-system`、`network-core`、`playlist-core`、`feature-player` 等

2. 在 `:feature-home` 内建立稳定边界
   - 迁入首页 screen、ViewModel、repository、remote data source、mapper、UI model 与 layout spec
   - 新增 `HomeFeatureServiceFactory`
   - 新增 `HomeHostDependencies` / `HomeHostDependenciesProvider`
   - 定义首页自有动作模型和内容目标模型

3. 宿主切换到新的 feature 接入方式
   - `AppContainer` 改为通过 `HomeFeatureServiceFactory` 创建首页 repository
   - `PlayerLiteApplication` 或宿主层提供 `HomeHostDependencies`
   - `MainActivity` 改为使用 `:feature-home` 暴露的 screen / ViewModel / callbacks

4. 删除 `app` 中旧首页实现并校验行为一致性
   - 删除旧首页 repository、mapper、screen 和相关内部类型
   - 运行首页、搜索入口、首页歌曲播放闭环、每日推荐入口相关验证

5. 回滚策略
   - 若迁移中发现宿主接入或首页路由翻译存在高风险问题，可先保留 `:feature-home` 模块但不切换主壳调用入口
   - 优先回滚接入层改动，而不是回滚整个模块创建

## Open Questions

- 每日推荐歌曲详情页是否应在下一条变更中并入 `:feature-home`，还是保持为独立页面能力继续留在 `app`？
- 首页内容跳转目标长期是否需要抽到更通用的 shared route contract，以复用给用户中心或其他发现流页面？
- 用户中心是否应作为下一阶段独立拆分对象，形成与首页对称的 `:feature-user-center`？
