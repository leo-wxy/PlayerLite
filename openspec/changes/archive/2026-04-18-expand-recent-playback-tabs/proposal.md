## Why

当前“最近播放”只承接歌曲列表，仓库里虽然已经接了 `/record/recent/song`，但用户中心入口语义是“最近播放”而不是“最近播放歌曲”。同时，服务端已经提供最近播放的多种内容接口，现有页面无法体现这些内容类型，也无法验证这些接口在真实账号下的返回形态。

这次变更先把最近播放扩成多类型标签页，并把各类型最近播放真实渲染出来。首版只解决“看得到”与“切得动”，不在同一条变更里强行补齐视频、声音、播客等尚无成熟承接页的点击功能。

## What Changes

- 把用户中心的“最近播放”从单一歌曲页扩展为多类型最近播放总页
- 新增 6 个最近播放标签页：歌曲、视频、声音、歌单、专辑、播客
- 每个标签页分别请求对应的最近播放接口，默认最多展示 100 条
- 页面样式整体参考搜索结果页的列表骨架，统一行高、封面、文字层级和空态/错误态表达
- 仅对已有成熟承接能力的内容保留现有交互；新增的多类型标签首版只做渲染，不新增详情或播放跳转

## Capabilities

### New Capabilities
- `recent-playback-multi-tabs`: 扩展最近播放页为多类型标签页，统一承接最近播放内容。

### Modified Capabilities
- `user-center-tab-shell`: 更新最近播放入口与页面行为，支持多类型最近播放。

## Impact

- 影响用户中心“最近播放”入口和最近播放页的 ViewModel / repository / UI state
- 需要扩展用户中心远端数据源，接入 `/record/recent/video`、`/record/recent/voice`、`/record/recent/playlist`、`/record/recent/album`、`/record/recent/dj`
- 需要新增最近播放多类型的 UI model、tab state、定向测试和主 spec 更新
