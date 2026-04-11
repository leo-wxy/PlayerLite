## Why

当前首页发现流、首页搜索入口、首页歌曲推荐区块和首页 UI 壳层已经形成独立 feature 体量，但核心实现仍集中在 `app` 模块内，导致 `app` 同时承担宿主、路由、首页实现与首页数据映射职责。现在继续在该结构上扩展首页能力，会让 `app` 重新膨胀为默认业务落点，抵消此前详情页、搜索页和播放列表域的模块化收益。

## What Changes

- 将首页能力从 `app` 中抽离为独立的 Home feature 模块，承载首页页面实现、状态管理、repository、remote data source 与首页映射逻辑。
- 为独立 Home feature 定义稳定的宿主接入方式，使 `app` 只保留双 Tab 主壳、Activity 宿主、首页 route 接入与 composition root 职责。
- 收敛首页相关依赖装配方式，避免 `app` 继续直接构造首页 feature 的内部实现细节。
- 保持现有首页发现流、悬浮搜索框、横向歌曲推荐区块、每日推荐入口与点击闭环行为不变，仅调整模块边界与装配责任。

## Capabilities

### New Capabilities
- `home-feature-architecture`: 定义首页能力作为独立 feature 模块的边界、内部组成和宿主接入契约。

### Modified Capabilities
- `app-shell-composition-root`: 调整 `app` 对首页能力的职责边界，要求宿主通过稳定入口接入独立 Home feature，而不是继续直接保存首页实现细节。

## Impact

- Affected code:
  `app` 模块中的首页页面、首页 ViewModel、首页 repository、首页 remote data source、首页 JSON mapper、首页路由接入与装配代码。
- Affected systems:
  `MainActivity` 主壳、`AppContainer` 装配层、首页发现流与首页搜索入口的宿主 wiring。
- Dependencies:
  可能新增一个独立 Home feature 模块，并调整其与 `design-system`、`network-core`、`playlist-core`、`feature-player`、`user` 及现有详情路由能力之间的依赖关系。
- Non-goals:
  本次变更不顺带重构 `PlayerRuntime` / `playback-orchestrator` 边界，也不改变首页现有用户可见行为。
