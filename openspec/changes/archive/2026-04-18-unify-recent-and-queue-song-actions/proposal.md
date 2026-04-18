## Why

最近播放和播放队列仍然停留在两套旧的单曲交互里：最近播放的主点击与次级入口缺少清晰分工，播放队列则仍然把右侧入口绑定为“删除”，无法承接歌曲详情、歌手或专辑跳转。现在首页、本地歌曲和每日推荐已经统一到更稳定的单曲动作体系，需要把最近播放和播放队列一起收口，避免用户面对同一种歌曲对象时继续看到割裂的主次路径。

## What Changes

- 修改最近播放子页面，把主点击收口为“按当前最近播放列表直接播放”，并把右侧三点收口为直达歌曲详情的弱入口。
- 修改播放列表半浮层，把队列项右侧从单独删除按钮收口为队列语义的三点更多入口。
- 修改共享播放队列条目元数据，为在线歌曲队列项补齐稳定的 `albumId`，以支撑“查看专辑”动作。
- 明确本次不调整搜索结果页交互，也不把“下一首播放”强行带入当前播放队列项菜单。

## Capabilities

### New Capabilities

### Modified Capabilities
- `song-overflow-actions`: 明确来源列表主点击与次级入口分工，并补充当前播放队列使用队列语义的动作变体。
- `user-center-tab-shell`: 最近播放列表需要收口为“整行播放当前列表、三点直达歌曲详情”的交互，并保留稳定歌曲入口。
- `playlist-management`: 播放列表半浮层需要把队列项右侧入口改为三点更多，并承载歌曲详情、歌手、专辑和移除队列动作。
- `playable-contract-and-queue-metadata`: 共享队列元数据需要补齐稳定 `albumId`，以支撑播放队列里的专辑跳转。

## Impact

- `app` 中的最近播放数据建模、列表 UI 和动作接线
- `feature-player` 中的播放列表半浮层 UI 与更多菜单承载
- `playback-contract` 中的 `PlaylistItem` 共享元数据字段
- 在线歌曲队列项 mapper 与最近播放 mapper
- 相关 Robolectric / repository / route bridge / playlist sheet 测试
