## MODIFIED Requirements

### Requirement: MediaSession 状态与元数据保持同步
系统 MUST 在播放状态、当前媒体项变化或当前歌词命中行变化时同步更新 `MediaSession` 的 `PlaybackState` 与 `MediaMetadata`，确保系统通知、锁屏卡片和其他媒体入口看到的当前播放标题与应用内当前歌词展示一致。

#### Scenario: 当前媒体项变化同步元数据
- **WHEN** 当前播放媒体切换到新条目
- **THEN** `MediaSession` 更新标题、艺术家、时长等元数据供系统界面展示
- **AND** 次级信息优先展示为“歌名 - 歌手”

#### Scenario: 当前歌词命中行变化时刷新标题位
- **WHEN** 当前歌曲存在已解析歌词
- **AND** 当前播放进度命中新的歌词行
- **THEN** `MediaSession` 将标题位刷新为当前歌词时间对应的一句歌词
- **AND** 次级信息保持为“歌名 - 歌手”

#### Scenario: 歌词不可用时回退到歌曲标题
- **WHEN** 当前歌曲没有可用歌词、歌词仍在加载或歌词请求失败
- **THEN** `MediaSession` 标题位回退为当前歌曲标题
- **AND** 次级信息仍展示为“歌名 - 歌手”或其稳定缺省值

#### Scenario: app 内业务态不被展示标题反向污染
- **WHEN** 系统为了 `MediaSession` 展示把标题位刷新为当前歌词行
- **THEN** app 内恢复当前播放歌曲业务态时仍可读取原始歌曲标题与歌手
- **AND** 不会把当前歌词行误当成当前歌曲标题写回播放器主状态
