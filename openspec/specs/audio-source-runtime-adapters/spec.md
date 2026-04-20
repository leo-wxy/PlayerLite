## Purpose

定义 source package、source adapter 生命周期与 `http-mapping` 首版边界，确保当前音源运行时能力通过统一模型接入播放链路。

## Requirements

### Requirement: 系统将可管理音源建模为 source package
系统 SHALL 将可导入、可展示、可切换的音源建模为 source package，而不是仅建模为 `baseUrl` 字符串；每个 source package MUST 至少包含可展示元数据、运行时类型、resolver 配置与运行时状态。

#### Scenario: 导入合法 native source package 后展示元数据与状态
- **WHEN** 用户导入一个合法的 native source package
- **THEN** 系统保存该 source 的名称、作者、版本、来源信息与 resolver 配置
- **AND** 设置页展示该 source 的启用状态、当前状态与最近一次初始化错误详情（若存在）

#### Scenario: 旧版 baseUrl manifest 读取时自动映射为 netease-compatible source package
- **WHEN** 系统读取一个仅包含 `type/baseUrl/name/author/version` 的旧版音源清单
- **THEN** 系统自动将其视为 `runtime.type = native` 且 `resolver.type = netease-compatible` 的 source package
- **AND** 用户无需手动重新导入即可继续看到该音源

#### Scenario: 不支持的 runtime type 不会被保存
- **WHEN** 用户导入的 source package 中 `runtime.type` 不是首版支持的 `native`
- **THEN** 系统拒绝保存该 source package
- **AND** 设置页展示可解释的导入失败原因

### Requirement: 系统通过统一的 source adapter 生命周期激活当前音源
系统 MUST 通过统一的 source adapter 生命周期激活当前音源；系统 MUST 只有在 source config 可解析、adapter 可创建且本地初始化成功时，才把该 source 切换为当前音源。

#### Scenario: 选择合法 source 后完成初始化并成为当前音源
- **WHEN** 用户把一个配置合法且可初始化的 source 设为当前音源
- **THEN** 系统通过对应 adapter 完成本地初始化
- **AND** 该 source 成为新的当前音源
- **AND** 设置页中的当前标识与播放进程中的当前 source 保持一致

#### Scenario: source config 非法或 adapter 不支持时保持原当前音源不变
- **WHEN** 用户尝试切换到一个配置非法、resolver type 不支持或本地初始化失败的 source
- **THEN** 系统拒绝这次切换
- **AND** 之前的当前音源保持不变
- **AND** 系统记录并展示本次失败的初始化错误信息

### Requirement: source adapter 通过 action 接口处理音源能力
系统 SHALL 通过统一的 `action + context -> result` 接口调用 source adapter，而不是让播放主流程直接依赖某个固定协议；首版 MUST 至少支持 `ResolveMusicUrl` action。

#### Scenario: 解析播放地址时向 source adapter 提供完整歌曲上下文
- **WHEN** 系统为一首在线歌曲请求 `ResolveMusicUrl`
- **THEN** 系统向当前 source adapter 提供 `songId`、歌曲标题、歌手、专辑、时长、默认音质偏好、请求头与试听片段信息
- **AND** source adapter 基于这些上下文返回统一的播放地址结果

#### Scenario: 首版未实现的 action 返回明确不支持
- **WHEN** 系统调用当前 source adapter 的 `ResolveLyric` 或 `ResolvePic`
- **THEN** 首版 source adapter 返回明确的不支持结果
- **AND** 系统不会伪装成成功或静默吞掉该调用

### Requirement: http-mapping source 被限制为单次 JSON 地址解析器
系统 MUST 将 `http-mapping` source 限制为“单次 HTTP 请求、JSON 响应、提取真实播放地址”的原生解析器，而不是通用脚本系统。

#### Scenario: 合法的 http-mapping source 可以从单次 JSON 响应中提取播放地址
- **WHEN** 当前 source 的 `resolver.type` 为 `http-mapping`
- **AND** 其请求模板、模板变量、响应路径与播放地址提取规则均合法
- **THEN** 系统使用单次 HTTP 请求获取 JSON 响应
- **AND** 从配置指定的响应路径中提取真实播放地址

#### Scenario: 超出首版映射边界的 source 会在导入阶段被拒绝
- **WHEN** 用户导入的 `http-mapping` source 需要多步请求、脚本表达式、HTML 抓取、通用 JSONPath 或不受支持的模板变量
- **THEN** 系统拒绝保存该 source
- **AND** 以可解释错误说明其超出首版支持范围

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
