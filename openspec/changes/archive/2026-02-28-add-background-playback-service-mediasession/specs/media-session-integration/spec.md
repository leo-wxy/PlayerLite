## ADDED Requirements

### Requirement: 服务内创建并维护唯一 MediaSession
系统 SHALL 在 `MediaSessionService` 内创建并维护唯一 `MediaSession` 实例，用于承载系统级媒体控制与状态分发。

#### Scenario: 服务启动时初始化 MediaSession
- **WHEN** `MediaSessionService` 完成启动初始化
- **THEN** 服务创建可连接的 `MediaSession` 并对外暴露会话令牌

#### Scenario: 服务销毁时释放 MediaSession
- **WHEN** 服务进入销毁流程
- **THEN** 服务释放 `MediaSession` 资源并停止对外分发会话状态

### Requirement: 传输控制动作映射到播放器命令
系统 SHALL 将 `MediaSession` 接收到的标准传输控制动作（播放、暂停、上一首、下一首）映射到播放器等价命令。

#### Scenario: 系统媒体中心触发播放或暂停
- **WHEN** 用户在系统媒体中心触发播放或暂停动作
- **THEN** 服务执行对应播放器命令并返回一致的播放状态

#### Scenario: 外部控制触发切歌动作
- **WHEN** 用户通过耳机、蓝牙设备或通知栏触发上一首或下一首
- **THEN** 服务执行对应切歌命令并切换到正确的队列项

### Requirement: MediaSession 状态与元数据保持同步
系统 MUST 在播放状态或当前媒体项变化时同步更新 `MediaSession` 的 `PlaybackState` 与 `MediaMetadata`。

#### Scenario: 播放状态变化同步到会话
- **WHEN** 播放器在播放、暂停、缓冲、结束等状态间切换
- **THEN** `MediaSession` 向系统发布与实际状态一致的播放状态

#### Scenario: 当前媒体项变化同步元数据
- **WHEN** 当前播放媒体切换到新条目
- **THEN** `MediaSession` 更新标题、艺术家、时长等元数据供系统界面展示

### Requirement: 会话可用操作与队列边界一致
系统 SHALL 依据当前队列边界与播放状态动态暴露可执行媒体操作，避免系统端出现不可执行但可点击的控制项。

#### Scenario: 首项时限制上一首操作
- **WHEN** 当前媒体已是队列首项
- **THEN** `MediaSession` 不暴露可执行的上一首操作

#### Scenario: 末项时限制下一首操作
- **WHEN** 当前媒体已是队列末项
- **THEN** `MediaSession` 不暴露可执行的下一首操作

### Requirement: 外部控制结果与应用内控制保持一致
系统 MUST 保证外部媒体入口触发的控制结果与应用内控制路径在状态迁移和最终结果上保持一致。

#### Scenario: 外部暂停后应用内状态同步
- **WHEN** 用户通过外部媒体入口触发暂停
- **THEN** 应用内控制界面在可接受延迟内显示为暂停态并保持可继续播放
