## Status

该 change 已被拆分为更小的子 change 重新提交，后续应优先参考：
- `unify-playable-contract-and-queue-metadata`
- `stabilize-online-playback-and-cache`
- `add-detail-page-playback-entry`
- `refresh-player-expanded-page`

这个大 change 当前仅保留为拆分参考，不建议再直接作为单个归档单元使用。

## Why

当前歌单详情页已经能展示头部信息和歌曲列表，但还缺少“播放全部”与“点按某首歌后按当前歌单上下文开始播放”的闭环，导致详情页内容与真实播放能力仍然割裂。更关键的是，歌单详情接口目前只能稳定提供歌曲标识、时长和封面信息，真实播放仍需要结合缓存完整性判断额外解析音乐 URL；现有播放列表操作与 `MusicInfo` 结构也需要一起调整，同时还要兼容本地播放条目，让播放进程只依赖最小可播放契约，而不是强绑完整在线 `MusicInfo`。

## What Changes

- 修改歌单详情页交互，在头部增加“播放全部”入口，并要求点击任一歌曲时以当前歌单完整曲目集合作为播放列表、以被点击歌曲作为激活项开始播放，而不是退化为单曲播放。
- 修改歌手详情页交互，在热门歌曲区域提供“播放全部”入口，并要求该入口与歌曲行点击都遵循统一的 index 播放语义：直接以当前详情上下文的完整曲目集合替换当前播放列表，再以目标 index 作为激活项开始播放。
- 修改专辑详情页交互，在歌曲列表区域补齐带播放图标的胶囊式“播放全部”入口，并要求该入口与歌曲行点击同样遵循统一的 index 播放语义：直接以当前专辑详情上下文的已加载曲目集合替换当前播放列表，再以目标 index 作为激活项开始播放。
- 修改歌单详情页信息承载，允许页面额外加载歌单详情动态数据，用于补充评论数、收藏状态与播放数等信息，并在需要时接入播放量更新入口以保持详情信息更完整。
- 修改受保护在线播放能力，要求系统先基于缓存完整性判断是否需要联网，再基于歌单详情返回的歌曲 id 解析真实播放 URL，并根据登录态、音质等级与接口返回结果构建可播放或可解释的在线曲目状态，而不是假定详情接口已直接给出最终播放地址。
- 修改播放列表能力，要求当前播放列表支持基于外部歌单进行整体替换或切换，并在后续继续支持切歌、删除条目与激活项衔接。
- 修改详情页播放装配链路，要求歌单/歌手/专辑等外部播放入口以 `songId` 作为最小开播前提，先快速设置可播放列表，再统一通过 `/song/detail` 分页异步补齐标题、歌手、专辑、封面与时长等元数据，而不是各自依赖页面接口返回的局部字段。
- 修改播放列表删除语义，要求删除当前激活项或非激活项后都能保持剩余列表、激活项与后续播放导航一致，而不是出现激活丢失或切歌错位。
- **BREAKING** 调整 `MusicInfo` 与共享可播放契约，使在线 `MusicInfo` 能以语义化字段名承载稳定标识、播放地址、展示信息、时长、封面信息、歌单上下文与在线播放附加状态，并对 `/song/detail` 等接口的原始字段完成明确映射；播放进程则仅依赖最小可播放基类，以兼容潜在的 `LocalMusicInfo` 或其他本地条目模型。
- 对齐歌单详情页播放入口、播放列表管理与共享播放契约的测试覆盖，确保歌单内指定播放、播放全部、列表删除与列表切换行为可以被稳定验证。

## Capabilities

### New Capabilities
- 无

### Modified Capabilities
- `playlist-detail-page`: 补充歌单详情页的播放入口与动态信息契约，要求页面提供“播放全部”按钮，将歌曲点击行为定义为“在当前歌单中指定播放某首歌”，并能承载评论数、收藏状态、播放数等动态信息。
- `artist-detail-page`: 补充歌手详情页的“播放全部”契约，要求热门歌曲区域提供整体播放入口，并在触发时以当前歌手热门歌曲集合替换当前播放列表，而不是只追加单曲或保留旧队列。
- `album-detail-page`: 补充专辑详情页的“播放全部”与点歌播放契约，要求专辑详情能够在歌曲列表标题右侧提供带播放图标的胶囊式播放入口，并以当前专辑已加载曲目集合作为新的播放上下文替换当前播放列表。
- `playlist-management`: 补充从歌单详情替换当前播放列表、在列表中切换激活曲目以及删除条目后的衔接契约，要求这些操作保持列表顺序与激活项语义稳定。
- `authenticated-online-playback-access`: 补充基于歌曲 id 解析在线播放 URL、区分完整可播与试听片段、以及受限音源失败兜底的契约，而不是只定义登录前置卡口。
- `playback-state-authority`: 调整 app 与后台播放服务共享的可播放 DTO 契约，要求上层 `MusicInfo` / 本地条目模型能够映射到统一的最小可播放基类，并让当前曲目、时长、封面与歌单来源上下文得到一致投影，而不是依赖零散参数拼装。

## Impact

- 影响 `app/src/main/java/com/wxy/playerlite/feature/playlist/` 下的歌单详情页 Activity、Screen、ViewModel、repository 与歌曲条目交互接线。
- 影响 `app/src/main/java/com/wxy/playerlite/feature/artist/` 下的歌手详情页热门歌曲区域与“播放全部”入口接线。
- 影响 `app/src/main/java/com/wxy/playerlite/feature/album/` 下的专辑详情页歌曲列表区域与“播放全部”入口接线。
- 影响歌单详情数据获取链路，新增或调整基于歌单 id 请求 `/playlist/detail/dynamic` 与 `/playlist/update/playcount` 的数据访问与结果映射。
- 影响在线播放地址解析链路，新增或调整基于歌曲 id 请求 `/song/url`、`/song/url/v1`、`/song/url/v1/302` 与 `/check/music` 的数据访问、可用性判定与结果映射。
- 影响缓存与播放准备链路，需要在决定是否解析在线 URL 前判断缓存是否已完整，并把详情接口已有 `durationMs` 与封面信息透传到共享播放结构。
- 影响详情页播放网关与播放列表状态链路，需要支持“立即按 `songId` 入队开播 + 后台分页 enrichment 元数据 + 局部回填现有播放列表”的新装配方式。
- 影响 `song/detail` 等详情字段到共享播放结构的 mapper，需要把 `id`、`name`、`ar`、`al`、`dt` 等原始字段收口为可读的 `MusicInfo` 命名。
- 影响 `app/src/main/java/com/wxy/playerlite/core/playlist/` 与 `app/src/main/java/com/wxy/playerlite/feature/player/runtime/` 下的播放列表替换、激活项切换、删除衔接和列表来源管理。
- 影响 `playback-contract/src/main/java/com/wxy/playerlite/playback/model/MusicInfo.kt`、潜在的本地播放条目模型，以及 app / playback-service 中依赖共享可播放契约的播放命令、状态投影与测试代码。
- 需要考虑在线播放 URL 在受限场景下的 403 / referrer 风险，并为原生链路提供合适的请求头或等效兜底策略，而不是假设 Web `head meta` 方案可直接照搬。
- 需要为歌单详情页播放入口、播放列表切换/删除语义与 `MusicInfo` 结构调整补充或更新单元测试、Robolectric 测试与服务侧测试。
