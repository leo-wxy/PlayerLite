## Why

歌单、歌手、专辑详情页的播放入口和元数据回填语义，虽然依赖共享契约与在线播放链路，但它们本身是一条完整的业务能力：详情页发起播放、替换当前队列、指定激活项、补动态信息、触发播放量更新。把这条链独立成 change，能让详情页体验、播放业务和 UI 行为的回归边界更清楚。

## What Changes

- 新增统一的详情页播放网关，承接歌单、歌手、专辑详情页的“播放全部”和点歌播放
- 详情页以当前上下文整体替换当前播放列表，并以目标 index 指定激活项
- 支持先凭 `songId` 立即建立播放队列并开始播放，再后台分页请求 `/song/detail` 异步补齐封面、标题、歌手、专辑与时长
- 歌单详情页独立加载动态信息，并在真实播放开始后更新播放量
- 专辑详情页补专辑封面兜底，保证播放页主视觉信息不丢失

## Capabilities

### Modified Capabilities
- `playlist-detail-page`: 歌单详情页播放入口、动态信息与播放量更新
- `artist-detail-page`: 歌手详情页热门歌曲整体播放入口
- `album-detail-page`: 专辑详情页播放入口与封面兜底
- `playlist-management`: 外部详情上下文替换当前播放列表

## Impact

- `PlaylistDetailViewModel` / `ArtistDetailViewModel` / `AlbumDetailViewModel`
- 详情页播放网关、播放列表替换逻辑与异步 enrichment 接线
- 详情页相关 Robolectric / ViewModel 测试
