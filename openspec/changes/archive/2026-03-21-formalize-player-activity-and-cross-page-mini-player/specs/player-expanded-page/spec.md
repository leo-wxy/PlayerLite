## MODIFIED Requirements

### Requirement: MediaSession 内容入口直接进入播放器展开页
系统 SHALL 让通知或其他 `MediaSession` 内容入口在唤起 app 后直接进入由独立 `PlayerActivity` 承载的播放器展开页，而不是只回到首页壳层或停在无关页面。

#### Scenario: 从系统媒体入口点击后直接回到独立播放器页
- **WHEN** 用户从通知、锁屏卡片或其他系统媒体入口点击当前播放项
- **THEN** app 被拉起后直接进入由独立 `PlayerActivity` 承载的播放器展开页
- **AND** 不会只回到首页或停在无关 tab
