## Context

当前播放器相关约束分散在多个主 spec 里：

- [`user-center-tab-shell`](/Users/wxy/Projects/player-lite/openspec/specs/user-center-tab-shell/spec.md) 仍要求“首页概览态与播放展开态属于同一 Home 域”。
- [`player-expanded-page`](/Users/wxy/Projects/player-lite/openspec/specs/player-expanded-page/spec.md) 只定义了播放页本身的视觉和基础交互，没有把宿主层、启动契约和通知入口单独建模。
- [`playlist-management`](/Users/wxy/Projects/player-lite/openspec/specs/playlist-management/spec.md) 约束了播放列表 sheet 与详情页播放上下文替换，但没有定义首页/详情页的列表入口如何统一落到独立播放器宿主。
- [`playback-lyrics`](/Users/wxy/Projects/player-lite/openspec/specs/playback-lyrics/spec.md) 目前只明确了首页 `minibar` 与 `MediaSession` 复用歌词摘要，没有把详情页 `minibar` 纳入同一真源。

而代码已经开始收敛到另一套结构：

- [`PlayerActivity`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/player/PlayerActivity.kt) 已经成为完整播放器宿主，并承载播放器页内的播放列表 sheet。
- [`MainActivity`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/MainActivity.kt) 的首页 `minibar` 主体与列表按钮已经直接跳到 `PlayerActivity`。
- [`BasePlaybackDetailActivity`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/detail/BasePlaybackDetailActivity.kt) 与 [`DetailMiniPlayerBar`](/Users/wxy/Projects/player-lite/app/src/main/java/com/wxy/playerlite/feature/detail/DetailMiniPlayerBar.kt) 已经形成详情页共享底部 chrome。
- [`PlaybackLaunchRequest`](/Users/wxy/Projects/player-lite/playback-contract/src/main/java/com/wxy/playerlite/playback/model/PlaybackLaunchRequest.kt) 与 [`PlayerMediaSessionService`](/Users/wxy/Projects/player-lite/playback-service/src/main/java/com/wxy/playerlite/playback/process/PlayerMediaSessionService.kt) 已开始按独立 `PlayerActivity` 组织启动契约。

这次 change 的目标不是再实现一轮代码，而是给现有结构补回统一 spec 真源，避免主规范继续要求“Home 域播放器”，而实现已经转向“独立播放器 Activity + 跨页面 minibar chrome”。

## Goals / Non-Goals

**Goals:**

- 把完整播放器宿主正式定义为独立 `PlayerActivity`，并补齐首页入口、详情页入口与 `MediaSession` 内容入口的统一启动契约。
- 把专辑、歌手、歌单等播放感知详情页底部共享 `minibar` 的显示规则、点击行为和内容避让规则收敛成独立 capability。
- 保持首页 `minibar` 仍属于 Home 壳层的一部分，但把它与完整播放器 surface 的宿主职责彻底分开。
- 统一播放列表状态语义：首页播放列表入口进入独立播放器页并直接展开列表 sheet，详情页播放列表入口则在当前页共享 chrome 中直接展开同一份列表。
- 统一歌词摘要投影语义：首页 `minibar`、详情页 `minibar` 和系统 `MediaSession` 看到的是同一条当前歌词摘要回退规则。

**Non-Goals:**

- 不重新设计播放器页面的视觉层次、歌词页布局或播放列表视觉样式。
- 不改动播放队列替换、激活项切换、随机顺序、拖拽重排等底层播放规则。
- 不把所有页面都升级为播放感知页面；本次只覆盖当前已有的专辑、歌手、歌单详情页这类共享 detail chrome 的场景。
- 不在本次引入新的导航框架或跨 Activity 共享 View 宿主方案。

## Decisions

### Decision: 完整播放器只保留一个宿主，即独立 `PlayerActivity`

采用方案：

- 用新 capability `player-activity-shell` 单独定义 `PlayerActivity` 的职责边界。
- `PlayerActivity` 成为完整播放器 UI 与通知内容入口的唯一完整宿主。
- 首页只保留 `minibar` 入口；详情页保留 `minibar` 与当前页播放列表 sheet 的共享 chrome，不再承担完整播放器 surface 宿主职责。

这样做的原因：

- 当前最大的规范冲突来自“主 spec 还在要求 Home 域内展开播放器”，而实现已经被迫同时兼容 Home surface、详情页入口和通知入口。
- 把完整播放器收敛到单一 Activity，能让“打开播放器”“通知返回播放器”等核心行为落到同一契约上，同时不强迫详情页为了查看列表离开当前上下文。

备选方案：

- 备选 A：继续保留 `MainActivity` 内嵌播放器 surface，并让详情页本地再承载播放列表。缺点是需要维持多个宿主和多套返回/恢复语义。
- 备选 B：把播放器做成全局浮层。缺点是会显著放大 Activity、window inset 和返回栈复杂度，也与当前代码结构不符。

### Decision: 首页 `minibar` 继续属于 Home 壳层，但只作为播放器入口而不是播放器宿主

采用方案：

- 首页 `minibar` 仍由 `user-center-tab-shell` 负责定义其视觉和 Home 壳层位置。
- 其主体点击改为启动独立 `PlayerActivity`，而不是再进入 Home 域内展开态。
- 列表按钮也不在首页本地开 sheet，而是通过 launch request 打开 `PlayerActivity(openPlaylist = true)`。

这样做的原因：

- 首页 `minibar` 仍是 Home 概览态的一部分，视觉和布局不应从 Home 壳层中抽走。
- 但“完整播放器页”和“Home 壳层中的底部入口”不是同一职责，继续混在同一 capability 里会让 spec 和实现一起失真。

备选方案：

- 备选 A：把首页 `minibar` 一起抽成全局浮层。缺点是会破坏 Home 壳层现有页面节奏和主题关系。
- 备选 B：保留 Home 域内展开播放器。缺点是与当前独立 `PlayerActivity` 方向冲突，并继续制造详情页入口兼容问题。

### Decision: 非首页 `minibar` 用共享 detail chrome capability 定义，而不是塞回各详情页 capability

采用方案：

- 新建 `detail-playback-chrome` capability，统一描述播放感知详情页的底部 `minibar` 行为。
- 详情页本身只负责内容和上下文；底部 `minibar` 的显示条件、内容避让和交互规则由共享 capability 定义。
- 专辑、歌手、歌单详情页继续通过各自 capability 定义“详情页发起播放”，而不是在各 spec 里重复写一份底部 chrome 规则。

这样做的原因：

- 这三类页面的底部播放器 chrome 是横切关注点，不属于任何单页独有业务。
- 如果把同一套底部 `minibar` 规则分别写进 `album-detail-page` / `artist-detail-page` / `playlist-detail-page`，后续会再次出现三处漂移。

备选方案：

- 备选 A：在三个详情页 spec 各自追加非首页 `minibar` 规则。缺点是重复且容易失去一致性。
- 备选 B：完全不写 spec，只让实现共享基类。缺点就是当前已经暴露出来的“代码有了但真源缺失”。

### Decision: 首页与详情页共享同一份播放列表状态，但入口停留在各自最合适的宿主

采用方案：

- `PlaybackLaunchRequest` 作为播放器页启动契约真源，显式承载首页/通知等入口的 `openPlaylist` 与 `startPlayback` 等初始动作。
- 首页 `minibar` 与通知内容入口继续通过 launch contract 与 `PlayerActivity` 交互；详情页 `minibar` 的列表按钮在当前页共享 chrome 中直接展开本地 `PlaylistBottomSheet`。
- `playlist-management` spec 改为同时描述“播放器页如何展开列表”和“详情页如何在当前页访问同一份列表状态”。

这样做的原因：

- 如果强制所有入口都跳去播放器页再看列表，详情页会失去当前上下文，也会继续出现“点列表却先跳别处”的体验冲突。
- 将 launch request 只保留给确实要进入播放器页的入口，同时允许详情页在本地展开同一份列表，能把“查看列表”和“进入完整播放器”区分清楚，并保持实现可测试。

备选方案：

- 备选 A：首页和详情页都各自本地弹出列表。缺点是首页会重新落回 Home 壳层局部播放器语义，并放大宿主分裂。

### Decision: 当前歌词摘要的复用语义只保留一个真源

采用方案：

- `playback-lyrics` spec 统一约束“当前歌词摘要可被首页 `minibar`、详情页 `minibar` 和 `MediaSession` 复用”。
- 所有入口都使用同一回退规则：有当前命中歌词时展示歌词摘要；不可用时回退到当前歌曲标题。

这样做的原因：

- 当前实现已经在首页、详情页和通知链路里都需要这个投影；如果 spec 只写首页和通知，详情页就会再次变成“实现特判”。

备选方案：

- 备选 A：让详情页 `minibar` 自己定义歌词投影规则。缺点是会出现不同入口文案不一致。

## Risks / Trade-offs

- [风险] 主 spec 从“Home 域播放器”迁移到“独立 `PlayerActivity`”后，归档时会影响多份现有 capability 的含义
  → 缓解：这次 change 明确同时修改 `user-center-tab-shell`、`player-expanded-page`、`playlist-management` 和 `playback-lyrics`，不只补单点 spec。

- [风险] `detail-playback-chrome` 作为横切 capability 可能与各详情页既有播放 requirement 产生边界重叠
  → 缓解：detail page spec 继续只描述“如何发起详情上下文播放”，共享 capability 只描述底部 chrome、入口与内容避让。

- [风险] 规范补齐后，仍可能存在尚未同步清理的旧实现或死代码
  → 缓解：在 tasks 中单列死代码/旧入口清理项，并要求通过 launch contract 与 UI 回归测试验证。

- [风险] launch request 扩展为统一真源后，如果约束写得不清楚，后续会再次出现“openPlaylist 是否隐含 openPlayer”的歧义
  → 缓解：在 `player-activity-shell` 里明确初始动作语义和 Activity 唤起结果，而不是只在实现里约定。

## Migration Plan

- 第一步：新增 `player-activity-shell` 和 `detail-playback-chrome` 两个 capability 的 delta spec。
- 第二步：更新 `user-center-tab-shell`，移除“Home 域内播放器展开态”这一旧 requirement，改为首页提供独立播放器入口。
- 第三步：更新 `player-expanded-page`、`playlist-management` 和 `playback-lyrics`，让播放器宿主、播放列表入口与歌词摘要复用语义全部指向新结构。
- 第四步：按 tasks 补齐或校验 `MainActivity`、`PlayerActivity`、`BasePlaybackDetailActivity`、`PlaybackLaunchRequest` 与通知入口相关实现和测试。
- 回滚策略：若需要回退，只回退本 change 的 delta specs 与相关实现，让主规范重新回到旧的 Home 域播放器描述；但这会重新引入多宿主兼容成本。

## Open Questions

- 当前 detail chrome 是否只覆盖专辑、歌手、歌单详情页，还是未来会扩展到其他可播放详情页，主 spec 当前先按已知页面建模即可。
- 详情页 `minibar` 的视觉是否要严格与首页同皮复用，还是允许在共享交互和布局契约下做轻量样式差异；本 change 先规范行为和宿主，不细化视觉风格。
