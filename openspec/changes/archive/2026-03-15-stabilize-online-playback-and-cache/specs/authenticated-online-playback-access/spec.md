## ADDED Requirements

### Requirement: 在线播放在缓存不可信或不完整时才继续解析远端地址
系统 SHALL 在在线播放准备前先判断缓存是否可信完整；只有缓存不存在、不完整或不可信时，才继续解析远端播放地址。

#### Scenario: 可信完整缓存直接命中
- **WHEN** 当前在线歌曲已经存在可信完整的缓存
- **THEN** 系统直接使用现有缓存准备播放
- **AND** 不再重复请求 `/song/url` 或 `/song/url/v1`

#### Scenario: 稀疏缓存不能伪装成完整缓存
- **WHEN** data 文件长度已经达到 `contentLength`
- **AND** `completedRanges` 仍存在空洞
- **THEN** 系统不会把该缓存视为完整缓存
- **AND** 仍会继续缺失区间补拉或远端解析

#### Scenario: 试听解析结果不进入完整音源地址缓存
- **WHEN** 远端返回的在线播放结果表明当前歌曲仅提供试听片段
- **THEN** 系统可以使用该结果继续当前歌曲播放准备
- **AND** 不会把该结果写入 10 项完整音源 URL LRU 缓存

#### Scenario: 明显短于稳定时长的结果不能继续伪装成完整音源
- **WHEN** 当前歌曲具备稳定时长提示
- **AND** 本次解析结果或缓存探测时长明显短于该稳定时长
- **THEN** 系统不会把该结果继续当作可信完整音源
- **AND** 会触发重新解析或重建缓存

### Requirement: Seek 后的 range 读取在合法 offset 上遇到空读时会主动重试
系统 SHALL 在 seek 后的合法 offset 上，把首次异常空读、瞬时 `0 byte` 或立即 EOF 视为可重试异常，而不是直接把它当成可信结束。

#### Scenario: 合法 offset 上的首次空读会触发重连重试
- **WHEN** 用户 seek 到一个仍位于合法内容范围内的位置
- **AND** 新连接第一次读取出现空读或立即 EOF
- **THEN** provider 主动关闭当前连接并对同一 offset 执行重连重试
- **AND** 不会立刻把本次读取结果上抛为可信 EOF

#### Scenario: HTTPS range 读取保留原始域名
- **WHEN** provider 为 HTTPS 音源建立 range 连接
- **THEN** 系统使用原始 HTTPS 域名发起连接
- **AND** 不会把域名替换成裸 IP 再依赖 `Host` 请求头继续连接

#### Scenario: 独立 metadata probe 成功时不再强制 rewind playback source
- **WHEN** 当前曲目的元信息已经由独立 probe source 成功读取
- **THEN** 真正用于播放的 source 不再被额外强制 rewind
- **AND** 系统不会把这次准备误判成 `Source rewind failed`
