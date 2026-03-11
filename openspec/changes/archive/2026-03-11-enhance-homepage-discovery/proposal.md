## Why

当前首页仍是轻量占位概览，主要展示品牌封面、登录提示和进入播放展开态的按钮，无法承接“首页-发现”接口返回的内容，也没有稳定的搜索入口来展示默认搜索关键词。需要将首页升级为面向发现内容的主入口，让用户一进入应用就能看到可消费的推荐内容，同时为后续搜索与内容运营提供承载位。

## What Changes

- 清理当前首页概览态中的占位型展示内容，改为面向发现内容的首页首屏。
- 在首页顶部增加悬浮搜索框，并支持默认搜索关键词轮播展示。
- 首页内容改为按“首页-发现”接口的数据结构进行展示，覆盖首屏加载、空态和异常态的承载。
- 保持首页仍属于现有 Home 域；如当前进入播放展开态的入口形态需要调整，应同步明确新的首页内跳转约定。

## Capabilities

### New Capabilities
- `homepage-discovery-content`: 定义首页如何消费“首页-发现”接口并按区块展示发现内容，以及相关加载态、空态与异常态。
- `homepage-floating-search`: 定义首页悬浮搜索框的呈现方式，以及默认搜索关键词的轮播展示行为。

### Modified Capabilities
- `user-center-tab-shell`: 调整首页概览态的 requirement，使首页从轻量占位概览升级为发现内容主入口，同时保持 Home Tab 与播放展开态属于同一域的交互关系。

## Impact

- 影响首页主界面与 Home 域状态承载，重点包括 `app/src/main/java/com/wxy/playerlite/MainActivity.kt` 与 `app/src/main/java/com/wxy/playerlite/feature/main/MainShellScreen.kt`。
- 需要补充首页发现内容的数据建模、状态映射与接口接入，以支持按“首页-发现”接口驱动页面展示。
- 需要新增首页发现内容与悬浮搜索的 specs，并为 `user-center-tab-shell` 补充对应的 delta spec。
