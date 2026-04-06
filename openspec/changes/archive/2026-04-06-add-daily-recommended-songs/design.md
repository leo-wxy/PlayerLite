## Context

首页 `/homepage/block/page` 返回的“每日推荐”当前属于 `DRAGON_BALL` 快捷入口资源，接口里只有标题、图片和 `orpheus://songrcmd` 这类动作信息，并不携带每日推荐歌曲列表本体。仓库当前的首页发现流会把这类资源统一映射为 `HomeSectionItemUiModel`，默认只保留 `id/title/subtitle/imageUrl/badge/action` 等通用字段，最后在首页渲染成紧凑快捷卡。

仓库已验证 `/recommend/songs` 在登录态下可以返回完整的 `dailySongs` 列表，单项至少包含歌曲 id、名称、歌手、专辑、封面、时长和推荐理由。现有代码里，受登录态约束的列表页已经有稳定模式：`Activity + ViewModel + Repository` 承载页面与状态，远端请求统一通过 `JsonHttpClient(requiresAuth = true)` 复用当前用户会话，遇到 `301/302` 统一抛出 `UserSessionInvalidException` 并触发登出。

对于歌曲点击结果，当前代码里并不存在可直接承接 `SearchRouteTarget.Song` 的站内详情页；真正稳定的歌曲点击闭环是 `DetailPlaybackGateway + PlaylistItem` 这条播放接线，歌单详情、专辑详情和本地歌曲页都沿用这套方案把列表项直接送入播放器队列。因此，这次能力不应继续沿用“歌曲详情跳转”假设，而应直接接到现有播放链路。

## Goals / Non-Goals

**Goals:**
- 新增站内“每日推荐歌曲”页面，承接首页“每日推荐”快捷入口。
- 在登录态下调用 `/recommend/songs` 获取每日推荐歌曲列表，并提供加载态、空态、失败态和登录失效反馈。
- 让每日推荐歌曲项具备稳定的点击结果，沿用现有详情页/列表页的歌曲播放闭环。
- 仅修改首页“每日推荐”入口的站内落地方式，不影响其他 `DRAGON_BALL` 快捷入口。

**Non-Goals:**
- 不在这次变更里引入通用的 `orpheus://` 路由框架。
- 不同时改造“私人 FM”“排行榜”“歌单”等其他首页快捷入口。
- 不新增歌曲详情页，也不把 `SearchRouteTarget.Song` 扩展为新的宿主导航契约。
- 不在这次变更里引入分页、离线缓存或复杂的推荐理由筛选能力。

## Decisions

### 1. 以独立页面承载每日推荐歌曲，而不是塞回首页主流

实现上新增 `DailyRecommendedSongsActivity`、`DailyRecommendedSongsViewModel` 和对应的 Compose Screen，放在 `app/src/main/java/com/wxy/playerlite/feature/main/` 下，沿用 `RecentSongsActivity` / `LikedContentActivity` 这类登录态列表页模式。

这样做的原因：
- 首页快捷入口已经是一个明确的二级入口，独立页面更符合现有导航层级，也避免把首页 ViewModel 和首页滚动容器继续做重。
- 现成的登录页跳转、重试、空态/失败态承载模式都可以直接复用，落地成本低。
- 独立页面更适合后续补充“播放全部”“日期说明”“推荐理由”等头部信息，而不需要把首页主流变成特例。

备选方案：
- 直接在首页展开内嵌纵向歌曲列表。缺点是会把首页发现流和登录态歌曲列表耦合在一起，增加首页刷新、状态恢复和滚动复杂度。
- 复用一个完全通用的“在线歌曲列表 Activity”。当前仓库没有现成抽象，硬抽象会先把问题做大。

### 2. 为 `/recommend/songs` 建立专用 repository 和 song-centric UI model

新增 `DailyRecommendedSongsRepository` 和 `NeteaseDailyRecommendedSongsRemoteDataSource`，远端请求直接调用 `/recommend/songs`，并使用 `requiresAuth = true` 复用当前 `JsonHttpClient` 的鉴权头。Repository 产出独立的 `DailyRecommendedSongUiModel` 列表，而不是复用 `UserCenterCollectionItemUiModel`。

选择专用模型的原因：
- 每日推荐歌曲需要稳定保留 `recommendReason`、`songId`、`durationMs`、艺人列表和专辑信息，和用户中心通用收藏项的字段形状并不完全一致。
- 这类列表最终要接播放器队列，song-centric model 更适合直接转换为 `PlaylistItem`。
- 避免把用户中心的“通用内容卡”模型继续扩成一个承担所有歌曲场景的万能结构。

备选方案：
- 复用 `UserCenterCollectionItemUiModel`。缺点是推荐理由只能塞进 `meta`/`badge` 之类的泛字段，后续很快会再次拆分。

### 3. 首页“每日推荐”入口改为内部 action，而不是继续依赖 `orpheus://songrcmd`

在现有 `ContentEntryAction` 上新增明确的内部目标分支，例如 `OpenDailyRecommendedSongs`，并由 `HomeDiscoveryRepository` 在识别到首页快捷入口的“每日推荐”资源时优先映射成该内部 action。`MainActivity.handleContentEntryAction()` 与其他承载 `ContentEntryAction` 的页面继续通过统一入口解析并启动对应 `Intent`。

选择内部 action 的原因：
- 当前 `orpheus://songrcmd` 并不是本应用稳定可解析的站内 Intent 协议，继续走 `ACTION_VIEW` 只会把能力交给外部解析，结果不可控。
- 这次只有一个明确的站内原生入口，先加一个明确 action 最符合当前代码规模，也避免提前发明完整的内部路由框架。
- `ContentEntryAction` 已经是首页/用户中心点击承载的统一语言，沿用它比单独在首页 UI 层写 if/else 更清晰。

备选方案：
- 继续保留 `OpenUri("orpheus://songrcmd")`。缺点是行为依赖外部环境，无法保证站内闭环。
- 一次性把所有内部页面都抽成通用 destination 枚举。当前收益不足，容易超出这次变更边界。

### 4. 歌曲项点击与“播放全部”统一复用 `DetailPlaybackGateway`

每日推荐歌曲页面不走 `SearchRouteTarget.Song` 导航，而是像歌单详情页、本地歌曲页一样，把当前列表转换为 `PlaylistItem` 集合，再通过 `DetailPlaybackGateway.play(DetailPlaybackRequest)` 直接替换播放队列并打开播放器。单曲点击用当前索引作为 `activeIndex`，页头提供“播放全部”时固定使用 `0`。

采用这条链路的原因：
- 仓库已经有稳定的 detail/list -> player 队列替换流程，且播放运行时能基于 `songId` 继续补齐在线播放需要的元数据。
- 当前宿主并没有歌曲详情页承接 `SearchRouteTarget.Song`，继续用详情跳转只会落到“不支持打开”。
- 对用户来说，“每日推荐歌曲列表”最自然的结果就是直接播放，而不是先打开一个不存在的中间页。

备选方案：
- 点击歌曲后显示“不支持打开”。这会让新能力只有列表展示，没有真正使用价值。
- 进入独立歌曲详情页。当前仓库没有该能力，超出这次改动范围。

### 5. 登录态变化和接口失效保持与现有受限页面一致

`DailyRecommendedSongsViewModel` 监听 `userRepository.loginStateFlow`。未登录时页面展示登录引导；登录后触发首轮加载；请求过程中遇到 `UserSessionInvalidException` 时，直接调用 `userRepository.logout()`，让页面按现有全局语义回退到未登录态。这样能与最近播放、喜欢内容、设置页等既有行为保持一致。

备选方案：
- 在页面内部自行维护一套 session-invalid 状态。缺点是会和现有用户会话源头重复，容易出现局部已失效、全局未失效的分叉状态。

## Risks / Trade-offs

- [首页 mapper 需要识别“每日推荐”特例] → 仅对已确认的每日推荐快捷入口做最小分支，其他 `DRAGON_BALL` 资源继续走现有通用映射，避免把首页入口体系整体重写。
- [每日推荐歌曲页新增了一套独立 song list UI] → 首版复用现有受限列表页的脚手架和 DetailPlaybackGateway，不提前抽通用页面；如果后续再出现第二个相同形态页面，再统一抽象。
- [部分每日推荐歌曲可能因版权或音源受限无法正常播放] → 页面仍正常展示列表；播放链路继续依赖现有在线地址解析、登录校验和错误提示，不在页面层重复发明一套播放失败处理。
- [登录失效会导致页面从内容态回退到登录态] → 这是当前受限在线页面的既有语义；通过明确登录引导和重试入口降低突兀感。

## Migration Plan

这是纯前台增量能力，不涉及数据库、持久化 schema 或线上迁移脚本。发布步骤按普通客户端发版处理即可。

回滚方式：
- 回退首页“每日推荐”入口到原有通用 `OpenUri/Unsupported` 行为；
- 移除新增的每日推荐歌曲页面及其接线；
- 不需要清理本地存储数据。

## Open Questions

- 当前设计默认“每日推荐歌曲”首版提供列表展示、单曲点击播放和可选的“播放全部”入口；是否需要补充更多操作（如收藏、更多菜单）留到实现时按现有播放器能力评估。
