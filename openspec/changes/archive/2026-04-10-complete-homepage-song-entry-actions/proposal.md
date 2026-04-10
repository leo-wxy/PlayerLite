## Why

首页已经展示了真实的歌曲推荐资源块，但这些歌曲目前既没有进入站内播放闭环，也没有稳定的次级操作入口，导致首页“能看不能播”、歌曲行右侧“更多”形同占位。与此同时，首页 banner、歌单卡、快捷入口和搜索框目前共享过于平均的视觉层级，首页缺少明确的主次关系。现在需要一起收口两件事：先把首页歌曲资源块变成最小可用的音乐入口，再把 Home 页整体视觉层级拉开。

## What Changes

- 将首页 `song` 资源块从通用卡片/不可播放入口收口为站内歌曲推荐行。
- 将首页 `song` 资源块改成带封面、三行信息和更多入口的横向歌曲卡片模块。
- 用户点击首页歌曲行时，以当前区块歌曲列表替换当前播放队列，从被点击歌曲开始播放并打开播放器。
- 首页歌曲行右侧“更多”按钮改为真实菜单，首批支持“下一首播放”“查看专辑”“查看歌手”。
- 首页歌曲资源缺少可执行主动作或次级动作所需信息时，提供明确降级结果，而不是静默无响应。
- 收口 Home 页发现流视觉层级，拉开 banner、歌曲卡、歌单卡、快捷入口和顶部搜索入口之间的主次关系。

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `homepage-discovery-content`: 首页歌曲推荐区块需要从展示型内容升级为站内可播放歌曲入口，并定义歌曲行点击与“更多”菜单行为。
- `homepage-floating-search`: 首页顶部搜索入口需要随 Home 页视觉收口一起调整为更轻量、与发现内容更协调的搜索入口。
- `playlist-management`: 播放列表需要支持来自首页歌曲“更多”菜单的“下一首播放”单曲插入能力，而不破坏当前播放上下文。

## Impact

- 首页发现数据映射与首页歌曲行 UI：`HomeDiscoveryRepository`、`MainShellScreen`
- 首页搜索入口与首页发现视觉层级：`MainShellScreen`、`HomeDiscoveryLayoutSpec`
- 首页歌曲播放与“更多”菜单编排：`MainActivity`、首页相关状态与事件处理
- 播放列表域与播放编排：`playlist-core`、`playback-orchestrator`
- 首页相关测试、OpenSpec delta specs 与任务清单
