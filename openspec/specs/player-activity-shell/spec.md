## Purpose

定义独立 `PlayerActivity` 作为完整播放器的唯一宿主，以及它的启动契约、返回语义和系统媒体入口对齐规则。

## Requirements

### Requirement: 独立 PlayerActivity 承载完整播放器
系统 SHALL 使用独立 `PlayerActivity` 作为完整播放器页的唯一宿主，并由该 Activity 承载当前播放页、播放器页内的播放列表 sheet 与播放器级返回行为，而不是继续依赖 Home 壳层内部的展开 surface 或详情页本地播放器宿主。

#### Scenario: 从首页 mini player 主体进入独立播放器页
- **WHEN** 用户在首页概览态点击底部 mini player 的主体区域
- **THEN** 系统启动独立 `PlayerActivity`
- **AND** `PlayerActivity` 展示当前播放页而不是回到首页内部某个展开态

#### Scenario: 从详情页 mini player 主体进入独立播放器页
- **WHEN** 用户在专辑、歌手或歌单详情页点击底部 mini player 的主体区域
- **THEN** 系统启动独立 `PlayerActivity`
- **AND** 当前详情页不再本地承载完整播放器界面

#### Scenario: 从独立播放器页返回时恢复原有页面上下文
- **WHEN** 用户从独立 `PlayerActivity` 执行返回操作
- **THEN** 系统关闭该 Activity
- **AND** 用户回到此前发起打开动作的首页或详情页上下文

### Requirement: PlayerActivity 启动契约支持初始动作
系统 SHALL 通过统一的启动契约承载播放器页的初始动作请求，包括“打开播放器”“打开后立即展开播放列表”和“打开后立即开始播放”等语义，而不是让调用方通过页面内私有状态猜测播放器页首帧行为。

#### Scenario: 请求打开播放列表时首帧直接展开列表
- **WHEN** 任一入口以 `openPlaylist` 语义启动独立 `PlayerActivity`
- **THEN** 系统进入播放器页后直接展开当前播放列表 sheet
- **AND** 用户无需再次点击播放器页中的列表按钮

#### Scenario: 请求开始播放时进入播放器页后触发播放
- **WHEN** 任一入口以 `startPlayback` 语义启动独立 `PlayerActivity`
- **THEN** 系统进入播放器页后触发播放开始动作
- **AND** 不要求调用方先在其他页面手动展开播放器页

#### Scenario: 同时请求打开播放列表与开始播放
- **WHEN** 任一入口同时携带 `openPlaylist` 与 `startPlayback` 语义启动独立 `PlayerActivity`
- **THEN** 系统进入播放器页后执行播放开始动作
- **AND** 播放列表 sheet 在播放器页内保持已展开

### Requirement: 系统媒体内容入口统一唤起 PlayerActivity
系统 SHALL 让通知、锁屏卡片和其他 `MediaSession` 内容入口统一唤起独立 `PlayerActivity`，而不是仅返回首页壳层或停在无关页面。

#### Scenario: 从通知点击当前播放项进入独立播放器页
- **WHEN** 用户从系统通知点击当前播放项
- **THEN** app 被拉起后直接进入独立 `PlayerActivity`
- **AND** 不会只回到首页或停在其他无关页面

#### Scenario: 从锁屏或其他媒体入口点击当前播放项进入独立播放器页
- **WHEN** 用户从锁屏卡片或其他 `MediaSession` 内容入口点击当前播放项
- **THEN** app 被拉起后直接进入独立 `PlayerActivity`
- **AND** 当前播放上下文与播放器页展示保持一致

### Requirement: PlayerActivity 负责播放器页方向模式的隔离管理
系统 SHALL 让独立 `PlayerActivity` 负责播放器页方向模式的隔离管理，使播放器可以在页面内切换 `自动`、`锁定横屏` 和 `锁定竖屏`，同时不影响 app 其他页面。

#### Scenario: PlayerActivity 根据播放器方向模式更新页面方向
- **WHEN** 播放器方向模式从 `自动` 切换到 `锁定横屏` 或 `锁定竖屏`
- **THEN** `PlayerActivity` 立即对当前页面应用目标方向
- **AND** 该方向更新只作用于独立播放器页

#### Scenario: 关闭 PlayerActivity 后恢复外层页面默认方向
- **WHEN** 用户从独立 `PlayerActivity` 返回到原页面上下文
- **THEN** app 其他页面继续使用各自默认方向策略
- **AND** 不会继承播放器页最后一次锁定的方向模式
