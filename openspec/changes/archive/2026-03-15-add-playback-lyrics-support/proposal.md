## Why

当前播放页仍停留在“歌词待补充”占位态，既无法展示随播放进度变化的歌词，也没有稳定的歌词获取与保留策略；与此同时，首页底部 minibar 仍偏大，在已有播放上下文下占用过多纵向空间。现在补齐歌词能力并同步收紧 minibar，可以让在线播放体验更接近完整音乐播放器，而不是长期停留在半成品状态。

## What Changes

- 在播放展开页引入歌词相关承载，替代现有“歌词待补充”占位文案，为当前歌曲提供歌词 tab、按播放进度同步的当前行歌词展示，以及与播放信息协同的歌词阅读体验。
- 在播放页歌手信息附近补充当前时间对应歌词的即时展示，让用户不进入完整歌词视图也能看到当前播放到哪一句。
- 接入无需登录的 `/lyric?id=<songId>` 歌词接口，为具备在线 `songId` 的歌曲拉取歌词，并补齐失败、缺失歌词和非在线歌曲的降级态。
- 完善歌词下载与本地保留策略，限制最多仅保留 100 首歌词资源，避免缓存无限增长导致资源堆积。
- 收紧首页底部 minibar 的整体尺寸和内部控件密度，在不破坏现有主体点击、播放控制和播放列表入口的前提下减少视觉与空间占用。
- 让首页 minibar 的主标题位与系统 `MediaSession` 标题位都按当前歌词时间展示当前一句歌词，同时把次级信息统一为“歌名 - 歌手”，保证应用内外的当前播放语义一致。
- 收敛完整歌词页当前命中行的强调强度，保留自动滚动和高亮识别，但避免过大的字号跳变。

## Capabilities

### New Capabilities
- `playback-lyrics`: 定义歌词获取、解析、按时间同步展示、下载保留上限与缺失歌词降级行为。

### Modified Capabilities
- `player-expanded-page`: 播放页从静态歌词占位升级为可承载歌词 tab 与当前行歌词展示的展开页信息结构。
- `user-center-tab-shell`: 首页底部迷你播放条需要收紧高度与控件尺寸，同时保持当前播放信息与核心操作区的稳定排布。
- `media-session-integration`: 系统媒体卡片与通知需要把当前歌词时间对应的一句歌词投影到标题位，并把次级信息收敛为“歌名 - 歌手”。

## Impact

- Affected code: `app/src/main/java/com/wxy/playerlite/feature/player/ui/PlayerScreen.kt`、播放页状态与数据加载链路、`app/src/main/java/com/wxy/playerlite/feature/main/MainShellScreen.kt`、`playback-client` / `playback-contract` / `playback-service` 中的 `MediaSession` 元数据投影链路。
- Affected APIs: 新增调用 `/lyric?id=<songId>` 获取歌词，不要求登录。
- Affected systems: 在线歌曲元数据到歌词数据的映射、歌词缓存/下载保留策略、播放页与首页 minibar 的 UI 验证用例，以及系统媒体通知/锁屏卡片的动态标题展示。
