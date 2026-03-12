## Context

当前搜索能力已经具备独立 `SearchActivity`、热搜/建议/结果的基本流程，但实现仍停留在“单一歌曲结果列表”阶段：`SearchRepository` 目前固定用 `/cloudsearch?type=1`，`NeteaseSearchJsonMapper` 只解析 `songs`，`SearchResultItemUiModel` 也仍然是单一 `title/subtitle/cover` 结构。这意味着一旦要支持歌手、专辑、歌单等不同类型，现有模型会立刻失真，页面层也无法根据结果类型提供差异化展示。

与此同时，搜索代码当前仍全部位于 `app/src/main/java/com/wxy/playerlite/feature/search/`，`SearchViewModel` 直接依赖 `AppContainer`，`SearchActivity` 也和 `app` 主题、Manifest 注册、依赖装配绑在一起。用户已经明确要求把搜索能力逐步拆成独立 Module，并且要求“结果类型完全按接口返回定义，本地做映射，后续搜索点击详情页也要能关联”，这说明这次变更不仅是 UI 样式问题，而是一次结果建模与结构边界的升级。

另一个现实约束是：仓库仍要求 `MainActivity` 保持轻量、Compose UI 尽量无状态、网络访问继续走 `JsonHttpClient` + `requiresAuth` 的统一约定。因此这次设计必须同时满足三件事：结果类型建模不能直接泄漏原始 JSON 到 UI，搜索 module 拆分不能破坏现有 cookie / csrf 注入链路，结果点击又要为后续详情页跳转预留稳定的类型与实体标识。

## Goals / Non-Goals

**Goals:**
- 让搜索结果类型严格以接口返回定义为准，并通过本地 mapper 层转换成稳定的本地展示模型。
- 为歌曲、歌手、专辑、歌单等主要搜索类型提供差异化展示能力，而不是继续复用单一歌曲卡片。
- 在本地结果模型中保留后续详情页关联需要的 `type`、`id` 与必要展示摘要，避免后续再回头从原始 JSON 补救。
- 新增独立 `:feature-search` Module，承载搜索页 UI、ViewModel、Repository、远端数据源、mapper 与状态模型。
- 保持现有热搜、建议、历史搜索、cookie / csrf 自动透传能力不回退。
- 让 `app` 侧回归为搜索入口注册、依赖装配和未来详情导航接线的角色，而不是继续承载完整搜索实现。

**Non-Goals:**
- 本次不实现搜索结果点击后的真实详情页，只预留稳定关联能力。
- 本次不改变现有登录态存储、`JsonHttpClient` 鉴权注入机制或全局网络层协议。
- 本次不引入新的全局导航框架，也不顺带重构首页/用户中心等其他 feature 的模块边界。
- 本次不强求一次性覆盖所有后端可能支持的搜索类型；首批以当前产品要展示的主要类型为主，但模型和映射机制要可扩展。

## Decisions

### 1. 用“后端类型定义 + 本地枚举映射”统一结果类型，而不是让页面直接理解原始字段

搜索结果类型将以一个后端对齐的本地定义承载，例如 `SearchResultType`，每个类型至少包含：
- 后端请求需要的 `typeCode`
- `/cloudsearch` 结果对象中的主列表字段名
- 对应详情页关联需要的本地目标类型

首批结果类型按当前需求收敛为：
- `SONG` → `type=1` → `result.songs`
- `ALBUM` → `type=10` → `result.albums`
- `ARTIST` → `type=100` → `result.artists`
- `PLAYLIST` → `type=1000` → `result.playlists`

这样做的原因是：
- 用户已经明确要求“类型相关完全按照接口返回定义”，因此类型语义不能由 UI 自己发明。
- 结果请求、解析、展示、详情关联都需要围绕同一个类型源头组织，否则后面会出现“请求 type 和页面展示类型不是一回事”的漂移。
- 本地枚举层可以屏蔽后端字段名差异，让 UI 层只面向稳定模型，不碰 `songs/albums/artists/playlists` 这些原始 key。

备选方案：
- **方案 A：UI 直接根据 JSON 里有没有某个字段来猜类型。** 实现省事，但会把页面和远端字段名绑定死，不利于测试和后续演进。
- **方案 B：定义一套完全本地化的业务类型名，再手工映射回接口。** 会让接口契约和页面契约分叉，不符合用户要求。

因此本次选择“后端类型定义对齐 + 本地枚举映射”。

### 2. 引入“两段式 mapper”：原始 payload 先按类型解析，再落到稳定的本地结果模型

搜索数据层继续允许 `SearchRemoteDataSource` 返回原始 `JsonObject`，但 repository 内部会新增按类型拆分的 mapper：
- `SearchTypePayloadMapper`：根据 `SearchResultType` 从 `/cloudsearch` 返回体中提取对应列表字段；
- `SearchItemMapper`：把不同类型的原始 item 映射成稳定本地模型；
- `SearchResultRouteMapper`：把结果项同步映射成后续详情页可用的路由目标。

最终页面层不再只拿一个扁平的 `SearchResultItemUiModel`，而是拿带类型语义的本地结果模型，例如：
- `SearchResultUiModel.Song(...)`
- `SearchResultUiModel.Artist(...)`
- `SearchResultUiModel.Album(...)`
- `SearchResultUiModel.Playlist(...)`

这些模型共享一个统一基类，同时都带上：
- `id`
- `resultType`
- `title`
- `subtitle` / `assistText`
- `coverUrl`
- `routeTarget`

这样做的原因是：
- 不同结果类型在 UI 上需要不同信息密度，单一 `title/subtitle/cover` 已经不足够。
- 本地结果模型是未来详情导航、列表点击埋点、播放/收藏动作扩展的稳定承载点。
- 两段式 mapper 能让“接口字段变化”和“UI 布局变化”分层处理，不会让一个变动同时污染所有层。

备选方案：
- **方案 A：保留单一 `SearchResultItemUiModel`，靠可选字段越堆越多。** 初期看似简单，实际会很快演变成难以维护的“万能 DTO”。
- **方案 B：在 Compose 中直接对 `JsonObject` 分支渲染。** 会让 UI 测试、预览与后续扩展都变得脆弱。

因此本次明确采用类型化本地模型与专用 mapper。

### 3. 搜索结果点击现在就要生成稳定的详情关联目标，但导航行为仍留在 app 层

虽然这次不实现详情页，但每个搜索结果项都会在 mapper 阶段生成一个稳定的本地 `routeTarget`，例如：
- `SongDetailTarget(songId)`
- `ArtistDetailTarget(artistId)`
- `AlbumDetailTarget(albumId)`
- `PlaylistDetailTarget(playlistId)`

搜索页 UI 和 `SearchViewModel` 只负责把这个目标通过事件抛出，不在 feature 内直接决定如何打开详情页。`app` 侧后续可以基于这个目标接入实际详情页 Activity / route，而不用重新修改搜索数据模型。

这样做的原因是：
- 用户已经明确要求“后续搜索点击的详情页也会做关联”，如果现在不把关联目标收进模型，后面会再次回到依赖原始字段的临时做法。
- 详情页导航属于宿主应用接线问题，不应该让 repository 或 Compose 直接感知外部页面。
- 先稳定“点击什么对象”比现在就硬接某个详情页更重要。

备选方案：
- **方案 A：先不建目标模型，等详情页做的时候再补。** 会导致当前结果模型缺失关键标识，未来补起来代价更高。
- **方案 B：在搜索 module 内直接写死详情页打开逻辑。** 会让 module 边界反而重新耦合宿主页面。

因此本次选择“现在就建 route target，但导航依旧留给 app 装配层”。

### 4. 新增 `:feature-search` Android library，搜索实现迁出 `app`，但 app 继续作为组合根

这次模块拆分采用中等力度方案：新增 `:feature-search` Android library，把以下内容迁入新 module：
- `SearchActivity`
- 搜索页 Compose UI 与适配样式 spec
- `SearchViewModel`
- `SearchUiState`、结果类型模型、本地 route target
- `SearchRepository`、`SearchRemoteDataSource`
- 搜索历史存储、热搜缓存、mapper

`app` 侧只保留：
- 搜索入口点击接线
- Manifest / Activity 暴露
- 依赖装配与宿主级导航实现

为了避免 `feature-search` 反向依赖 `app`，搜索 module 不再直接 import `AppContainer`，而是通过一个轻量依赖提供接口获取：
- `JsonHttpClient`
- 搜索历史存储或其工厂
- 未来结果点击导航回调注册点

换句话说，`app` 仍然是组合根，但不再是搜索实现所在位置。

这样做的原因是：
- 用户已经明确提出“搜索功能是否可以整体独立一个 module 用来处理流程”，这次设计需要把拆分路径说清楚。
- 继续让 `SearchViewModel` 直连 `AppContainer`，即使文件移动到新 module，也只是形式上的拆分。
- 搜索和首页、用户中心、播放器一样，都应该逐步朝“宿主装配 + feature 自治”的方向收敛。

备选方案：
- **方案 A：搜索继续留在 `app`，只做包内重构。** 风险最低，但无法真正解决宿主壳层持续膨胀的问题。
- **方案 B：搜索 module 自己创建 `JsonHttpClient`、用户态存储和 baseUrl。** 会造成配置复制与鉴权链路分叉。
- **方案 C：同时抽更底层的全局依赖模块。** 长期更干净，但这轮会把范围扩大得过头。

因此本次选择“新增 `:feature-search` + app 继续做组合根”的折中方案。

### 5. 搜索页结果状态改成“查询 + 选中类型 + 类型化结果”，并用渲染器按类型展示

当前 `SearchUiState` 只有 `HOT / SUGGEST / RESULT` 三种页面模式，`RESULT` 下面承载的仍是单一结果列表。本次会把结果态升级为：
- 当前查询 `query`
- 当前选中结果类型 `selectedResultType`
- 可切换的结果类型集合 `availableResultTypes`
- 类型化结果状态 `SearchResultUiState`

结果展示层则采用“按类型渲染”的组织方式：
- 搜索页保留统一的顶部搜索栏与结果容器；
- 结果区增加类型切换控件（首版可用水平 tab / chip row 承载），并在进入结果态后固定在结果区顶部；结果主体支持左右滑动切换当前结果类型，避免用户滚动长列表后还要回到最上方切换类型；
- 每个结果类型走自己的 item renderer，而不是再共享一张通用卡片。

建议与热搜依然维持当前链路，不强行和结果类型耦合；只有提交搜索或切换结果类型时，才按选中的 `SearchResultType` 请求对应 `/cloudsearch` 数据。

这里需要特别明确两层边界：
- **协议层类型注册** 必须以搜索接口文档定义为准，完整覆盖 `1: 单曲`、`10: 专辑`、`100: 歌手`、`1000: 歌单`、`1002: 用户`、`1004: MV`、`1006: 歌词`、`1009: 电台`、`1014: 视频`、`1018: 综合`、`2000: 声音`；
- **页面展示策略** 顶部结果类型栏直接覆盖文档定义的全部结果类型，并以横向滑动切换栏承载；常用类型可以放在前面，但不能再把“只显示 4 个 tab”当作首版限制。其余类型同样需要具备可切换入口、mapper 入口与详情关联目标，避免后面继续返工协议层。

这样做的原因是：
- `/cloudsearch` 的结果类型本身就是通过 `type` 参数驱动，页面状态必须把“当前看的是哪种类型”变成显式状态；文档中的 `1018` 综合搜索本质上也是一种单独的 `type`，并不等于接口会一次性返回可直接承载为多个独立 tab 的全量类型页。
- 只有把“选中类型”建成状态，后续详情页关联、类型缓存、类型结果回切才会自然。
- 渲染器分离后，后续新增类型或调视觉只会落在局部，不会不断污染通用列表。

备选方案：
- **方案 A：一次查询把多种类型全拉下来并混排。** 信息密度会很高，但接口成本、状态复杂度和首版实现代价都更大。
- **方案 B：继续只做歌曲结果，等以后再扩。** 与当前 change 目标相违背。

因此首版选择“单次只展示一个选中类型，但页面内通过横向类型栏与 pager 可切换任意文档类型”的模式。为了保证长类型栏在实际使用中仍然可控，顶部横向类型栏在选中项变化时需要自动滚动，使当前选中类型尽量保持在可视区域中部，而不是长期停留在边缘位置。

补充约束：热搜列表继续使用 repository 内缓存以减少重复请求，但搜索结果本身不做 repository 级持久缓存。对于同一查询词，结果区需要采用接近 ViewPager 的页状态语义：某个结果类型页首次进入时请求一次，随后在同一次查询会话内切回该类型时直接复用已加载页状态，而不是再次白屏重拉；只有查询词变化、用户显式刷新，或首次进入尚未加载的类型页时，才重新请求对应类型结果。

### 6. cookie / csrf 自动透传链路保持不变，搜索类型扩展不能绕开统一网络约定

无论搜索 module 是否拆分，`/search/hot/detail`、`/search/suggest`、`/cloudsearch` 仍然继续通过 `JsonHttpClient` 的 `requiresAuth = true` 机制自动透传 cookie / csrf。类型扩展只影响查询参数、mapper 与 UI，不重新定义登录态处理逻辑。

这样做的原因是：
- 用户前面已经明确强调“大部分接口都要带上 cookie 配置”，这条链路已经在现有实现里跑通，不能因为 module 拆分重新走散。
- 搜索 module 真正需要独立的是业务流和类型建模，而不是重新复制一份网络认证体系。

备选方案：
- **方案 A：在搜索 module 内手工拼接 cookie。** 容易与现有用户登录态存储脱节。
- **方案 B：只有部分结果类型请求带鉴权。** 会让行为不一致，也不符合统一约定。

因此本次明确保持统一的 `requiresAuth` 协议不变。

## Risks / Trade-offs

- **[后端不同类型返回字段差异较大]** → 通过类型枚举 + 分类型 mapper 收敛解析逻辑，并为每个类型补 mapper 测试，避免“一个 mapper 吞所有类型”。
- **[首版支持的类型范围不够或过多]** → 先按产品当前明确需要的主要类型起步，同时让类型注册与 renderer 可扩展，避免一次性铺太大。
- **[module 拆分后依赖注入变复杂]** → 维持 app 作为组合根，只把搜索实现迁出去，不在这轮顺手重构所有基础设施。
- **[结果模型过度抽象导致开发成本上升]** → 保持统一基类 + 少量稳定公共字段，其余由分类型模型承载，不强求所有类型完全同构。
- **[未来详情页协议变化]** → route target 只承载稳定的 `type/id/basic display info`，避免现在就把详情页参数想得过重。
- **[搜索类型切换引起重复请求或状态跳动]** → 只在有非空查询时触发类型切换请求，并保留当前查询文案与上一次提交状态，减少闪烁。

## Migration Plan

1. 新增 `:feature-search` module，并在 `settings.gradle.kts`、对应 `build.gradle.kts` 中接入 Compose、Lifecycle 与 `:network-core` 依赖。
2. 将当前 `app/src/main/java/com/wxy/playerlite/feature/search/` 下的搜索实现迁入新 module，同时去掉 `SearchViewModel` 对 `AppContainer` 的直接依赖。
3. 引入 `SearchResultType`、类型化结果模型、route target 与分类型 mapper，保持热搜、建议、历史搜索与 cookie / csrf 透传能力不回退。
4. 改造搜索页结果区状态，使其支持结果类型切换与分类型 renderer。
5. 在 `app` 中补搜索依赖装配、入口接线与未来详情导航的宿主回调；如果迁移中出现严重问题，可先保留 `app` 侧旧入口和单类型结果实现作为临时回滚点。

## Open Questions

- 首批结果类型展示顺序是否固定为“单曲 / 歌手 / 专辑 / 歌单”，还是需要按产品优先级调整。
- 结果类型切换控件首版更适合做成 tab 还是 chip row；这属于交互呈现问题，不影响本次类型建模方案。
- 未来详情页是否全部由宿主 `app` 导航接线，还是后续会形成统一的 feature 路由协议；本次先以宿主接线为前提。
