## 1. 页面骨架对齐

- [x] 1.1 将专辑详情外层改造成 hero、sticky tabs 与 pager 组合结构，并保持现有加载态、失败态和返回入口不回退
- [x] 1.2 新增 `歌曲` / `简介` 两个 tab，并将简介内容收敛到 `简介` tab，移除头部重复简介入口

## 2. 滚动与内容承载

- [x] 2.1 复用 `DetailVerticalScrollHandoff`，让专辑页外层头部与内层 tab 列表形成连续滚动接力
- [x] 2.2 将动态信息卡上移到 header 底部、sticky tab 上方，在 `歌曲` tab 保留播放全部和分页曲目，在 `简介` tab 承载完整简介正文，并保留两个 tab 各自的滚动位置

## 3. 数据扩展与视觉收口

- [ ] 3.1 接入 `/digitalAlbum/detail` 和 `/digitalAlbum/sales`，补齐数字专辑详情与销量承载
- [ ] 3.2 接入登录态 `/ugc/album/get`，并与 `/album` 的简介内容做协同兜底
- [ ] 3.3 根据真机反馈收口专辑页 compact top bar / sticky tabs 的视觉细节，决定是否补 overlay top chrome

## 4. 验证与回归

- [ ] 4.1 补 repository、ViewModel 和 UI 测试，覆盖 tab 切换、滚动接力、分页和失败态
- [ ] 4.2 运行专辑详情相关 Gradle 验证命令并处理回归问题
