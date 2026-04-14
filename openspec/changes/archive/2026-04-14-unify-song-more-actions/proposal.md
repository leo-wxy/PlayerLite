## Why

首页歌曲、本地歌曲和每日推荐歌曲已经接入统一歌曲对象与歌曲详情页，但次级入口仍然分散：首页歌曲更多缺少“查看歌曲详情”，本地歌曲仍然使用单独的详情按钮而不是统一的更多入口，每日推荐歌曲则仍然只有整行播放而缺少次级动作。现在需要把这些歌曲入口的次级动作收口成一致的“更多”能力，避免同一类歌曲对象在不同页面表现出完全不同的交互心智。

## What Changes

- 新增统一的单曲“更多”动作能力，定义最小动作集合与按歌曲来源降级的规则。
- 修改首页歌曲推荐区块的更多菜单，补齐“查看歌曲详情”并明确动作顺序。
- 修改本地歌曲页的次级入口，从单独详情按钮收口为统一的三点更多入口。
- 修改每日推荐歌曲列表，在不改变整行播放语义的前提下补齐统一三点更多入口。
- 明确本次不调整搜索结果页交互；搜索单曲继续保持“整行播放、三点直达详情”。

## Capabilities

### New Capabilities
- `song-overflow-actions`: 定义单曲更多入口的最小动作集合、动作顺序与在线/本地歌曲的降级规则。

### Modified Capabilities
- `homepage-discovery-content`: 首页歌曲推荐区块的更多菜单需要补充“查看歌曲详情”并与统一动作能力保持一致。
- `local-music-library-page`: 本地歌曲页需要将单独详情入口改成统一更多入口，并按本地歌曲能力降级显示动作。
- `daily-recommended-songs`: 每日推荐歌曲列表需要补充统一更多入口，并在不改变主点击播放语义的前提下承载次级动作。

## Impact

- `feature-home` 的首页歌曲菜单建模与点击 bridge
- `app` 中的本地歌曲列表 UI 与本地歌曲更多动作接线
- `app` 中的每日推荐歌曲列表 UI 与在线歌曲更多动作接线
- 相关 Robolectric / repository / route bridge 测试
- OpenSpec 主 specs：`homepage-discovery-content`、`local-music-library-page`、`daily-recommended-songs`
