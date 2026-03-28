## ADDED Requirements

### Requirement: 在线歌曲通过当前音源 adapter 解析真实播放地址
系统 MUST 在准备具备歌曲标识的受保护在线歌曲时，通过当前音源 adapter 的 `ResolveMusicUrl` action 解析真实播放地址，而不是继续把固定接口协议散落在播放主流程中。

#### Scenario: 未切换音源时继续使用内置默认 source 解析播放地址
- **WHEN** 用户尚未切换当前音源而系统准备一首具备 `songId` 的在线歌曲
- **THEN** 系统使用内置默认 source adapter 执行 `ResolveMusicUrl`
- **AND** 播放准备链路继续基于该 adapter 返回的真实播放地址构建拉流 source

#### Scenario: 在线队列项没有直链时仍由当前 source 解析出可播地址
- **WHEN** 当前在线歌曲的队列项只包含 `songId` 和歌曲上下文而不包含长期有效的 `playbackUri`
- **THEN** 系统仍通过当前 source adapter 解析真实播放地址
- **AND** 不会因为队列项缺少直链而直接判定准备失败

#### Scenario: 当前音源无法返回播放地址时结束本次在线播放准备
- **WHEN** 当前 source adapter 无法为目标在线歌曲返回可用的播放地址
- **THEN** 系统以明确的在线播放准备失败结束本次准备
- **AND** 失败信息优先反映当前 source 返回的错误而不是伪装成通用本地读流错误

### Requirement: 默认音质作为 source 地址解析的偏好输入
系统 SHALL 将当前默认音质作为 `ResolveMusicUrl` 的偏好输入交给当前 source adapter；如果当前 source 无法满足该偏好但仍能返回可播地址，系统 MUST 允许本次播放降级成功。

#### Scenario: 当前音源支持目标默认音质时按该偏好解析
- **WHEN** 用户已设置默认音质且当前 source 支持该音质对应的地址解析
- **THEN** 系统把该默认音质作为偏好输入传给 `ResolveMusicUrl`
- **AND** 当前 source 按该偏好返回可播地址

#### Scenario: 当前音源只能返回较低音质时仍允许成功播放
- **WHEN** 用户设置了较高默认音质
- **AND** 当前 source 只能返回较低但可播的在线地址
- **THEN** 系统允许本次在线播放继续成功
- **AND** 不会因为 source 无法满足目标音质而直接阻断播放

### Requirement: 切换当前音源后旧解析缓存失效并重新准备当前在线歌曲
系统 SHALL 在切换当前音源后失效旧音源对应的在线播放解析缓存，并对当前会话中的在线歌曲重新准备，以保证后续播放地址与当前 source 一致。

#### Scenario: 正在播放的在线歌曲切换音源后重新准备并继续播放
- **WHEN** 用户在一首具备 `songId` 的在线歌曲播放过程中切换当前音源
- **THEN** 系统清空旧音源的在线播放解析缓存
- **AND** 使用新音源重新准备该歌曲
- **AND** 如果切换前处于播放态，则重新准备后继续播放

#### Scenario: 已暂停的在线歌曲切换音源后保持暂停位置
- **WHEN** 用户在一首已暂停的在线歌曲上切换当前音源
- **THEN** 系统使用新音源重新准备该歌曲
- **AND** 重新准备后仍保持暂停态与当前位置

#### Scenario: 本地歌曲切换音源时不会误触发在线重准备
- **WHEN** 当前曲目是本地文件或 `content://` 音源且用户切换当前音源
- **THEN** 系统更新当前音源配置
- **AND** 不会因为切换音源而把本地曲目错误地送入在线地址解析链路
