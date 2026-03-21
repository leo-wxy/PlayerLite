## Why

现有歌手详情页已经完成基础信息、百科摘要与热门歌曲的首版闭环，但内容结构仍停留在首版承载，尚未覆盖歌手专辑浏览，也没有对齐新的 HTML UI 参考所要求的页面层次与视觉组织。现在用户已经明确给出新的歌手专辑接口 `/artist/album`、需登录的百科接口 `/ugc/artist/get`，以及用于头部增强的 `/artist/detail/dynamic` 与 `/artist/follow/count`；同时，最近确认的 UI 约束还包括封面全宽正方形且无圆角、简介预览两行、颜色统一走 theme roles、sticky header 需避开状态栏安全区、切换 tab 时不能继承上一 tab 的滚动偏移。因此需要把歌手详情页重构为更完整、可扩展且对登录态约束有明确兜底的详情承载。

## What Changes

- 重构歌手详情页的页面结构与视觉层次，使头部、百科、热门歌曲和专辑区域能够对齐新的 HTML UI 参考，而不是继续沿用首版详情布局。
- 在歌手详情页头部补充接入 `/artist/detail/dynamic` 与 `/artist/follow/count`，用于增强是否关注、视频数、粉丝数等信息；这些字段属于头部软依赖，不阻塞基础详情页承载。
- 在歌手详情页中增加歌手专辑内容承载，使用 `/artist/album` 按 `limit/offset` 分页加载专辑列表，支持从歌手详情页继续浏览该歌手的作品。
- 将歌手百科的数据源切换为需登录的 `/ugc/artist/get`，并明确登录可用、接口失败或无百科数据时的非阻塞降级行为。
- 明确粉丝数展示规则：当 `fansCount` 达到 `10000` 及以上时，展示为按 `10000` 换算后的 `w` 单位结果；低于 `10000` 时展示具体数值。
- 明确 UI 方向约束：HTML 仅作视觉参考而非逐像素复刻目标，头图保持全宽正方形且无圆角，简介预览保留两行，颜色优先走 theme roles，列表 item 采用小间距卡片，百科 tab 视觉尽量填满以避免回弹，同时 sticky tab / 固定头部需要与状态栏安全区正确协同，不得卡进状态栏；用户切换 tab 时，外层滚动位置需要保持稳定，各 tab 保留各自独立的内容滚动位置，不能把上一 tab 的滚动偏移错误继承到当前 tab。
- 保留并整理现有热门歌曲播放、失败重试和局部加载态能力，确保重构后页面仍满足详情页的可恢复性要求。

## Capabilities

### New Capabilities

无。本次变更聚焦于重构和扩展既有歌手详情能力，不新增独立 capability。

### Modified Capabilities

- `artist-detail-page`: 扩展歌手详情页的需求边界，使其除基础信息、百科摘要与热门歌曲外，还需要承载歌手专辑列表、接入头部增强软依赖接口、使用需登录的百科接口，并按新的 UI 参考重组详情页视觉结构、顶部安全区/sticky tab 协同与 tab 切换滚动复位行为。

## Impact

- 受影响代码主要位于 `app/src/main/java/com/wxy/playerlite/feature/artist/`，包括 `ArtistDetailActivity`、`ArtistDetailViewModel`、`ArtistDetailRepository` 及相关 UI 结构。
- 需要扩展歌手详情远端数据访问与映射逻辑，引入 `/artist/album`、`/ugc/artist/get`、`/artist/detail/dynamic` 和 `/artist/follow/count`，并继续复用现有认证请求链路。
- 需要同步更新相关测试，重点覆盖 repository 映射、ViewModel 状态分支以及歌手详情页的 Robolectric UI 行为。
- 设计阶段需要把用户提供的 HTML 文件作为 UI 对照参考，明确哪些是视觉重构，哪些属于 capability 层面的需求变化，并避免把视觉参考写成逐像素复刻或“必须与 HTML 完全一致”的要求。
