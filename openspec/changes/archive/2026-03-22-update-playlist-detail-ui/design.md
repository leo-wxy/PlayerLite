## Context

- 当前歌单详情页已具备完整数据链路与核心交互：头部信息、动态信息、分页歌曲列表、播放全部与点歌播放、歌单简介查看。
- 主要实现位置：
  - UI：`app/src/main/java/com/wxy/playerlite/feature/playlist/PlaylistDetailActivity.kt`
  - 状态：`PlaylistDetailViewModel`（header/tracks/dynamic 三段独立状态）
  - 数据：`PlaylistDetailRepository`（`/playlist/detail`、`/playlist/track/all`、`/playlist/detail/dynamic`、`/playlist/update/playcount`）
- 当前歌单详情页虽已使用 `MusicDetailScaffold`，但整体仍更接近“渐变 hero + 固定返回按钮 + LazyColumn body”的轻量详情页形态；与专辑/歌手详情页已验证的 detail chrome（沉浸式 hero + `DetailBottomScrim()` + overlay collapsing top bar + 状态栏适配）存在明显割裂。
- 用户明确期望“歌单详情风格参考专辑详情”，因此本次变更需要把歌单详情页纳入同一套 detail shell 视觉语言中：hero 背景绘制至状态栏下方、滚动折叠时顶栏渐显标题、状态栏图标明暗自动适配。
- 约束与边界（来自 proposal）：
  - 仅做 UI/交互层刷新，不新增或调整歌单相关网络接口。
  - 不改变播放语义与播放量上报时机（播放成功后才更新 playcount，并刷新 dynamic）。
  - 保持加载态/错误态为“分区承载”，避免把局部失败升级为整页白屏回退。

## Goals / Non-Goals

**Goals:**

- 对齐专辑/歌手详情页的 detail shell：hero 绘制到状态栏下方，使用 overlay collapsing top bar（返回/更多/标题渐显），状态栏颜色与图标明暗随折叠进度过渡。
- Hero 信息层级更贴近专辑详情页：大标题 + 次级信息（作者/歌曲数）+ “播放全部”入口置于 hero 内；歌单简介不再放在 hero 中，统一通过“简介”tab 展示。
- 动态信息区域对齐专辑详情页的呈现方式：hero 下方展示轻量 card 统计区（评论/收藏/播放），失败/空态为局部承载，不阻断主体内容。
- 歌曲列表条目样式对齐专辑详情页：采用圆角 Card 行样式（序号 + 标题 + 歌手/专辑 + 时长），保持分页加载与 footer 语义不变。
- 尽量保持现有测试 tag 与交互入口稳定，降低 UI 回归成本。

**Non-Goals:**

- 不引入新的播放列表语义、队列管理逻辑或跨模块架构调整（例如抽离新模块、引入新的状态机）。
- 不新增“收藏/取消收藏歌单”等写操作能力（当前仅展示 `subscribed` 状态）。
- 不新增或调整网络接口、鉴权链路、数据模型映射。
- 不扩展为专辑/歌手详情那种“多 Tab（3+）+ 复杂内容页”的结构（歌单仅实现“歌曲/简介”双 Tab）。
- 不实现 top bar “更多”按钮的实际功能（仅作为 chrome 对齐的占位入口）。

## Decisions

### 1) Scaffold 策略：引入与专辑/歌手一致的 Detail Shell（仍基于 `MusicDetailScaffold`）

**Decision:** 采用与 `AlbumDetailShell` / `ArtistDetailShell` 同款的“Box + MusicDetailScaffold + overlay collapsing top bar”结构实现歌单详情页：hero 全幅贴边、绘制至状态栏下方、隐藏 scaffold 默认返回按钮，返回入口由 overlay top bar 提供；并在正文区引入与专辑同款的 Tab + Pager 结构来展示“歌曲/简介”两块内容。

**Rationale:**

- “参考专辑详情”的核心差异不在列表密度，而在 detail chrome：沉浸式 hero、底部 scrim、折叠顶栏与状态栏适配是一套完整能力，零散调整 padding/颜色很难达成一致性。
- 复用项目内已验证的 album/artist shell 模式，可控地提升一致性；同时将 tab 结构限定为“歌曲/简介”两页，并复用专辑页已验证的 handoff 连接，避免自研复杂 nested scroll 细节。

**Alternatives considered:**

- 继续沿用旧的 `MusicDetailScaffold` 默认参数（固定返回按钮 + hero padding）：实现简单，但无法满足“像专辑详情”的整体观感与 chrome 一致性。

### 2) Hero：封面全幅主视觉 + `DetailBottomScrim()` + 播放入口上移到 Hero

**Decision:** 参考专辑详情页的 hero 结构：封面图作为全幅主视觉背景（aspectRatio 1:1），叠加 `DetailBottomScrim()` 保证文字可读性；标题、作者、歌曲数与“播放全部”入口聚合在 hero 底部信息区，并随滚动折叠进度做渐隐/上移动效。

**Implementation notes:**

- 缺图时使用稳定占位（首字母 + 主题色半透明底）。
- 作者与歌曲数以更轻量的文本行承载，并保留原有 `testTag`（`playlist_creator_meta`、`playlist_track_count_meta`）。
- “播放全部”按钮放入 hero（对齐专辑 hero 交互入口），避免因 hero 高度变化导致按钮初屏不可见。

### 3) Tab：歌曲列表与简介分离展示

**Decision:** 参考专辑详情页，将“歌曲列表”和“歌单简介”通过双 Tab 展示，并使用 `HorizontalPager` 支持横滑切换；两页均使用内层 `LazyColumn` 承载内容，并通过 `rememberDetailVerticalScrollHandoffConnection` 将 vertical scroll 手势优先用于折叠外层 hero，再交给内层列表滚动。

**Rationale:**

- 歌单简介通常较长，放在弹窗或 hero 内都会破坏信息层级；Tab 能更清晰地分区展示。
- 复用专辑页已验证的 pager + handoff 模式，避免自行实现复杂 nested scroll 细节。

**Implementation notes:**

- 选项卡固定为两项：`歌曲`、`简介`。
- hero 不再承载歌单简介入口；简介仅通过 “简介”tab 展示，减少头部信息噪声并保持层级稳定。
- “歌曲”页继续承载分页 footer；“简介”页使用独立 `LazyColumn`，保证长文本可滚动且与外层 hero 折叠手势协调。

### 4) 动态信息：Hero 下方动态 meta card（对齐专辑详情样式）

**Decision:** 将 dynamic 统计信息放置在 hero 下方，使用与专辑详情页一致的 card 风格（圆角、surface 背景、三列指标），并保持 loading/error 为局部承载（不会影响头部与歌曲列表）。

**Rationale:**

- 专辑详情页已验证 “hero + dynamic card” 的信息节奏：动态信息补齐而不抢占主视觉。
- dynamic 失败时保持局部错误卡片，继续满足“分区承载”的容错要求。

### 5) 歌曲列表：对齐专辑 `AlbumTrackRowCard` 的卡片行样式

**Decision:** 歌曲条目采用与专辑详情页一致的圆角 Card 行样式（序号 + 标题 + “歌手 · 专辑” + 时长），并保留 `playlist_track_<trackId>` tag 与点击回调语义不变。

**Implementation notes:**

- 行内信息保持扫读：标题单行、省略；副标题单行、“歌手 · 专辑”；时长右侧对齐。
- 外层 padding/圆角对齐专辑：水平 16dp，垂直 4dp，圆角 22dp。

### 6) 共享组件抽取策略：先局部实现，必要时再上收

**Decision:** 优先在 `feature/playlist` 内实现 UI 刷新，只有当组件在专辑/歌手/歌单三处明显复用时，才上收至 `feature/detail`（例如通用的“详情页紧凑行条目”“统计条”组件）。

**Rationale:**

- 减少本轮变更波及面，避免为了“看起来更统一”而引入跨页面的大范围重构。
- 后续若确实需要统一，可基于这次实现沉淀出更稳的共享组件边界。

## Risks / Trade-offs

- [Risk] UI 布局调整导致现有 UI 测试依赖的 `testTag` 或层级发生变化。  
  Mitigation: 优先保持现有 tag 不变；确需调整时同步更新测试并在 tasks 中显式列出。
- [Risk] 引入沉浸式状态栏与折叠顶栏后，状态栏图标明暗切换可能出现闪烁或对比度不足。  
  Mitigation: 复用 album/artist 的混色与 `shouldUseLightStatusBarContent` 判定逻辑，并以折叠进度驱动渐变过渡。
- [Risk] 更紧凑的行样式可能造成信息截断（标题/副标题过长）。  
  Mitigation: 使用 `maxLines + ellipsis`；简介正文迁移到独立 tab 内完整展示，避免依赖弹窗补充阅读。
- [Risk] 头部背景取色导致对比度不稳定（白字在浅色背景上可读性下降）。  
  Mitigation: 继续使用现有 brush 策略，并在 hero 内容区域使用稳定的 scrim/透明度策略；封面缺失时回退默认渐变。
- [Trade-off] 引入 Tab + Pager + nested scroll handoff 会增加实现复杂度与回归面（滚动/吸顶/状态栏过渡）。  
  Mitigation: 复用专辑页已验证的 handoff 连接实现，并保持关键 tag/交互入口稳定；通过 Robolectric 测试覆盖核心结构。

## Migration Plan

- 无数据迁移与接口变更，仅 UI 层修改。
- 发布与回滚：随 App 版本发布；如出现回归，可通过回退对应 UI 提交快速恢复旧样式。

## Open Questions

- 歌单 top bar 右侧“更多”未来计划承载什么入口（分享/评论/收藏等），以及是否需要接入真实行为？
- dynamic 空态（comment/play/subscribed 全 0）是展示占位还是直接隐藏更合适？
- 未来是否需要为长简介增加额外快捷入口（例如吸顶栏内二级操作），还是继续保持“仅通过 Tab 查看”的单一入口？
