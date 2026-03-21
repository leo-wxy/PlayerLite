## Why

现有专辑详情页已经具备基础内容、动态信息、分页歌曲列表和播放入口，但页面层次与数据覆盖仍停留在首版承载，没有对齐你刚提供的专辑详情 UI 参考，也还没有接入数字专辑详情、销量和登录态百科等新数据来源。现在已经明确需要把专辑详情页改造成更接近歌手详情重构后的信息组织方式，并补齐 `/digitalAlbum/detail`、`/digitalAlbum/sales`、`/ugc/album/get`、`/album`、`/album/detail/dynamic` 这组接口的协同承载，因此需要发起一次面向现有专辑详情能力的重构变更。

## What Changes

- 重构专辑详情页的页面结构与视觉层次，使其整体效果与现有歌手详情页的重构方向保持一致，但语义切换为专辑详情承载，而不是只替换 tab 文案。
- 在专辑详情页中补充接入 `/digitalAlbum/detail`、`/digitalAlbum/sales`，用于承载数字专辑特有的信息与销量表现，并明确这些字段在无数据或请求失败时的非阻塞降级行为。
- 将专辑简介/百科承载切换为优先使用登录态接口 `/ugc/album/get`，同时保留与 `/album` 返回内容的协同兜底，确保简介区既能展示两行预览，也能进入完整内容浮层。
- 继续使用 `/album` 承载专辑基础信息与曲目列表，并使用 `/album/detail/dynamic` 承载评论、分享、收藏等动态元信息；需要按新的页面结构重新组织这些内容在头部、tab 和列表区域中的呈现位置。
- 明确新的 UI 方向约束：参考你提供的 HTML 视觉结构，但不要求逐像素复刻；优先复用当前歌手详情页已经验证过的布局组织、滚动行为与风格语言，再替换成专辑语义、专辑内容区和对应 tab。
- 保留现有专辑详情页的加载态、失败态、续页、播放全部和点歌播放能力，确保重构后不会丢失已有可恢复性和播放上下文语义。

## Capabilities

### New Capabilities

无。本次变更聚焦于重构和扩展既有专辑详情能力，不新增独立 capability。

### Modified Capabilities

- `album-detail-page`: 扩展专辑详情页的需求边界，使其除了现有专辑内容、动态信息、歌曲分页与播放入口外，还需要承载数字专辑详情与销量、登录态专辑百科信息，并按新的 UI 参考和歌手详情页既有重构风格重新组织页面结构、tab 语义与详情头部内容。

## Impact

- 受影响代码主要位于 `app/src/main/java/com/wxy/playerlite/feature/album/`，包括 `AlbumDetailActivity`、`AlbumDetailViewModel`、`AlbumDetailRepository` 及相关 Compose UI 结构。
- 需要扩展专辑详情远端数据访问与映射逻辑，引入 `/digitalAlbum/detail`、`/digitalAlbum/sales`、`/ugc/album/get`，并继续整合 `/album` 与 `/album/detail/dynamic`；其中百科接口依赖现有登录态请求链路。
- 需要同步更新相关测试，重点覆盖 repository 映射、ViewModel 状态分支、数字专辑信息与销量的展示兜底，以及专辑详情页的 Robolectric UI 行为。
- 设计阶段需要明确哪些变化属于 spec 级需求收敛，哪些只是实现和视觉重排，避免把“参考歌手详情页效果”误写成逐像素复制要求。
