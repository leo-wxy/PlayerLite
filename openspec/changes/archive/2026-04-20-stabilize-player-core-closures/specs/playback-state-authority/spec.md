## ADDED Requirements

### Requirement: 播放目标切换使用 generation 隔离旧事件
系统 SHALL 为每次准备、切歌、seek 后重准备和音质切换建立可识别的当前播放目标 generation；来自旧 generation 的 completion、failed、ready 或 position 事件 MUST 不得覆盖当前播放目标状态。

#### Scenario: 旧 source completion 不覆盖新曲目
- **WHEN** 用户从当前歌曲切换到下一首
- **AND** 旧歌曲的 playback source 在释放过程中回调 completion
- **THEN** 系统忽略该旧 generation 的 completion
- **AND** 当前新歌曲的播放状态、当前项和队列激活项保持不变

#### Scenario: 重复 prepare 只允许最新目标发布状态
- **WHEN** 同一首歌因为 URL 刷新、音质切换或重试触发多次准备
- **THEN** 系统只允许最新 generation 的 ready / failed 结果更新远端播放快照
- **AND** 旧 generation 的结果不会让 UI 回跳到旧进度、旧标题或错误失败态

### Requirement: 可恢复播放错误通过远端快照显式投影
系统 SHALL 将缓冲、重试中和最终失败态作为远端播放快照中的可解释状态投影给 app，而不是继续用 Playing、Paused 或空闲态伪装这些过渡状态。

#### Scenario: 重试中状态冻结可见进度
- **WHEN** 当前在线播放进入资源刷新或重连重试
- **THEN** 远端快照明确表示当前处于 buffering / retrying 类状态
- **AND** app 的可见进度在新数据到达前保持冻结

#### Scenario: 最终失败不伪装成暂停
- **WHEN** 当前播放目标已经确认不可恢复
- **THEN** 远端快照明确表示失败原因或失败分类
- **AND** app 不会把该状态展示成用户主动暂停
