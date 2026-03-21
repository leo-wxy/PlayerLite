## Why

当前主 spec 仍把播放展开页定义为 Home 域内的展开态，而代码已经开始演进到独立 `PlayerActivity`、首页/详情页共享 `minibar` 入口、播放列表在播放器页与详情页 chrome 之间共享展示状态的结构。继续让“首页壳层 spec”“详情页播放 spec”和“播放器页 spec”各自描述一部分，会导致非首页 `minibar`、播放列表入口、歌词投影和通知唤起行为缺少统一真源。

现在需要把“独立播放器 Activity + 跨页面 mini player chrome”补成一条独立 change，收敛播放器宿主、入口契约和共享播放上下文，避免后续继续在首页、详情页和播放器之间靠实现细节互相兼容。

## What Changes

- **BREAKING** 将播放展开页从“Home 域内展开态”重定义为独立 `PlayerActivity` 宿主，不再要求首页壳层内部承载完整播放器 surface。
- 新增独立播放器宿主能力，定义 `PlayerActivity` 作为完整播放器与通知内容入口的唯一完整承载位，并保留启动后默认展开播放器页内播放列表 sheet 的契约。
- 新增跨页面 detail mini player chrome 能力，定义专辑、歌手、歌单等播放感知详情页如何在底部复用共享 `minibar`，以及如何预留内容空间而不破坏正文滚动与吸顶。
- 修改首页 `minibar` 能力定义：首页仍展示 Home 域底部迷你播放条，但主体和播放列表入口统一跳转到独立 `PlayerActivity`。
- 修改播放列表管理能力：首页的播放列表入口通过 launch request 要求播放器页直接展开播放列表；详情页的播放列表入口则在当前页共享 chrome 内直接展开同一份播放列表。
- 修改歌词投影能力：首页 `minibar`、详情页 `minibar` 与系统 `MediaSession` 统一复用当前歌词摘要，并在歌词不可用时回退到歌曲标题。
- 修改 `MediaSession` 集成能力：系统通知、锁屏卡片与其他媒体入口继续复用同一条歌词摘要与标题回退语义，并保持与 app 内 mini player 展示一致。

## Capabilities

### New Capabilities
- `player-activity-shell`: 定义独立 `PlayerActivity` 的宿主职责、启动契约、通知/MediaSession 内容入口与播放器页内播放列表默认展开行为。
- `detail-playback-chrome`: 定义播放感知详情页的共享底部 `minibar` chrome、内容避让、主体点击与当前页播放列表入口行为。

### Modified Capabilities
- `user-center-tab-shell`: 首页 `minibar` 继续挂在 Home 壳层内，但不再要求进入 Home 域内播放器展开态，而是跳转到独立 `PlayerActivity`。
- `player-expanded-page`: 播放页视觉与交互继续成立，但宿主从 Home 域内展开 surface 调整为独立播放器 Activity。
- `playlist-management`: 播放列表半浮层在独立播放器页与详情页共享 chrome 中复用同一份状态；首页入口通过打开播放器页访问，详情页入口留在当前页访问。
- `playback-lyrics`: 当前歌词摘要的复用范围从“首页 minibar + MediaSession”扩展到“首页 minibar + 详情页 minibar + MediaSession”。
- `media-session-integration`: 系统媒体标题位与内容入口继续跟随当前歌词摘要和独立播放器宿主语义同步。

## Impact

- `MainActivity`、`PlayerActivity`、`BasePlaybackDetailActivity`、`DetailMiniPlayerBar`
- `PlaybackLaunchRequest`、`PlayerMediaSessionService`
- 首页 `minibar`、详情页底部 chrome、播放器页和播放列表相关 Robolectric / launch contract 测试
- `openspec/specs/user-center-tab-shell/spec.md`
- `openspec/specs/player-expanded-page/spec.md`
- `openspec/specs/playlist-management/spec.md`
- `openspec/specs/playback-lyrics/spec.md`
- `openspec/specs/media-session-integration/spec.md`
