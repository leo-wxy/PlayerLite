## Why

当前播放器只有竖屏主布局，手机横过来时没有专门的横屏承载，也缺少页面内的方向切换入口，导致横屏播放体验既不沉浸也不稳定。

这次变更需要把横屏播放器作为一等体验补齐，并为后续更强视觉稿预留稳定的布局、方向状态和局部倒影特效骨架。

## What Changes

- 新增横屏播放器体验能力，定义播放器的方向模式、横屏主视觉布局和局部水波倒影效果。
- 修改独立播放器页要求，让 `PlayerActivity` 负责页面内的方向模式控制，并在退出播放器后恢复 app 其他页面的默认方向行为。
- 修改播放展开页要求，让现有播放器在横屏下切换到独立布局分支，同时保持歌曲/歌词、播放进度和更多操作等上下文连续。

## Capabilities

### New Capabilities
- `landscape-player-experience`: 定义播放器的自动/锁定方向模式、横屏主视觉布局和局部倒影氛围效果。

### Modified Capabilities
- `player-expanded-page`: 为现有播放展开页补充横屏布局分支、横竖屏状态连续性和横屏氛围视觉要求。
- `player-activity-shell`: 为独立 `PlayerActivity` 补充页面内方向模式控制和返回恢复语义。

## Impact

- `app/src/main/java/com/wxy/playerlite/feature/player/PlayerActivity.kt`
- `app/src/main/java/com/wxy/playerlite/feature/player/PlayerViewModel.kt`
- `app/src/main/java/com/wxy/playerlite/feature/player/runtime/PlayerRuntime.kt`
- `feature-player/src/main/java/com/wxy/playerlite/feature/player/model/PlayerUiState.kt`
- `feature-player/src/main/java/com/wxy/playerlite/feature/player/ui/PlayerScreen*.kt`
- `app/src/test/java/com/wxy/playerlite/feature/player/**`
- 不引入新的远程 API；视觉特效基于现有 Compose/UI 能力实现，并对低版本保持无 shader 依赖的基础路径。
