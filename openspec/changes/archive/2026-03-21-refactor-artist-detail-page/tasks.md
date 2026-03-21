## 1. 数据层与接口迁移

- [x] 1.1 拆分 `feature/artist` 下的歌手详情数据层边界，至少把模型、remote data source、mapper 与 repository 职责从现有大文件中收敛为更清晰的实现结构
- [x] 1.2 将歌手百科数据源从 `/artist/desc` 切换到 `/ugc/artist/get`，继续复用现有 `requiresAuth = true` 的登录态透传链路，并保留头部主体对 `/artist/detail` 的基础信息承载
- [x] 1.3 接入 `/artist/album`，实现歌手专辑首批加载与基于 `limit/offset` 的分页数据模型，并输出稳定的 `ArtistAlbumRow` / 分页结果页面模型
- [x] 1.4 补充歌手详情数据层测试，覆盖新百科接口映射、未登录或空内容降级、专辑首批映射、分页参数传递以及续页结果拼接语义
- [x] 1.5 接入 `/artist/detail/dynamic` 与 `/artist/follow/count`，为头部补充是否关注、视频数、粉丝数等增强字段，并明确这两类数据仅作为头部软依赖
- [x] 1.6 补充头部增强相关测试，覆盖软依赖失败降级、`fansCount` 原始值映射以及 `w` 阈值格式样例

## 2. 状态机与播放语义整理

- [x] 2.1 扩展 `ArtistDetailViewModel` 状态模型，新增 `albumsState`，并把页级失败、百科区块失败、热门歌曲区块失败和专辑区块失败明确拆开
- [x] 2.2 调整歌手详情重试逻辑，区分整页重试、区块重试与专辑续页加载，避免已成功内容被无意义重置
- [x] 2.3 保持现有“全部播放 / 点击热门歌曲播放”语义不变，并确保新增专辑区块不会污染热门歌曲的当前播放上下文
- [ ] 2.4 补充 `ArtistDetailViewModel` 测试，覆盖登录态百科成功/失败、专辑首批成功/失败、专辑续页成功/失败与重试后的状态迁移
- [x] 2.5 扩展头部展示状态与 formatter 边界，承载关注状态、视频数、粉丝数等增强字段，并收敛 `fansCount >= 10000` 时使用 `w` 单位、低于 `10000` 时展示具体数值的规则
- [x] 2.6 明确头部增强字段的降级语义，确保 `/artist/detail/dynamic` 与 `/artist/follow/count` 任一失败时，状态层仍保留基础头部与既有详情承载

## 3. 歌手详情页 UI 重构

- [x] 3.1 调整 `ArtistDetailActivity` 与 Compose Screen 的边界，让 Activity 只保留宿主装配，把详情壳层、头部、百科、热门歌曲、专辑区块拆成更稳定的 screen/section 结构
- [x] 3.2 将歌手详情页加载态改为保留详情页壳层与固定返回入口的承载方式，补齐当前全屏 loading 与主 spec 的差距
- [x] 3.3 按新的详情结构重构歌手头部、百科摘要/全文浮层、头像预览与热门歌曲区块，并为后续 HTML 参考样式对齐预留稳定插槽
- [x] 3.4 新增歌手专辑列表区块，承载首批专辑、尾部加载中、尾部失败重试与“没有更多内容”状态
- [ ] 3.5 补充歌手详情页 Robolectric/UI 测试，覆盖详情壳层加载态、百科降级、热门歌曲区块、专辑区块以及专辑分页尾部状态，并避免把内部 spacer 或过渡层 tag 当成主要验收条件
- [x] 3.6 按最新 UI 方向调整头部与内容区：头图全宽正方形且无圆角、简介预览两行、颜色走 theme roles、列表 item 为小间距卡片、百科承载视觉尽量填满，并确保固定头部正确避让状态栏安全区
- [ ] 3.7 补充 UI 测试，覆盖头部增强信息展示、百科承载填满预期以及顶部安全区/固定头部不回归
- [x] 3.8 收口顶部 compact top bar 与 sticky tabs 的职责分离，避免双重状态栏占位、标题与 tab 互相覆盖，并确保渐变与标题淡入由连续滚动进度驱动而非硬切
- [x] 3.9 收口 tab 切换滚动语义：切换 tab 时保持外层滚动位置稳定，各 tab 保留各自独立的内容滚动位置，不因切换被强制拉回顶部或覆盖为其他 tab 的滚动偏移
- [ ] 3.10 更新 artist detail 相关 Robolectric 断言，优先验证用户可见结果，例如当前 tab 内容、旧内容隐藏和简介卡片可见性，而不是依赖内部 spacer 或结构 tag

## 4. 宿主装配与入口回归

- [x] 4.1 调整 `AppContainer` 中歌手详情 repository 的装配，接入新的数据层边界并保持现有 Activity/Intent 用法不变
- [x] 4.2 检查搜索、首页、个人中心与播放器歌手入口的 artist detail 打开链路，确保本次重构后仍然传递稳定 `artistId`
- [x] 4.3 为 artist detail 入口闭环补充或更新回归测试，覆盖 `SearchRouteTarget.Artist` 与现有宿主跳转不回归

## 5. 实现验证

- [ ] 5.1 运行歌手详情相关单元测试与 Robolectric 测试，至少覆盖 repository、ViewModel 与 screen 三层
- [ ] 5.2 按仓库要求运行 `./gradlew :app:testDebugUnitTest`、`./gradlew :playback-service:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`
