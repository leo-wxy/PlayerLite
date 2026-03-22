## 1. 基线与约束对齐

- [x] 1.1 对齐 `playlist-detail-page` delta spec 的新增约束（detail shell 对齐专辑/歌手：沉浸式 hero + 折叠顶栏 + 状态栏适配；dynamic 统计区 card 化；歌曲/简介 双 Tab 展示；歌曲条目风格对齐专辑），明确需要改动的 UI 区域与实现锚点
- [x] 1.2 盘点并锁定歌单详情页关键 `testTag`（来自 `PlaylistDetailScreenRobolectricTest`），确定“尽量保留 tag 不变”的迁移策略
- [ ] 1.3 在模拟器/真机完成一次冒烟走查：从首页/搜索/用户中心进入歌单详情，确认本次 UI 刷新不改变“播放全部/点歌/分页/简介 Tab 切换/播放量回写”的既有语义

## 2. 头部（Hero）与动态信息区域刷新

- [x] 2.1 调整 `PlaylistDetailHero` 的信息层级与间距（封面、标题、作者、曲目数与播放入口更聚合），并保持关键 tag 稳定：`playlist_detail_hero_panel`、`playlist_detail_cover`、`playlist_creator_meta`、`playlist_track_count_meta`
- [x] 2.2 优化 hero 区文本对比度与可读性（必要时增加轻量 scrim/透明度策略），确保不同封面取色下白字可读
- [x] 2.3 将 `PlaylistDynamicMetaSection` 对齐为与专辑详情一致的 card 统计区样式（圆角、surface 背景、三列指标），保留 `playlist_dynamic_meta_section` tag，并确保 loading/error/empty 仍为局部承载
- [x] 2.4 引入与专辑/歌手详情一致的 detail shell：hero 全幅贴边并绘制至状态栏下方、overlay collapsing top bar、状态栏图标明暗适配；并将“播放全部”入口上移到 hero（保持 `playlist_play_all_button` tag）
- [x] 2.5 移除 hero 内歌单简介入口，统一由“简介”tab 承载全文内容，避免头部信息层级过重

## 3. 歌曲列表与简介 Tab

- [x] 3.1 引入“歌曲/简介”双 Tab + `HorizontalPager`，并复用 `rememberDetailVerticalScrollHandoffConnection` 处理外层 hero 与内层列表的垂直滚动 handoff
- [x] 3.2 将“歌曲”tab 承载歌曲列表、错误态与分页 footer，将“简介”tab 承载全文简介卡片，并保证长文可滚动

## 4. 歌曲列表样式与分页

- [x] 4.1 将歌曲条目样式对齐专辑详情页：采用圆角 Card 行样式（序号 + 标题 + “歌手 · 专辑” + 时长），保留 `playlist_track_<trackId>` tag，且点击仍触发 `onTrackClick(index)`
- [x] 4.2 调整歌曲列表标题区与列表区的整体密度（padding/间距策略），保持“歌曲列表”标题与列表节奏清晰（播放入口已迁移至 hero）
- [x] 4.3 校验分页加载与 footer 在新样式下行为不变：`DetailPagingFooter` 的 load more 触发、loading/error/end 文案展示正常，且底部内容不被 detail chrome 遮挡

## 5. 回归测试与验证

- [x] 5.1 保持或更新 `PlaylistDetailScreenRobolectricTest` 通过（优先不改 tag；如必须改动则同步更新断言），覆盖 hero、dynamic、Tab 切换、歌曲列表与分页 footer
- [x] 5.2 运行并修复单测：`./gradlew :app:testDebugUnitTest --tests "*PlaylistDetail*"`
- [x] 5.3 运行一次构建验证（至少 `:app:assembleDebug`），确保无编译回归
- [ ] 5.4 手动冒烟验证：加载态/错误态、分页追加、播放全部与点歌播放、播放成功后播放量回写并刷新 dynamic、简介 Tab 全文滚动与横滑切换
