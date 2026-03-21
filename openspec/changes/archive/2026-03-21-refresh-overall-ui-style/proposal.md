## Why

当前首页发现流、顶部搜索入口、底部 minibar、播放器展开页、播放列表半浮层与独立搜索页已经具备基本功能，但整体视觉语言仍沿用旧配色与较分散的样式层级，和这轮希望建立的主题方向不一致。更关键的是，`app` 与 `feature-search` 目前各自维护主题入口，页面内也混杂了临时色值与透明度拼接；如果这次只做一轮静态换色，后续要继续换肤或补品牌主题时，成本会再次落回到页面级搜改。现在需要先把核心主题色配置、共享语义 token 与首页、播放器、搜索页关键界面的视觉规范一起收敛下来，给后续主要音乐场景的样式统一和可扩展换肤提供明确基线。

## What Changes

- 引入一套新的主题色配置，以 `primary #e53935`、`secondary #616161`、`tertiary #0087a0`、`neutral #f9f9fb` 作为本轮 UI 风格调整的核心视觉基准。
- 新增轻量共享 `design-system` 主题层，以 “brand palette -> semantic tokens -> page/component tokens” 的三层结构承载这轮样式改造；本轮只落默认主题，不实现运行时换肤入口，但要求后续新增皮肤时不需要回到各页面重复改色。
- 刷新首页发现流的搜索入口、banner 推荐位、功能快捷入口、推荐歌单区和纵向歌曲列表层级，使首页整体更接近参考图中的音乐首页效果。
- 刷新首页底部 minibar 的封面、文本、进度线、主播放按钮与播放列表入口的渲染效果，使其与首页主题语言保持一致，并更接近音乐 App 的胶囊式小播放器。
- 保持现有双 Tab 主壳层信息架构不变，底部导航仍先保留两个入口；本轮只收窄整体宽度、统一圆角和层级，后续若一级入口增加，再重新调整宽度与分布。
- 刷新播放器展开页的主色强调、辅助信息、背景基底与操作层级，使播放页整体视觉更统一，并与新的主题色语义保持一致。
- 刷新播放列表半浮层的头部信息、当前高亮项、普通列表项、拖拽手柄与清空操作样式，使其贴合“Next Up”样式方向。
- 刷新独立搜索页的顶部结构、空查询态与搜索结果态样式，包括独立返回键、轻量圆角搜索框、搜索历史标签、榜单式热门搜索区域、顶部结果类型切换栏与更紧凑的音乐结果列表层级，并补足结果页在快速切换类型时的稳定性，避免当前 tab 短暂落入空白页。
- 保持现有首页发现数据、搜索入口导航、minibar 点击/滑动行为与 Home 域播放器层级关系不变，本轮聚焦首页壳层与内容区的视觉表达，而不是改写首页信息架构或播放语义。
- 保持现有播放队列、拖拽排序、切歌与播放控制语义不变，本轮聚焦视觉表达、信息层级和主题一致性，而不是改写播放列表业务规则。
- 保持现有搜索请求、历史记录、热搜加载、结果切换与结果跳转语义不变，本轮聚焦搜索页的结构层次、组件皮肤和统一主题表达，而不是改写搜索流程行为。

## Capabilities

### New Capabilities
- `ui-theme-tokens`: 定义本轮样式调整使用的共享主题色 token 及其语义映射，建立可扩展的共享主题 contract，明确主强调色、次级信息色、辅助强调色与中性色基底在播放器相关界面中的使用边界，并为后续新增皮肤保留稳定入口。

### Modified Capabilities
- `homepage-discovery-content`: 调整首页发现流的视觉要求，包括 banner 推荐位、快捷入口、推荐歌单卡片和纵向歌曲列表的层级、圆角、留白与文字节奏，使首页更贴近参考音乐首页效果。
- `homepage-floating-search`: 调整首页顶部搜索入口的视觉要求，使其在首页中以更轻量的圆角搜索框呈现，并与独立搜索页保持统一的主题语言。
- `player-expanded-page`: 调整播放器展开页对主题配色、文字层级、操作强调与背景基底的视觉要求，使其围绕新的主题色配置形成稳定一致的播放器主界面风格。
- `playlist-management`: 调整播放列表半浮层的视觉要求，包括标题区信息、清空操作、当前项高亮、普通项层级、封面与拖拽手柄样式，使播放队列呈现符合新的设计方向。
- `search-page`: 调整独立搜索页与搜索结果页的视觉要求，包括左侧独立返回键、顶部轻量搜索栏、历史搜索标签、清空操作、榜单式热门搜索容器、固定结果类型切换栏，以及带封面/副标题/右侧更多操作入口的紧凑结果列表层级，并保证快速切换结果类型时当前 tab 始终展示可见页状态而非空白，使搜索场景和播放器场景共享同一套主题与样式语言。
- `user-center-tab-shell`: 调整首页所属主壳层与底部 minibar 的视觉要求，保持现有双 Tab 架构不变，同时让底部导航采用更窄的整体宽度与更统一的胶囊样式，并让首页 minibar 呈现参考图中的小播放器效果。

## Impact

- `app/src/main/java/com/wxy/playerlite/ui/theme/`
- `design-system/src/main/java/com/wxy/playerlite/designsystem/theme/`
- `app/build.gradle.kts`
- `feature-search/build.gradle.kts`
- `settings.gradle.kts`
- `app/src/main/java/com/wxy/playerlite/feature/player/ui/`
- `app/src/main/java/com/wxy/playerlite/feature/player/ui/components/`
- `app/src/main/java/com/wxy/playerlite/feature/main/`
- `app/src/main/java/com/wxy/playerlite/feature/search/`
- `openspec/specs/homepage-discovery-content/spec.md`
- `openspec/specs/homepage-floating-search/spec.md`
- `openspec/specs/player-expanded-page/spec.md`
- `openspec/specs/playlist-management/spec.md`
- `openspec/specs/search-page/spec.md`
- `openspec/specs/ui-theme-tokens/spec.md`
- `openspec/specs/user-center-tab-shell/spec.md`
