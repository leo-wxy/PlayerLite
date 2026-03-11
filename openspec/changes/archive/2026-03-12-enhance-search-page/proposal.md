## Why

首页已经具备悬浮搜索框和默认关键词轮播，但点击后仍只是提示“即将上线”，用户无法真正进入搜索流程。现在需要补齐独立搜索页与基础搜索接口接入，让首页搜索入口从展示型占位升级为可用能力，尽快承接热搜浏览、搜索建议和结果检索。

## What Changes

- 新增独立 `SearchActivity` 作为搜索页承载，提供更偏音乐 App / 网易云风格的顶部搜索栏、历史搜索、推荐热搜、搜索建议与搜索结果的一体化内容承载。
- 用户进入搜索页后，优先调用 `/search/hot/detail` 展示更丰富的热搜列表，作为首屏默认内容与推荐搜索入口，并允许对热搜结果做接口缓存以避免频繁重复请求。
- 用户输入关键词时调用 `/search/suggest` 获取搜索建议，并在选择建议或提交关键词后调用 `/cloudsearch` 展示搜索结果。
- 为搜索页增加本地持久化的历史搜索展示，支持退出重进后继续复用最近搜索记录。
- 将首页悬浮搜索框的点击行为从临时 Toast 升级为进入搜索页，同时保留现有默认关键词轮播展示能力。
- 为搜索页补充加载态、空态与错误态，并把页面视觉从通用 Material 风格收敛到更偏网易云的轻量音乐 App 风格：更柔和的背景层次、更轻的历史搜索标签、更像榜单的推荐热搜区，以及更紧凑的结果列表密度。
- 在搜索页保留当前视觉方向的前提下，适度放大搜索输入、历史、热搜、建议和结果相关字级，提升不同设备上的可读性。
- 将首页概览态整体视觉也收敛到与搜索页一致的中度网易云风格：统一首页搜索入口、发现区标题、banner、横滑推荐卡片、快捷入口卡片与底部播放入口卡的视觉语言。
- 移除首页快捷入口卡片中重复出现的“推荐”标签，并适度降低该类卡片高度，减少视觉噪声与占高。

## Capabilities

### New Capabilities
- `search-page`: 定义独立搜索页的进入方式、紧凑头部与滚动布局、历史搜索、首屏热搜展示、搜索建议交互、关键词提交后的搜索结果承载，以及更偏网易云风格的视觉层次与对应的加载/空态/异常态。

### Modified Capabilities
- `homepage-floating-search`: 补充首页悬浮搜索框的点击 requirement，并让其视觉层次与搜索页保持一致的音乐 App 风格搜索入口。
- `homepage-discovery-content`: 补充首页发现流整体视觉层次 requirement，使 banner、推荐卡片、快捷入口与模块标题从通用卡片风格收敛到更偏音乐 App / 网易云的首页样式。
- `user-center-tab-shell`: 补充首页概览态底部播放入口与 Home 壳层视觉统一 requirement，确保底部入口卡与首页整体风格一致。

## Impact

- 影响首页搜索入口与主界面跳转，重点包括 `app/src/main/java/com/wxy/playerlite/feature/main/MainShellScreen.kt` 与 `app/src/main/java/com/wxy/playerlite/feature/main/HomeViewModel.kt`。
- 需要新增搜索页相关 UI、状态管理与 Activity 承载，预计落在 `app` 模块新的搜索 feature 目录及 Android manifest 注册。
- 需要补充 `/search/hot/detail`、`/search/suggest`、`/cloudsearch` 的远端数据源、DTO 映射、热搜缓存策略、本地历史搜索持久化与状态建模，并与现有 `network-core` / `JsonHttpClient` 约定保持一致。
- 需要新增 `search-page` spec，并为 `homepage-floating-search` 增补 delta spec，以约束首页入口与搜索页之间的交互契约。
- 需要继续调整 `MainShellScreen.kt` 中首页概览态相关 Compose 组件样式，并补充 `homepage-discovery-content` / `user-center-tab-shell` 的 delta spec 与对应 UI 回归测试。
