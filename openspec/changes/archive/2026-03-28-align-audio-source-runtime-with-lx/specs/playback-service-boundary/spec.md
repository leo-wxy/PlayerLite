## ADDED Requirements

### Requirement: 播放客户端边界支持下发当前 source config
系统 SHALL 通过稳定的播放客户端边界下发当前 source config，而不是要求 `app` 直接依赖播放服务实现细节来切换音源。

#### Scenario: 设置页通过播放客户端边界切换当前音源
- **WHEN** 用户在设置页把某个 source 设为当前音源
- **THEN** `app` 通过稳定的播放客户端边界下发该 source config
- **AND** 调用方不需要直接引用播放服务实现类或进程内实现细节

#### Scenario: source config 非法时边界返回明确失败
- **WHEN** `app` 通过播放客户端边界下发一个无法解析或不受支持的 source config
- **THEN** 播放客户端边界向调用方返回明确失败
- **AND** 调用方不需要感知播放服务内部的异常类型或构造方式

### Requirement: 播放服务重建后恢复当前 source config
系统 MUST 在播放服务重建或客户端重连后恢复最近一次成功生效的当前 source config，确保播放真相源保持稳定。

#### Scenario: 播放服务重建后继续恢复当前音源
- **WHEN** 播放服务因进程重建、连接断开或服务销毁后重新启动
- **THEN** 系统恢复最近一次成功生效的当前 source config
- **AND** 后续在线播放仍使用该 source 对应的运行时能力

#### Scenario: 客户端重连后不需要重新拼接播放服务实现细节
- **WHEN** `app` 与播放服务重新建立连接
- **THEN** 客户端仍通过既有边界读取和控制当前播放会话
- **AND** 不需要因为 source runtime 的引入而改为直接操作播放服务实现类
