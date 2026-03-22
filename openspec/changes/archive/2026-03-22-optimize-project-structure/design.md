## Context

- 仓库已经拆出 `network-core`、`user`、`playback-client`、`playback-contract`、`playback-service`、`feature-search` 与 `design-system` 等模块，但 `app` 仍承载大量业务实现与装配逻辑。
- 当前 `app` 同时承担：
  - 壳层入口与导航
  - 依赖装配（`AppContainer`）
  - 多个 detail feature 的页面、ViewModel、repository、remote data source 与 mapper
  - 播放层部分实现细节引用
- 关键现状：
  - `AppContainer` 直接 new 大量 feature 实现并硬编码 `API_BASE_URL`
  - `PlayerViewModel` 通过 `PlayerMediaSessionService::class.java` 直接感知播放服务实现
  - 歌单、专辑、艺人详情大量复用同一套 detail chrome，但共享代码仍留在 `app/feature/detail`
  - 多个模块重复声明 Android build 约束，运行时环境配置也没有统一来源
- 约束：
  - 本次变更以结构优化为主，不改变既有业务行为
  - 不引入 Hilt/Koin 等完整 DI 框架
  - 不重写 `player` / `cache-core` / native 播放内核
  - 详情页仍需要复用现有播放 chrome，因此不能把整个详情播放链路一次性迁出 `app`

## Goals / Non-Goals

**Goals:**

- 将 `app` 收口为应用壳层、Activity 入口与 composition root，而不是继续承载完整 feature 实现。
- 移除 `app` 对播放服务实现类的直接依赖，让播放连接通过稳定客户端边界完成。
- 将歌单、专辑、艺人详情能力整理为一致结构，并把可共享的 detail UI 支撑从 `app` 中抽离。
- 将共享 Android 构建约束与运行时环境配置集中治理，降低脚本重复和环境切换成本。

**Non-Goals:**

- 不改变首页、我的、搜索、播放器页与详情页的用户可见行为语义。
- 不替换现有 `PlayerRuntime`、`PlaylistController` 或后台播放服务的核心算法实现。
- 不在本次变更中把所有 feature 全部迁出 `app`，仅覆盖已经明确暴露结构问题的能力。
- 不引入需要全仓改造的 DI 框架或路由框架。

## Decisions

### 1) `app` 保留壳层与 Activity 适配器，逐步移除完整 feature 实现

**Decision:** `app` 继续持有 `Application`、主壳导航、Activity 入口、composition root 以及与播放器页强绑定的宿主适配代码；歌单、专辑、艺人详情在迁移后由 `app` 保留薄 Activity 适配器，而不再保留完整 feature 实现。

**Rationale:**

- 详情页当前仍通过 `BasePlaybackDetailActivity` 与 `PlayerViewModel`、播放列表 sheet、detail minibar 绑定；完全迁出整条 UI 宿主链路会扩大风险。
- 保留薄 Activity 适配器可以把 `app` 限定为宿主层，同时允许 feature 的状态、数据与主体 UI 迁出。

**Alternatives considered:**

- 一次性把详情 Activity、播放器壳层与 detail chrome 全部迁出 `app`：理论上更彻底，但会把播放器壳层和结构优化绑成一次高风险重构，不适合本次 change。

### 2) 引入 `feature-detail-support` 模块承载共享 detail UI 支撑

**Decision:** 新增 `feature-detail-support` 模块，迁出当前 `app/feature/detail` 中不依赖 `PlayerViewModel` 的共享 detail UI 支撑，包括 `MusicDetailScaffold`、分页 footer、scroll handoff、dynamic hero brush、状态栏判定与通用详情状态卡；`BasePlaybackDetailActivity` 和直接绑定播放器页的宿主 chrome 暂留在 `app`。

**Rationale:**

- 歌单、专辑、艺人详情已经共享同一套 detail UI 能力，但这些公共能力仍留在 `app`，导致新 detail feature 无法直接依赖。
- 将 UI 支撑先抽为独立模块，可以在不触碰播放器宿主真相源的前提下，为后续 detail feature 模块化建立公共基础。

**Alternatives considered:**

- 继续将共享 detail UI 留在 `app`：实现最省，但无法真正支持 detail feature 脱离 `app`。
- 将 `BasePlaybackDetailActivity` 一并迁出：会立刻引入对 `PlayerViewModel` 与播放列表 UI 的跨模块抽取，超出本次安全边界。

### 3) 详情 feature 迁为独立模块，但 `app` 保留薄宿主入口

**Decision:** 新增 `feature-playlist-detail`、`feature-album-detail`、`feature-artist-detail` 三个模块。每个模块承载自身的 repository、remote data source、mapper、UI state、ViewModel 与主体 screen composable；`app` 保留对应 Activity 作为薄宿主入口，负责：

- 读取 intent 参数
- 提供 `AppContainer` 依赖
- 提供 detail 播放 gateway 实现
- 通过 `BasePlaybackDetailActivity` 挂接播放器 chrome

**Rationale:**

- 这是在不整体搬走播放器壳层的前提下，让 `app` 停止继续承载详情能力完整实现的最小可行路径。
- 详情 Activity 保留在 `app` 后，搜索、用户中心与其他入口仍可继续通过现有 intent 打开详情页，避免同时重做全部路由。

**Alternatives considered:**

- 只迁 repository / mapper，不迁 UI state 与 ViewModel：会让 `app` 继续持有 feature 主体实现，收益有限。
- 直接把整个详情 Activity 迁模块：会被 `BasePlaybackDetailActivity`、`PlayerViewModel`、播放器页入口和播放 sheet 反向耦合阻塞。

### 4) 共享播放队列模型与详情播放 gateway 契约外置

**Decision:** 将 detail feature 依赖的共享播放队列模型与 `DetailPlaybackGateway` 契约外置到 `playback-contract` / `playback-client` 等稳定边界中，使 detail feature 模块不再依赖 `app` 内部模型。

**Rationale:**

- 当前歌单、专辑、艺人详情 ViewModel 直接依赖 `app/core/playlist/PlaylistItem` 与 `app/feature/player/runtime/DetailPlaybackGateway`，这是 detail feature 无法迁模块的主要结构阻塞。
- 先外置共享契约，再由 `app` 提供运行时实现，可以保持播放行为不变，同时打通模块边界。

**Alternatives considered:**

- 保留这些模型与 gateway 在 `app`，只迁 UI 文件：detail feature 仍会被 `app` 内部类型反向锁死，无法建立稳定边界。

### 5) `PlayerServiceBridge` 按 manifest action 解析播放服务，不再由 `app` 传实现类

**Decision:** 调整 `playback-client` 的 `PlayerServiceBridge`，不再要求调用方传入具体 `serviceClass`，而是通过统一的播放服务 action / component 解析规则定位后台播放服务。`playback-service` 在 manifest 中暴露对应 action，`app` 与页面层只依赖 `playback-client` / `playback-contract`。

**Rationale:**

- 当前 `app` 直接引用 `PlayerMediaSessionService::class.java`，使宿主代码知道播放服务实现类，破坏了客户端边界。
- 通过 action + 包管理器解析服务组件，可以将服务定位逻辑收敛到播放客户端内部。

**Alternatives considered:**

- 继续让 `app` 传服务类，但只换成别的包装器：本质上仍然是实现细节泄漏。

### 6) 通过 `build-logic` convention plugin 与环境配置对象治理构建和运行时配置

**Decision:** 新增 `build-logic` included build，用 convention plugin 治理共享 Android library / application / Compose 配置；同时引入统一的环境配置对象，让 `AppContainer` 从生成配置中读取 base URL，而不是继续硬编码。

**Rationale:**

- 当前模块脚本中 `compileSdk`、`minSdk`、Java 版本、测试配置大量重复；继续扩模块会同步放大维护成本。
- `API_BASE_URL` 这类环境值属于配置真相，不应继续放在业务装配代码里。

**Alternatives considered:**

- 只在根脚本里继续手工复制公共常量：短期省事，但无法真正收口脚本重复。
- 一步到位引入更重的构建平台或复杂配置系统：超出当前仓库规模所需。

## Risks / Trade-offs

- [Risk] detail feature 迁模块时仍可能被 `PlayerViewModel`、播放列表模型或共享 UI 反向耦合。  
  Mitigation: 先外置共享模型与 gateway 契约，再迁 feature；Activity 保留在 `app` 作为过渡宿主。

- [Risk] `build-logic` 与模块脚本调整会影响构建稳定性。  
  Mitigation: 先提取共享 convention，再逐模块替换；每完成一批模块替换都运行 `assembleDebug`。

- [Risk] 播放服务 action 解析若实现不稳，可能导致无法连接后台播放服务。  
  Mitigation: 保持 manifest 中 MediaSessionService 声明不变，并为解析与连接行为补单测/集成验证。

- [Risk] 结构迁移期间的 import 与 package 变更可能导致 Robolectric 测试与 Activity 路由回归。  
  Mitigation: 保留现有入口 `Intent` 语义与关键 `testTag`；分阶段运行主页、我的、详情页和播放器相关测试。

- [Trade-off] 为了控制风险，本次不会把播放器页和 detail host chrome 一并迁出 `app`，因此 `app` 仍保留少量宿主适配代码。  
  Mitigation: 明确将这些代码限定为宿主层，避免继续向其中堆入 feature 业务实现。

## Migration Plan

1. 引入 `build-logic` convention plugin，并把共享 Android 配置抽到统一约束；同时为 `app` 增加环境配置入口，移除 `AppContainer` 中的硬编码 base URL。
2. 调整 `playback-client` 与 `playback-service` 的服务定位方式，使 `app` 不再直接引用播放服务实现类。
3. 外置 detail feature 迁移所需的共享契约与共享 detail UI 支撑，包括播放队列模型、`DetailPlaybackGateway` 契约与 `feature-detail-support` 模块。
4. 新建 `feature-playlist-detail`、`feature-album-detail`、`feature-artist-detail` 模块，迁出 repository、mapper、ViewModel 与 screen composable；`app` 保留薄 Activity 适配器。
5. 更新 `AppContainer` 与宿主入口引用，删除 `app` 中已迁移的实现副本。
6. 逐阶段验证：定向 Robolectric + `:app:assembleDebug` + 关键手动冒烟。

回滚策略：

- 若 `build-logic` 或环境配置引入构建回归，可单独回退 convention 与配置提交，不影响业务模块迁移。
- 若播放边界收口导致连接异常，可回退 `PlayerServiceBridge` 的解析策略，恢复旧连接路径。
- 若某个 detail feature 模块迁移产生回归，可按 feature 单位回退对应模块与 `app` 适配器改动，而不需要整体回退全部结构优化。

## Open Questions

- 不存在阻塞性开放问题；若在实施中发现某个 detail feature 仍被新的跨模块类型耦合阻塞，则优先外置该共享契约，而不是回退到继续把实现堆回 `app`。
