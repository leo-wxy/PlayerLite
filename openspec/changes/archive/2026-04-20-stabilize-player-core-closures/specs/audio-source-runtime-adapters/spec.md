## ADDED Requirements

### Requirement: source adapter 返回可解释的播放地址解析结果
系统 SHALL 让 `ResolveMusicUrl` 返回统一的成功、可重试失败、资源不可用、仅试听、未授权和不支持结果；调用方 MUST 基于这些分类执行 URL 刷新、重试、登录卡口或备用 source 兜底。

#### Scenario: adapter 返回带过期信息的成功结果
- **WHEN** 当前 source adapter 成功解析在线歌曲播放地址
- **THEN** 返回结果携带可用于播放的地址、请求头和可选过期时间
- **AND** 调用方可以在过期后重新解析而不是复用旧直链

#### Scenario: adapter 明确返回资源不可用
- **WHEN** 当前 source adapter 确认目标歌曲无版权、下架或没有完整可播地址
- **THEN** 返回结果明确表示资源不可用
- **AND** 调用方不会继续把该结果包装成通用网络错误

### Requirement: 单曲来源切换不改变全局当前 source
系统 SHALL 允许当前播放目标在解析失败、用户手动切换或备用兜底时使用单曲级 source override；该 override 只影响当前歌曲的资源解析、恢复和当前会话展示，不得自动改变设置页中的全局当前 source。

#### Scenario: 当前歌曲使用备用 source 成功播放
- **WHEN** 当前歌曲通过备用 source 成功解析并开始播放
- **THEN** 系统将该 source 作为当前歌曲的单曲来源上下文
- **AND** 设置页和后续新歌曲解析仍保持原全局当前 source 不变

#### Scenario: 手动切换当前歌曲来源
- **WHEN** 用户对当前在线歌曲触发单曲来源切换
- **THEN** 系统只重新解析并准备当前歌曲
- **AND** 当前播放列表中的其他歌曲来源上下文不被批量改写
