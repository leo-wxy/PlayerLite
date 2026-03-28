## Context

当前账户域只有用户中心一级页，没有独立的设置页。`MainShellScreen.kt` 里的 `UserCenterScreen` 目前直接承载头像头部、快捷入口、自建歌单列表和一个内联“退出登录”按钮；`MainActivity.kt` 负责打开“喜欢”“最近”“本地”“导入”等独立二级页，但还没有“设置”入口。

这次变更横跨几个现有稳定链路：

- 用户会话方面，`UserRepository.logout()` 已经负责远端登出尝试、本地会话清理和 `loginStateFlow` 降级，`UserCenterViewModel` / `PlayerViewModel` 都基于这条链路感知登录态变化。
- 播放缓存方面，真正安全的清理动作目前只存在于播放进程：`PlaybackProcessRuntime.clearCache()` 会先停止播放、释放当前 source，再调用 `CacheCore.clearAll()` 清空 `cache_core`。app 侧现有 `PlayerViewModel.clearCache()` 只是通过 `PlaybackTransportController` 下发这个命令。
- 其他可见缓存方面，歌词通过 `LyricLocalStore` 落在 `filesDir/lyrics`；这些文件和播放缓存不在同一个目录，也不共享同一套清理入口。
- “音源”方面，仓库里目前只有零散的 source 概念，例如播放链路的本地/内容 URI source、以及 `WebPlaylistImport` 中写死的 `ImportedPlaylistSource`；还没有一个可被设置页维护的通用音源注册表。

因此，这份设计的重点不是继续把按钮堆在用户中心里，而是在不改现有登录/播放主链路的前提下，补一个独立设置承载位，并明确三项能力分别应该复用哪条现有基础设施、哪些边界暂时不扩。

## Goals / Non-Goals

**Goals:**

- 在账户域中新增一个稳定可达的设置页，并沿用当前二级页的导航模式承载设置能力。
- 把“退出登录”“缓存管理”“添加音源”三项能力从用户中心主内容区剥离出来，收口到统一的设置页信息架构中。
- 退出登录复用现有 `UserRepository.logout()` 语义，确保会话、本地持久化和前台状态同步降级。
- 缓存管理提供真实可见的缓存占用信息，并定义清理动作如何同时覆盖播放缓存和歌词缓存。
- 为“添加音源”建立稳定的本地注册表和持久化模型，让设置页可以展示已有音源并新增音源配置。
- 保持设置页与登录页、用户中心页使用同源的账户域视觉 token，避免再起一套孤立样式。

**Non-Goals:**

- 不改动底部双 Tab 主结构、登录流程、游客态进入主界面的门控逻辑。
- 不把当前在线播放、歌单导入或本地播放链路重构成完整的多音源插件架构。
- 不把搜索历史、播放列表状态、音效偏好等持久化状态都算进“缓存”并一起清空；首版只覆盖明确可回收的内容缓存。
- 不在本轮要求云端同步音源配置，也不要求账号间共享设置页里的音源列表。
- 不在本轮扩展用户未提及的其他设置项，例如主题切换、通知开关或网络策略。

## Decisions

### 1. 设置页使用独立 `SettingsActivity`，由用户中心头部提供稳定入口，而不是继续内联在 `UserCenterScreen`

当前仓库的二级内容页已经有比较稳定的承载模式：`LikedContentActivity`、`RecentSongsActivity`、`LocalSongsActivity`、`WebPlaylistImportActivity` 都是从 `MainActivity` 发起的独立页面。设置页属于同级的账户域二级能力，如果继续把它做成用户中心页内嵌 section，会让 `MainShellScreen.kt` 和 `UserCenterScreen` 继续膨胀，也会和已经独立出去的二级页风格不一致。

本次设计选择：

- 新增独立 `SettingsActivity`，负责设置页的生命周期、标题栏返回和内容滚动容器。
- 在 `UserCenterScreen` 的头部资料区增加稳定的设置入口，优先使用头部右上角 icon action，而不是再占用现有快捷入口位。
- 已登录和未登录用户都允许进入设置页，但设置页中的账户 section 根据当前登录态展示“退出登录”或“去登录”引导。
- 用户中心当前的内联“退出登录”按钮收口到设置页，避免同一账户域里同时出现两套入口和两套文案层级。

这样做能让用户中心继续聚焦“个人内容入口”，而设置页承担“账户与应用设置”的职责边界。

**备选方案：**

- 直接把设置 section 插进 `UserCenterScreen`：实现最快，但会继续拉长用户中心页，也会把“个人内容”和“设置”混在同一个滚动面板里。
- 做成底部弹层：切换成本低，但设置项会持续增长，弹层不适合作为长期承载位。

### 2. 设置页使用独立 `SettingsViewModel` 组织三段式状态，不复用 `PlayerViewModel` 或 `UserCenterViewModel`

`PlayerViewModel` 已经承担播放、歌词、音质、会话头部等多类职责；`UserCenterViewModel` 负责用户中心内容区加载。如果设置页再复用这两个 ViewModel 之一，就会把“账户设置”“缓存清理”和“音源注册表”继续塞进已有状态模型，后续很难维护边界。

本次设计选择新增独立 `SettingsViewModel`，其状态分成三段：

- `accountState`：当前登录态、账户摘要、是否正在执行退出登录。
- `cacheState`：缓存统计项列表、总占用、最近刷新时间、是否正在清理、最近一次清理结果。
- `sourcesState`：音源列表、是否正在读取、添加音源表单状态、基础校验错误。

依赖上保持轻量：

- `UserRepository` 负责账户 section。
- `SettingsCacheRepository` + `SettingsCacheController` 负责缓存展示和清理。
- `AudioSourceRepository` 负责音源列表与持久化。

这样可以把设置页的副作用和状态严格限定在设置域，不把用户中心或播放器的 ViewModel 继续做大。

**备选方案：**

- 扩展 `UserCenterViewModel`：导航上更近，但会把用户内容和设置状态耦在一起。
- 扩展 `PlayerViewModel`：可直接复用清缓存能力，但职责边界会进一步失控。

### 3. 退出登录完全复用 `UserRepository.logout()`，设置页只负责确认交互和结果投影

退出登录的正确语义已经存在：`DefaultUserRepository.logout()` 会尝试调用远端 logout，然后清空本地 session storage，并把 `loginStateFlow` 更新为 `LoggedOut`。这条链路已经被登录页、用户中心和其他依赖登录态的 ViewModel 验证过，不应该在设置页再复制一套“手动清偏好 + 手动改 UI 状态”的实现。

本次设计选择：

- 设置页点击“退出登录”时先展示确认提示，明确说明只退出在线会话，不影响本地播放能力。
- 用户确认后，`SettingsViewModel` 直接调用 `UserRepository.logout()`。
- 设置页不主动清理播放列表、播放进度、音效设置或缓存，继续沿用现有“退出登录不影响本地播放”的产品语义。
- 退出完成后由 `loginStateFlow` 自然驱动设置页和用户中心同步降级，不额外维护第二套“设置页登录态”。

这能保证设置页只是一个新的触发入口，而不是新的会话实现。

**备选方案：**

- 在设置页里重新组织 logout use case：看起来更“完整”，但实际是在重复已有逻辑。
- 退出登录时顺带清空播放状态和缓存：行为更激进，但和仓库现有 requirement 冲突，也超出用户本次范围。

### 4. 缓存管理采用“app 侧统计 + 播放进程清理”的双通道方案，首版明确只管理 `cache_core` 和 `lyrics`

当前缓存相关能力分散在两个位置：

- 在线播放缓存位于 `cacheDir/cache_core`，真实清理动作必须通过播放进程的 `clearCache()` 完成，因为它需要先停播并释放当前 `sourceSession`。
- 歌词缓存位于 `filesDir/lyrics`，这是 app 侧本地文件，不在 `CacheCore` 管理范围内。

如果设置页直接自己删 `cache_core` 目录，会绕开播放进程的 stop/release 逻辑，存在清理时机错误和文件占用风险；如果完全只暴露播放进程的 `clearCache()`，又无法把歌词缓存纳入管理。因此本次设计选择双通道：

- `SettingsCacheRepository` 在 app 侧扫描 `cacheDir/cache_core` 和 `filesDir/lyrics`，输出总占用与分项占用。
- `SettingsCacheController` 复用 `AppPlaybackGraph.playerServiceController(...)` 创建一个轻量服务控制器，在执行清理前先 `ensurePlaybackServiceStartedForPlayback()` 并 `connectIfNeeded()`，然后发送现有 `clearCache()` 指令。
- 只有当播放缓存清理指令成功下发后，app 侧才继续删除 `lyrics` 目录中的歌词文件，避免出现“歌词删了但播放缓存命令根本没发出去”的半成功状态。
- 清理完成后重新统计缓存占用，并把结果投影到 `cacheState`；若跨进程清理只收到“命令已接受”而非最终成功回执，则设置页显示“正在刷新结果”，并以后续重新统计结果为准。

缓存边界首版收紧为两类：

- 纳入设置页缓存管理：`cache_core` 在线播放磁盘缓存、`lyrics` 歌词文件缓存。
- 不纳入设置页缓存管理：播放列表状态、用户会话、搜索历史、音效/音质偏好等非缓存型持久化状态。

这样既能给用户真实可感的占用数字，也不会为了“一个清理按钮”去重构整个跨进程缓存协议。

**备选方案：**

- 设置页直接删除 app 目录：实现省事，但会绕过播放进程的安全释放步骤。
- 新增一整套跨进程“缓存统计 + 清理回执”协议：长期更完整，但明显超出本轮范围。

### 5. “添加音源”首版落成轻量本地注册表，不立即把在线播放链路改造成可插拔多源引擎

仓库里已经存在多个“source”概念，但它们都各自写死在当前 feature 中：本地播放的 `LocalFileSource` / `ContentUriSource`、歌单导入里的 `ImportedPlaylistSource.NETEASE/QQ_MUSIC`，以及在线播放默认依赖当前 API base URL。现阶段还没有一个统一的“音源注册表”来驱动这些 feature。

如果这次直接把播放、搜索、详情、导入全部改成可插拔 provider 架构，改动会跨 `app/`、`playback-service/`、`feature-search/` 等多个模块，已经超出“设置页补能力”的范围。因此本次设计选择先做轻量本地注册表：

- 新增 `ManagedAudioSource` 模型，至少包含 `id`、`displayName`、`baseUrl`、`kind`、`enabled`、`addedAtMs`。
- `AudioSourceRepository` 负责读取、追加和去重校验；持久化使用独立 `SharedPreferences` + JSON 序列化，沿用仓库当前处理轻量本地配置的方式。
- 设置页 sources section 首版提供“查看已有音源列表”和“添加音源”表单，不要求本轮完成编辑、拖拽排序或云端同步。
- 表单首版校验只覆盖能立即落地的规则：显示名非空、`baseUrl` 非空且为 `http/https`、同一 `baseUrl` 不能重复添加。
- 这个注册表先作为未来 source-aware 功能的单一来源；本轮只保证设置页可以维护它，不承诺立即替换现有在线播放或导入功能的固定 provider 实现。

这条路径能把“音源管理”先做成可扩展的基础设施，而不是在没有边界的情况下把所有播放入口一次性重写。

**备选方案：**

- 直接把当前在线播放重构成多 provider 架构：最终形态更完整，但与本次设置页 change 的范围不匹配。
- 把“添加音源”做成一个纯占位按钮：交付快，但没有实际数据结构，后续仍要推倒重来。

### 6. 设置页视觉复用 `AccountVisualStyle` 与账户域卡片组件，不额外创建全局主题分支

用户中心和登录页已经在账户域内形成了一套局部视觉语言：`AccountVisualStyle`、`AccountPageBackground`、`AccountCardSurface`、`AccountPrimaryButton` 等组件已经可复用。设置页如果再引入第三套样式，会让账户域的三个页面看起来不像同一个产品区域。

本次设计选择：

- 设置页复用 `AccountPageBackground` 作为页面背景，保持同源的暖色氛围。
- section 继续使用 `AccountCardSurface` 或同风格变体承载账户、缓存、音源三个分组。
- 重要 destructive action 使用账户域强调色的危险变体，而不是退回默认 Material 灰色按钮。
- 为设置页补充少量局部组件，例如设置项 row、缓存统计 row、音源列表 row，但保持圆角、间距和色板与账户域一致。

这样可以在不动全局主题的前提下，让设置页自然接到当前用户中心和登录页的风格体系里。

**备选方案：**

- 直接用默认 `Scaffold + ListItem`：实现最快，但会和现有账户域风格脱节。
- 把账户域样式全面上升到全局主题：长期可复用，但本轮没有必要扩大到全局视觉改造。

## Risks / Trade-offs

- **[设置页把退出登录从用户中心主页面移走，增加一步点击]** → 在用户中心头部提供稳定明显的设置入口，让“我的内容”和“账户设置”职责更清晰，而不是继续把二者混在一个长列表里。
- **[缓存清理是跨进程异步动作，UI 可能先收到“已发送”而不是最终成功]** → 设置页把“请求已发送”和“统计已刷新”区分成两个状态，并以重新统计后的目录占用结果作为最终依据。
- **[缓存统计直接扫描目录时可能存在瞬时误差]** → 把统计工作放在 IO 线程执行，并允许手动刷新；首版以“接近真实、可重复刷新”为目标，不为此新增重协议。
- **[音源注册表首版还不会立刻驱动全部播放能力]** → 在 spec 和 UI 文案里明确首版只保证维护音源配置与本地持久化，为后续 provider 接入提供单一来源。
- **[新增独立 Activity 与 ViewModel 会引入一些样板代码]** → 复用现有二级页模式和账户域组件，避免把复杂度继续堆回 `MainActivity` / `MainShellScreen`。

## Migration Plan

1. 在账户域增加 `SettingsActivity`、`SettingsViewModel` 以及从用户中心进入设置页的稳定导航入口。
2. 新增 `SettingsCacheRepository` / `SettingsCacheController`，补齐缓存统计模型、跨进程清理包装和歌词缓存删除逻辑。
3. 新增 `ManagedAudioSource`、`AudioSourceRepository` 和本地存储实现，完成音源列表与新增表单的持久化基础。
4. 实现设置页 UI，分成账户、缓存、音源三个 section，并把用户中心内联“退出登录”收口到设置页。
5. 补充单元测试与 UI 测试，至少覆盖：游客态/登录态打开设置页、退出登录后的状态同步、缓存统计刷新与清理流程、音源去重校验和重启后恢复。

## Open Questions

- “添加音源”首版是否只需要新增和展示，还是希望同一轮就补上删除/编辑能力；当前设计默认先不做编辑删除。
- 音源条目的 `kind` 是否只需要保留为通用字符串/枚举占位，还是用户已经有明确的来源类型集合需要同步落进首版表单。
- 缓存管理首版是否要把歌词缓存明细单独展示给用户，还是只在总占用里合并显示；当前设计倾向于展示分项，避免用户误以为“清理缓存”只影响在线播放。
