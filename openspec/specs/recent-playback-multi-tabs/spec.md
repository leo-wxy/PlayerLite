## Purpose

定义最近播放总页的多标签结构、本机最近听歌缓存、远端最近播放分类列表、按需加载与缓存行为，确保用户中心中的“最近播放”入口承载的不再只是单一歌曲页。

## Requirements

### Requirement: Recent Playback Multi-Tab Page

系统 MUST 提供一个最近播放总页，统一承接本机、歌曲、视频、声音、歌单、专辑和播客 7 类最近播放内容。

#### Scenario: Open recent playback page

- **WHEN** 用户从用户中心点击“最近播放”
- **THEN** 系统进入最近播放总页
- **AND** 页面顶部展示 7 个标签页：本机、歌曲、视频、声音、歌单、专辑、播客
- **AND** “本机”标签页固定排在第一个
- **AND** 默认选中“本机”标签页

### Requirement: On-Device Tab Uses Local Recent Listening Cache

系统 MUST 让本机 tab 展示来自本机歌曲播放行为形成的最近听歌缓存，不得依赖服务端接口返回该 tab 内容。

#### Scenario: Load on-device tab on page entry

- **WHEN** 用户首次进入最近播放页
- **THEN** 系统优先读取本机最近听歌缓存
- **AND** 该读取不依赖服务端最近播放接口

#### Scenario: Record song playback into local tab

- **WHEN** 用户在本机发生一次可记录的歌曲播放行为
- **THEN** 系统将该行为写入本机最近听歌缓存
- **AND** 该记录可在本机 tab 中展示

#### Scenario: Remote recent playback is unavailable

- **WHEN** 服务端最近播放接口不可用
- **THEN** 本机 tab 仍可基于本机缓存独立展示内容
- **AND** 不因远端接口失败而变成不可用

### Requirement: Lazy Load Recent Playback Tabs

系统 MUST 对最近播放标签页按需加载，不得在首屏一次性并发请求全部 6 个远端接口。

#### Scenario: Enter page for the first time

- **WHEN** 用户首次进入最近播放页
- **THEN** 系统只读取本机最近听歌缓存
- **AND** 不请求歌曲、视频、声音、歌单、专辑和播客 6 个远端标签页接口

#### Scenario: Switch to another tab

- **WHEN** 用户切换到未加载过的最近播放标签页
- **AND** 该标签页为歌曲、视频、声音、歌单、专辑或播客之一
- **THEN** 系统请求该标签页对应接口
- **AND** 每次请求默认 `limit=100`

### Requirement: Limit Recent Playback Retention To 100 Entries

系统 MUST 将本机 tab 和各远端 tab 的最近播放列表保留上限控制在 100 条。

#### Scenario: On-device tab exceeds retention limit

- **WHEN** 本机最近听歌缓存数量超过 100 条
- **THEN** 系统只保留最近的 100 条记录
- **AND** 更早的缓存记录被裁剪

#### Scenario: Load remote tab with default limit

- **WHEN** 系统请求任一远端最近播放标签页
- **THEN** 请求使用默认 `limit=100`
- **AND** 页面最多展示该 tab 最近 100 条记录

### Requirement: Render Recent Playback Items By Type

系统 MUST 根据不同最近播放类型，使用统一列表骨架渲染内容项，并展示各类型可识别的标题、副标题和辅助信息。

#### Scenario: Render on-device tab

- **WHEN** 当前标签页为本机
- **THEN** 每项展示歌曲名、歌手和专辑或来源信息

### Requirement: Song Rows In On-Device And Song Tabs Start Playback Directly

系统 MUST 让本机 tab 与歌曲 tab 的歌曲主区域点击直接按当前 tab 列表替换播放队列并开始播放。

#### Scenario: Tap a song in the on-device tab

- **WHEN** 用户点击本机 tab 中某一首歌曲主区域
- **THEN** 系统使用当前本机 tab 的歌曲列表替换当前播放队列
- **AND** 从被点击的歌曲开始播放

#### Scenario: Render song tab

- **WHEN** 当前标签页为歌曲
- **THEN** 每项展示歌曲名、歌手和专辑信息

#### Scenario: Tap a song in the remote songs tab

- **WHEN** 用户点击歌曲 tab 中某一首歌曲主区域
- **THEN** 系统使用当前歌曲 tab 的歌曲列表替换当前播放队列
- **AND** 从被点击的歌曲开始播放

#### Scenario: Render non-song tab

- **WHEN** 当前标签页为视频、声音、歌单、专辑或播客
- **THEN** 每项使用统一内容列表行渲染
- **AND** 展示标题、副标题以及对应类型的辅助信息

### Requirement: Read-Only First Delivery For Non-Song Tabs

系统 MUST 在首版中将非歌曲标签页视为只读展示内容，不得伪装成已有详情页或错误播放能力。

#### Scenario: Render video tab

- **WHEN** 用户查看最近播放视频标签页
- **THEN** 系统只展示列表内容
- **AND** 不要求点击进入站内视频详情页

#### Scenario: Render voice tab

- **WHEN** 用户查看最近播放声音标签页
- **THEN** 系统只展示列表内容
- **AND** 不要求点击进入站内声音详情页

#### Scenario: Render podcast tab

- **WHEN** 用户查看最近播放播客标签页
- **THEN** 系统只展示列表内容
- **AND** 不要求点击进入站内播客详情页

### Requirement: Independent State Per Tab

系统 MUST 为每个最近播放标签页维护独立的加载、空态和错误状态。

#### Scenario: One tab fails while another succeeds

- **WHEN** 某个最近播放标签页接口加载失败
- **THEN** 失败状态只影响当前标签页
- **AND** 已成功加载的其它标签页内容保持可查看

### Requirement: Preserve Loaded Tab Content During Switching

系统 MUST 在用户切换最近播放标签页时保留已加载内容，避免来回切换导致列表丢失或重复回到初始态。

#### Scenario: Switch away from a loaded tab and switch back

- **WHEN** 用户已经成功加载某个最近播放标签页内容
- **AND** 用户切换到其它标签页后再次返回
- **THEN** 系统展示之前缓存的内容
- **AND** 不因切换行为自动清空已加载结果

#### Scenario: Switch back to on-device tab after on-device content has loaded

- **WHEN** 用户已经成功加载本机 tab 内容
- **AND** 用户切换到任一远端 tab 后再次返回本机 tab
- **THEN** 系统展示之前缓存的本机最近听歌内容
- **AND** 不因切换行为自动重新读取本机缓存

#### Scenario: Refresh current tab explicitly

- **WHEN** 用户在当前最近播放标签页点击刷新
- **THEN** 系统仅刷新当前标签页内容
- **AND** 不清空其它标签页已缓存的结果

### Requirement: 本机最近播放只记录实际成功开播
系统 MUST 只在歌曲实际进入可播放执行态后写入本机最近播放缓存；点击行为、准备中状态、重试中状态和最终失败状态不得单独写入最近播放。

#### Scenario: 点击后资源失败不写入最近播放

- **WHEN** 用户点击一首在线歌曲开始播放
- **AND** 该歌曲因为无版权、下架、URL 过期刷新失败或所有 source 兜底失败而未成功开播
- **THEN** 系统不将该点击写入本机最近播放缓存

#### Scenario: 重试后成功开播只记录一次

- **WHEN** 用户点击一首在线歌曲开始播放
- **AND** 该歌曲经历 URL 刷新、短时重试或备用 source 兜底后成功进入播放
- **THEN** 系统将该歌曲写入本机最近播放缓存
- **AND** 同一次播放尝试只记录一次

#### Scenario: 本地歌曲成功开播后记录

- **WHEN** 用户播放本地文件或 `content://` 音源
- **AND** 播放服务确认该歌曲进入可播放执行态
- **THEN** 系统将该歌曲写入本机最近播放缓存
