## ADDED Requirements

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
