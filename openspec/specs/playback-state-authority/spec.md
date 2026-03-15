## Purpose

定义后台播放服务作为唯一播放真相源的职责边界，以及前台 UI 如何通过远端快照进行状态投影，确保播放控制、会话状态与模块依赖方向保持清晰一致。

## Requirements

### Requirement: Playback service is the authoritative playback state owner
The system SHALL manage the active playback execution state inside the background playback service. The currently projected queue, current media item, playback speed, play/pause readiness, seek position, and the playback-mode projection exposed through the media session MUST stay consistent with the business-layer playback state that drives the service.

#### Scenario: Service reflects business-layer mode without forcing item change
- **WHEN** the business layer updates the current playback mode while a media item is already active
- **THEN** the playback service keeps the current media item stable and the next media-session snapshot reflects the updated playback-mode projection

#### Scenario: Service follows projected queue on completion
- **WHEN** the current item completes naturally while the service is playing a queue projected from business-layer mode state
- **THEN** the playback service advances or repeats according to the currently projected queue / repeat behavior and publishes the resulting execution state through the media session

### Requirement: App playback UI projects remote playback state
The app SHALL derive playback UI state from remote playback snapshots and SHALL limit local playback state to UI-only concerns such as sheet visibility, drag state, optimistic request state, and the shuffle-only “show original order” display preference. The remote snapshot MUST include the current queue item's shared display metadata, including current song identity, duration hint, and cover artwork, so the player page can render current content without refetching detail data.

#### Scenario: Mode indicator updates without play-state toggle
- **WHEN** the playback service accepts a playback mode change while playback state remains unchanged
- **THEN** the app updates the visible playback-mode indicator from the remote snapshot on the next sync cycle without waiting for a separate play/pause transition

#### Scenario: Original-order toggle remains app-local
- **WHEN** the user toggles “显示原始顺序” while shuffle mode is active
- **THEN** the app updates playlist presentation locally without treating the toggle itself as a remote playback-state mutation

#### Scenario: Player page artwork and duration come from the remote snapshot
- **WHEN** the current queue item carries shared duration and cover metadata
- **THEN** the app updates the player page duration display and cover artwork from the remote snapshot
- **AND** it does not require a separate detail-page fetch to render the current track's primary visual information

#### Scenario: 非权威远端快照不会覆盖本地已恢复的当前曲目信息
- **WHEN** app 已经从本地播放列表恢复出当前激活项与其展示元数据
- **AND** 远端这次同步只返回了缺少当前媒体身份的过渡快照，或返回了不属于当前本地队列的旧 `currentMediaId`
- **THEN** app 保留本地已恢复的当前歌曲标题、歌手、封面与当前时间投影
- **AND** 不会把这些字段错误清空成空白、`0` 或上一条旧曲目信息
- **AND** 只有真正匹配当前队列的权威远端快照到达后，才允许覆盖这组本地投影

#### Scenario: 暂停时唱片主视觉保持当前旋转角度
- **WHEN** 当前曲目处于播放页唱片主视觉展示态
- **AND** 用户执行暂停
- **THEN** 播放页唱片主视觉保持暂停瞬间的旋转角度
- **AND** 只有旋转动画停下，而不是复位到固定初始角度

#### Scenario: 恢复播放时唱片主视觉从暂停角度继续而不闪烁
- **WHEN** 当前曲目已经在播放页唱片主视觉上暂停
- **AND** 用户再次执行播放或恢复
- **THEN** 唱片主视觉从暂停时的角度继续旋转
- **AND** 不会在恢复瞬间出现缩放闪烁、跳回初始角度或明显相位跳变

#### Scenario: MediaSession 内容入口直接回到播放页
- **WHEN** 用户从系统通知、锁屏卡片或其他 `MediaSession` 内容入口点击当前播放项
- **THEN** app 被拉起后直接切回播放页展开态
- **AND** 不会只回到首页或停在与当前播放无关的 tab

#### Scenario: 小屏设备上的播放页不发生控件重叠
- **WHEN** 播放页运行在较小屏幕高度或底部手势导航区占比较高的设备上
- **THEN** 唱片主视觉、进度区和控制区仍保持自上而下的可见顺序
- **AND** 底部控制区不会被挤出可视区域或与系统手势区发生明显重叠

#### Scenario: 窄屏设备上的播控区整体下移且不发生横向重叠
- **WHEN** 播放页运行在较窄宽度的小屏设备上
- **THEN** 播控区整体会相对主内容再下移一些，优先让节目主元素保持舒展
- **AND** 上一首、播放模式、下一首与播放列表按钮仍保持可点且互不重叠
- **AND** 中央播放按钮不会把两侧按钮挤压到不可见或不可点区域

#### Scenario: Seek 后进入明确的 buffering 态并冻结进度
- **WHEN** 用户在在线播放过程中拖动进度条执行 seek
- **THEN** 播放服务先进入明确的 buffering / preparing 态，而不是继续维持可误解的 Playing 投影
- **AND** 在新的可播放数据真正到达前，播放进度保持冻结，不继续向前推进
- **AND** 一旦新的 seek 目标附近数据恢复可播，播放态再切回 Playing 或 Paused

#### Scenario: 切歌时旧播放 completion 不会覆盖当前曲目状态
- **WHEN** 当前歌曲正在播放且用户执行上一首、下一首或直接切换到列表中的另一首
- **AND** 旧歌曲对应的 playback source 在切歌过程中被停止或释放
- **THEN** 系统先停止旧播放再释放旧 source
- **AND** 即使旧播放稍后回传迟到的 completion 或错误结果，也不会覆盖当前曲目的播放状态
- **AND** 新切换到的目标曲目仍会继续进入准备并自动开始播放

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

### Requirement: Shared playback contracts are isolated from service implementation
The system SHALL define playback commands, metadata extras, and a minimal playable DTO contract in modules that can be consumed by the app without depending on playback-service implementation packages. The playback service MUST depend on that minimal playable contract rather than a full online-only `MusicInfo` shape, while the app MAY maintain richer domain models such as `MusicInfo` or `LocalMusicInfo` and project them into the shared playback contract. Those domain models and shared DTOs MUST use domain-friendly field names rather than raw upstream API abbreviations, with explicit mappers translating source fields such as `id`, `name`, `ar`, `al`, and `dt` into the app's domain models and then into the playback contract.

#### Scenario: App consumes shared playback-mode projection
- **WHEN** the app builds against playback control APIs that read or display the current playback mode
- **THEN** it imports the shared playback contract and playback client modules without referencing playback-service implementation packages other than the exported service entry point required for controller connection

#### Scenario: Shared queue metadata crosses the process boundary intact
- **WHEN** the app projects a queue item built from playlist detail data
- **THEN** the shared playback contract carries song identity, duration hint, cover artwork, and online request metadata across the process boundary
- **AND** the playback service does not require app-specific implementation types to prepare or display the current item

#### Scenario: Queue metadata may be enriched after playback starts
- **WHEN** the app has already built an online playback queue from stable `songId` values
- **AND** richer metadata such as artwork or semantic title is loaded later from `/song/detail`
- **THEN** the app may update the in-memory playlist metadata asynchronously without rebuilding queue identity
- **AND** that metadata enrichment does not require changing the current queue order or active item semantics

#### Scenario: Raw upstream fields are mapped into semantic playback names
- **WHEN** the app builds shared playback metadata from `/song/detail` or equivalent upstream song-detail payloads
- **THEN** it maps raw source fields such as `id`, `name`, `ar`, `al`, and `dt` into semantic playback-contract names
- **AND** the shared playback contract does not expose upstream abbreviation fields directly as its public queue metadata shape

#### Scenario: Local playback projects into the same playable base contract
- **WHEN** the app prepares a local file or `content://` source for playback
- **THEN** it may use a local-domain model distinct from online `MusicInfo`
- **AND** it still projects that local model into the same shared minimal playable contract before handing it to the playback service
- **AND** the playback service does not require the full online `MusicInfo` field set to play the local source

### Requirement: MediaSession adapter remains a mapping layer
The media-session adapter SHALL map service runtime state to Media3 state and forward incoming playback commands to the service runtime without owning raw playlist-order or shuffle-generation business rules beyond command translation.

#### Scenario: Session queue-navigation command forwards to service runtime
- **WHEN** Media3 issues a queue-navigation command
- **THEN** the media-session adapter forwards the command to the playback service runtime and the resulting session state is produced from the updated projected queue state

### Requirement: Playback preparation honors shared duration hints
The playback preparation path SHALL reuse shared duration hints from queue metadata when available and MUST NOT require a source metadata probe solely to publish current duration.

#### Scenario: Positive duration hint avoids duration-only probing
- **WHEN** a queue item provides a positive duration hint through the shared playback contract
- **THEN** the playback preparation path uses that hint for current-track duration projection and cache-session setup
- **AND** it does not require an FFmpeg metadata probe solely to obtain playback duration

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
