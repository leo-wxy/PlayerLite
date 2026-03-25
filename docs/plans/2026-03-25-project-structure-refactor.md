# Project Structure Refactor Design

## Summary

本文档用于重构 `PlayerLite` 当前项目结构，目标不是机械地增加模块数量，而是把已经存在但尚未收口干净的职责边界真正落到结构上：

- `app` 回到应用壳、入口适配和 composition root 职责
- 播放域的应用侧编排从 `app` 中抽离
- detail/search/player 三类 feature 的接入策略尽量统一
- 保持现有 `playback-client` / `playback-contract` / `playback-service` 边界稳定
- 在不打断当前可运行主链路的前提下，逐步降低 `PlayerViewModel` 与 `PlaybackProcessRuntime` 的职责密度

这是一份增量重构设计，不追求一次性大拆分，不引入“为了模块化而模块化”的空层。

## Current Verified State

基于当前仓库实际代码结构，已验证事实如下：

1. `app` 仍然是最大聚合点，直接依赖 `feature-*`、`playback-client`、`playback-contract`、`playback-service`、`player`。
2. `app` 不只是系统入口壳，还承载了：
   - `AppContainer`
   - `PlayerViewModel`
   - `PlayerRuntime`
   - `PlaylistController`
   - 登录、首页、用户中心、本地歌曲、播放器 UI
   - 详情页 Activity 宿主
3. `feature-playlist-detail`、`feature-album-detail`、`feature-artist-detail` 内部已经基本形成 `Repository -> ViewModel -> Screen` 的稳定结构。
4. `feature-search` 的封装方式比 detail feature 更完整，它自带 `SearchActivity` 和宿主依赖接缝。
5. `playback-client`、`playback-contract`、`playback-service` 的跨进程控制边界相对清晰，已经是当前工程里最成熟的一段结构。
6. `player` 与 `cache-core` 的底层抽象也比较清晰，`IPlaysource` 与 `RangeDataProvider` 是两条关键骨架接口。
7. `network-core` 负责业务 JSON API，并不承载媒体 Range 字节流；真正的媒体 Range 拉流实现位于 `playback-service`。

## Core Problems

当前结构的主要问题，不是“模块太少”，而是“结构名义上已经分层，但应用侧编排尚未真正收口”。

### 1. `app` 过重

`app` 同时承担了：

- Android 系统入口
- 业务导航宿主
- 依赖装配
- 播放器 UI
- 播放列表状态
- 应用侧播放 runtime
- detail feature 宿主

这导致 `app` 既是壳，又是功能中心，又是运行时中心。

### 2. feature 接入策略不统一

当前至少存在两种模式：

- detail feature：逻辑在 feature，Activity 宿主还在 `app`
- search feature：Activity、ViewModel、UI、仓库接缝都在 feature

这种不一致会让“什么应该放 feature，什么应该留在 app”越来越模糊。

### 3. 播放域应用侧编排与 UI 耦合过深

`PlayerViewModel` 当前同时承担：

- UI 状态驱动
- 本地 runtime 调度
- 与后台播放进程同步
- 远端快照回灌
- 歌词/百科加载
- 用户态订阅

这使它既像页面 ViewModel，又像播放域应用服务。

### 4. `PlaybackProcessRuntime` 职责密度过高

`PlaybackProcessRuntime` 当前同时承担：

- 队列与选曲状态
- 播放/暂停/seek/stop 编排
- 缓存初始化
- 在线播放 URL 解析接线
- source 准备与切换
- 部分状态文本与持久化逻辑

它已经接近 service 侧的“超级 runtime”。

### 5. 协议层与域模型边界略混

`playback-contract` 已经不仅是 action/DTO 常量层，还承载：

- `PlayableItemSnapshot`
- `PlaybackMetadataExtras`
- playlist 共享模型
- 若干播放器相关模型

这本身不是错误，但需要明确它是“播放域共享模型层”，而不是只读作“轻量 contract”。

## Refactor Principles

本次结构重构遵循以下原则：

1. 不破坏现有独立 `:playback` 进程方案。
2. 不重写播放链路，只重组边界和职责归属。
3. 先收口应用侧结构，再清理 service 内部结构；不要同时大改两边。
4. 已经稳定的底层边界尽量保持不变：
   - `playback-client`
   - `playback-contract`
   - `playback-service`
   - `player`
   - `cache-core`
5. 新模块必须对应真实职责，不创建只有转发代码的空模块。
6. 迁移顺序优先选择“低风险、高收益、可单独验证”的步骤。

## Target Architecture

目标结构按职责分为六层：

```text
1. Application Shell
   app

2. App-side Domain Orchestration
   playback-orchestrator        (new)
   playlist-core                (new)

3. Feature Layer
   feature-player               (new, from app player UI)
   feature-search
   feature-playlist-detail
   feature-album-detail
   feature-artist-detail
   feature-detail-support
   user
   design-system

4. Cross-process Playback Boundary
   playback-client
   playback-contract

5. Playback Execution Layer
   playback-service

6. Native/Foundation Layer
   player
   cache-core
   network-core
```

## Target Module Responsibilities

### `app`

保留为应用壳，只负责：

- `Application`
- 主导航入口与 Activity 注册
- 薄 Activity 宿主
- feature 装配与 route wiring
- composition root

不再承载：

- 播放域运行时核心
- 歌单状态核心
- 播放器 feature 的主体实现

### `playlist-core`（新增）

从 `app/core/playlist` 中抽离播放列表域核心，承载：

- `PlaylistController`
- `PlaylistStorage`
- playlist state / codec
- active-index、shuffle、playback mode 本地状态规则

这个模块不应依赖 UI、Activity、Media3 service 或 feature。

### `playback-orchestrator`（新增）

这是应用侧播放域编排模块，承载：

- `PlayerRuntime`
- `PlayerRuntimeRegistry`
- `DetailPlaybackGateway` 契约与实现
- 本地播放选择态与远端快照投影规则
- 与 `playback-client` 的应用侧协同逻辑

它的定位不是 UI feature，而是“app-side playback domain orchestrator”。

### `feature-player`（新增）

将当前 `app/feature/player/ui` 以及播放器页相关 ViewModel/UI 状态迁出，承载：

- `PlayerActivity` 或播放器页面宿主接缝
- `PlayerViewModel`
- `PlayerScreen`
- 歌词/百科 UI 相关状态与展示模型
- 播放器页组件

注意：`PlayerViewModel` 中与应用侧播放编排直接相关的能力，后续应尽量下沉到 `playback-orchestrator`，它自己保留页面态、用户交互和页面数据加载协调。

### detail feature 模块

保持 detail feature 继续只负责：

- repository
- mapper
- ViewModel
- screen composable
- detail domain state

中期目标是让 Activity 宿主也下沉到各自 feature，或者至少统一为同一种宿主模式。

### `playback-client`

继续作为 app 侧远程控制 SDK，职责保持不变：

- 连接 `MediaSessionService`
- 同步队列
- 发送 custom command
- 映射远端快照

不引入业务页面或本地播放列表状态。

### `playback-contract`

明确其定位为“播放域共享协议与共享模型层”，允许承载：

- service 发现契约
- custom command 常量
- `MediaItem` 编解码模型
- session extras 编解码
- 跨进程共享的播放域模型

不再把它描述成“只有 contract 常量”的极薄层。

### `playback-service`

保持模块边界不变，但内部包结构做进一步收口，建议演进为：

```text
playback-service/process/
  session/       MediaSessionService, PlayerSessionPlayer, callback
  runtime/       PlaybackProcessRuntime, state, queue coordination
  source/        source repository, track preparation, online/cached source
  action/        custom command handling / runtime command handlers
  notify/        foreground notification
```

目的不是新建更多目录，而是把当前集中在 `PlaybackProcessRuntime` 和 `PlayerMediaSessionService` 的职责拆开。

## Proposed Migration Path

重构分四期推进。

### Phase 1: 应用侧核心域抽离

目标：先把 `app` 中最重、最稳定的非 UI 核心抽出来。

范围：

- 抽出 `playlist-core`
- 抽出 `playback-orchestrator`
- 让 `app` 通过新模块消费 `PlaylistController`、`PlayerRuntime`

完成标准：

- `app` 不再持有 `core/playlist/*`
- `app` 不再持有 `feature/player/runtime/*`
- `PlayerRuntime` 与 `PlaylistController` 已不依赖 `app` 内部 UI 类型

收益：

- 这是对 `app` 瘦身最明显的一步
- 风险低于直接拆播放器 UI 或 service

### Phase 2: 播放器 feature 独立化

目标：把“播放器页面”从应用壳中拿出来。

范围：

- 新建 `feature-player`
- 迁出播放器 UI、组件、页面状态模型、`PlayerViewModel`
- 明确 `feature-player -> playback-orchestrator -> playback-client` 的依赖链

完成标准：

- `app` 不再持有播放器页主体 UI
- 播放器页面只通过 feature 对外暴露入口
- `PlayerViewModel` 不再直接承载过多 domain 规则

收益：

- 应用壳和播放器页面职责分离
- 详情页、首页、播放器页围绕同一套 orchestrator 工作

### Phase 3: feature 宿主模式统一

目标：统一 detail/search/player 三类 feature 的接入方式。

优先方案：

- detail feature 继续保留 feature 内 `ViewModel + Screen`
- Activity 宿主逐步从 `app` 下沉到对应 feature

过渡方案：

- 若短期不下沉 Activity，至少统一成“`app` 只有极薄 Activity adapter，全部 factory 和 route 逻辑由 feature 自持”

完成标准：

- 不再出现 search 是自持 Activity、detail 却完全依赖 app 宿主的明显结构差异

### Phase 4: playback-service 内部收口

目标：不改模块边界，只改 service 内部结构可维护性。

范围：

- 拆薄 `PlaybackProcessRuntime`
- 抽出 action handler / state reducer / source preparation coordinator
- 明确 session 层和 runtime 层之间的命令边界

完成标准：

- `PlaybackProcessRuntime` 不再同时承担过多初始化、状态机、source 准备与命令分发职责
- `PlayerMediaSessionService` 不再继续增长为全能入口

## Detailed Adjustment Recommendations

### Recommendation 1: `app` 不再直接依赖 `player`

当前 `app` 直接依赖 `:player`，但从结构目标看，应用壳不应该认识 native 播放器模型。

建议目标：

- `app` 依赖 `feature-player`、`playback-orchestrator`、`playback-client`
- 若必须消费少量播放模型，优先经由 `playback-contract` 或 orchestrator 暴露

### Recommendation 2: `DetailPlaybackGateway` 契约从 `app` 内部类型中解耦

它现在虽然已经是接缝，但实现仍强依赖 `PlayerRuntimeRegistry`。

建议目标：

- 契约放到 `playback-orchestrator`
- detail feature 只依赖契约，不感知 `app`

### Recommendation 3: `PlayerViewModel` 降职责

建议把以下规则逐步下沉到 `playback-orchestrator`：

- 本地播放队列与远端队列同步规则
- 非权威远端快照的投影规则
- 播放速度、播放模式、音效设置的本地/远端一致性处理

`PlayerViewModel` 自己保留：

- 页面交互
- 歌词/百科请求协调
- 页面显示状态

### Recommendation 4: `PlaybackProcessRuntime` 内部拆为可测的服务对象

建议至少拆出三类对象：

- queue/runtime state coordinator
- source preparation coordinator
- playback command executor

这样可以避免一个 runtime 同时持有所有播放进程复杂度。

## Non-goals

以下内容不属于本次结构重构目标：

- 重写 `MediaSessionService` 架构
- 替换 `FFmpeg + JNI` 播放内核
- 改造 `cache-core` native 缓存设计
- 改动网络 API 语义
- 一次性把所有 Activity 全部迁出 `app`

## Risks

### 1. 播放状态权威混乱

应用侧 runtime、页面 ViewModel、远端 service 快照同时存在时，最容易出现“双真相”问题。

控制策略：

- 任意阶段都必须明确谁是某类状态的权威来源
- 先抽 orchestrator，再拆 ViewModel，避免页面直接接远端快照

### 2. 迁移期间回归播放主链路

播放、切歌、seek、详情页点播、minibar、通知返回播放器，这几条都是高风险链路。

控制策略：

- 每期迁移后都做主链路冒烟
- 不在同一期同时改 app 侧和 service 侧权威逻辑

### 3. 模块增加但边界未真正改善

这是最常见的伪模块化风险。

控制策略：

- 新模块必须有清晰职责和独立验证价值
- 不创建“纯转发模块”“只有 re-export 的模块”

## Verification Strategy

每一期结构迁移完成后，至少执行以下验证：

```bash
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

除自动验证外，建议固定执行一轮人工冒烟：

1. 首页进入播放器，播放、暂停、切歌正常
2. 歌单/专辑/艺人详情进入后可直接点播
3. `minibar`、播放列表 sheet、PlayerActivity 之间状态一致
4. 后台播放通知能正常返回播放器页
5. 登录态对需要鉴权的播放/详情链路无回归

## Rollback Strategy

回滚策略按阶段执行，不做整仓回滚：

1. `playlist-core` 抽离可单独回退
2. `playback-orchestrator` 抽离可单独回退
3. `feature-player` 独立化可单独回退
4. `playback-service` 内部收口可按包级别回退

原则是每期只引入一种主要结构变化，使回滚面保持局部可控。

## Recommended Execution Order

如果按交付优先级排序，建议执行顺序如下：

1. `playlist-core` 抽离
2. `playback-orchestrator` 抽离
3. `feature-player` 独立化
4. detail/player/search 宿主模式统一
5. `playback-service` 内部拆薄

这个顺序的原因是：

- 先拆应用侧稳定核心，收益最高、风险最低
- 再拆播放器 UI，避免 `app` 继续长胖
- 最后再动 service 内部，避免把最高风险变更放到最前面

## Final Assessment

当前仓库并不是“结构很乱”，而是“已经有了正确分层雏形，但 `app` 还没有真正退回壳层”。

因此最正确的重构方向不是继续无差别拆模块，而是围绕以下两点收口：

1. 把应用侧播放编排从 `app` 中抽出来，建立稳定的 orchestrator 层。
2. 把 `app` 限制回入口壳、宿主适配和依赖装配。

只要这两点做对，detail feature、player feature、service 内部结构都会更容易继续演进。
