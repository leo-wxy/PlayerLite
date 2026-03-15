## MODIFIED Requirements

### Requirement: App playback UI projects remote playback state
The app SHALL derive playback UI state from remote playback snapshots and SHALL limit local playback state to UI-only concerns such as sheet visibility, drag state, optimistic request state, and the shuffle-only “show original order” display preference.

#### Scenario: Seek 后进入明确的 buffering 态并冻结进度
- **WHEN** 用户在在线播放过程中执行 seek
- **THEN** 播放状态先进入明确的 buffering / preparing 投影
- **AND** 在新数据真正恢复可播前，进度保持冻结
- **AND** 恢复后才重新切回 Playing 或 Paused

#### Scenario: 旧曲目的播放回调不会覆盖当前曲目
- **WHEN** 用户在当前曲目播放过程中切到上一首、下一首或列表中的另一首
- **AND** 旧曲目的 completion、finally 或错误结果晚于切歌到达
- **THEN** 这些迟到回调不会覆盖当前曲目的播放状态
- **AND** 当前目标曲目仍继续准备并自动开始播放

#### Scenario: 旧 `PLAYING` 残留不会把当前曲目误判成 already active
- **WHEN** 播放服务的共享状态仍保留了来自旧曲目的 `PLAYING` 投影
- **AND** 当前曲目尚未真正启动播放
- **THEN** `playCurrent()` 不会把当前请求误判成 already active
- **AND** 系统仍会为当前曲目执行 `open + launchPlay`

#### Scenario: 手动上一首或下一首后目标曲目自动开始播放
- **WHEN** 用户手动点击上一首或下一首
- **THEN** 系统切换到目标曲目后立即自动开始播放
- **AND** 不要求用户再额外点击一次播放按钮

#### Scenario: 替换播放列表后当前目标曲目不会停在 prepare ready 但未起播
- **WHEN** 系统在“已处于继续播放”语义下用 `setMediaItems(...)` 替换当前播放列表
- **AND** 当前目标曲目已经准备完成
- **THEN** 系统会在这次换队列命令链内直接拉起目标曲目的播放
- **AND** 不依赖后续重复下发一条额外的 `play()` 命令才能真正开始播放

### Requirement: 播放服务预热与显式播放启动分离
系统 SHALL 把播放服务的预热连接与真正的播放启动拆开，避免初始化阶段的 service 启动和后续前台升级互相打架。

#### Scenario: 初始化阶段只预热 controller / session 连接
- **WHEN** 播放页 ViewModel 初始化
- **THEN** 系统只预热 `MediaController` / `SessionToken` 连接
- **AND** 不会在初始化阶段直接启动 playback service 进程

#### Scenario: 显式播放动作使用 foreground-safe 启动
- **WHEN** 用户触发播放全部、点歌播放、恢复播放、切歌补队列或其他明确播放动作
- **THEN** 系统先使用 foreground-safe 的 service 启动方式拉起 playback service
- **AND** 再继续同步队列或下发播放控制命令

#### Scenario: 前台通知升级被拒绝时不会打死 playback 进程
- **WHEN** playback service 需要升级为前台通知
- **AND** 系统拒绝本次 `startForeground()` 调用
- **THEN** service 保留普通通知并记录诊断信息
- **AND** `:playback` 进程不会因为这次拒绝直接崩溃
