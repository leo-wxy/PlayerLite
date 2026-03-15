## Why

播放页 UI、MediaSession 唤起和播放中交互被夹在一个超重 change 里，已经很难单独评审。最近围绕顶栏、封面、沉浸式背景、播控比例、buffering 状态和小屏适配的调整，本身就值得作为独立 change 收口。

## What Changes

- 播放页改成单条真实顶栏，返回 / 标题 / 分享位于同一安全区基线
- 封面采用顶部正方形主视觉，背景复用当前封面结果取色并保持深色沉浸氛围，而不是退回纯黑底
- 歌名 / 歌手 / “歌词待补充” 信息区固定在底部内容层
- 进度条改成更接近 progressbar 的细轨道样式，播控区整体更靠下，中间主播放键明显大于四个侧边按钮，且所有按钮都拥有稳定背景
- buffering / 状态提示固定锚在封面右下角，避免底部信息区抖动
- MediaSession 点击直接回到播放器展开页，播放页保持沉浸式与小屏可用性

## Capabilities

### New Capabilities
- `player-expanded-page`: 播放器展开页的视觉、布局与交互规则

## Impact

- `PlayerScreen` / `PlayerExpandedScreen` / `PlaybackControls`
- `MainActivity` 中的播放器展开态接线
- 播放页 Robolectric 测试与 MediaSession 唤起相关回归
