## 1. 搜索 Module 拆分与装配边界

- [x] 1.1 新增 `:feature-search` module，并在 `settings.gradle.kts` 与对应 `build.gradle.kts` 中接入 Compose、Lifecycle、`network-core` 等搜索所需依赖
- [x] 1.2 将当前 `app/src/main/java/com/wxy/playerlite/feature/search/` 下的搜索页面、状态模型、ViewModel、Repository、remote data source 与 mapper 迁入新 module
- [x] 1.3 去掉 `SearchViewModel` 对 `AppContainer` 的直接依赖，改为由宿主 `app` 提供搜索所需依赖装配入口

## 2. 搜索结果类型建模与本地映射

- [x] 2.1 定义与接口类型对齐的 `SearchResultType`、本地结果模型与详情关联 `route target`，覆盖单曲、歌手、专辑、歌单等首批类型
- [x] 2.2 将 `/cloudsearch` 结果解析拆成按类型提取 payload、按类型映射 item、生成详情关联目标的 mapper 结构
- [x] 2.3 调整 `SearchRepository` 搜索接口，使其支持按选中结果类型请求数据，并继续保持热搜、建议、历史搜索与 cookie / csrf 自动透传能力不回退
- [x] 2.4 将协议层搜索类型注册扩展为与接口文档一致，覆盖 `1/10/100/1000/1002/1004/1006/1009/1014/1018/2000`，并为 `1018` 综合与 `2000` 声音预留独立映射入口

## 3. 搜索页类型化结果承载

- [x] 3.1 调整 `SearchUiState` 与 `SearchViewModel`，补充当前查询、当前选中结果类型、可切换类型集合与类型化结果状态
- [x] 3.2 在搜索结果区增加结果类型切换承载，并为不同结果类型实现差异化 renderer，而不再复用单一结果卡片
- [x] 3.3 调整结果点击事件，让搜索页向宿主层抛出稳定的详情关联目标，为后续进入不同详情页预留接线基础
- [x] 3.4 将结果区改成接近 `ViewPager` 的页状态语义：同一查询下各类型页首次进入请求一次，已加载页切回直接复用，不因切换再次整页闪烁或重拉
- [x] 3.5 将顶部结果类型切换栏扩展为覆盖全部文档类型，并保持横向可滑动承载与 pager 同步
- [x] 3.6 为顶部结果类型切换栏增加“当前选中项尽量居中”的平滑滚动效果

## 4. 宿主接线与回归验证

- [x] 4.1 在 `app` 中补齐 `:feature-search` 的装配、Manifest 暴露与搜索入口接线，确保搜索页迁出后入口行为保持不变
- [x] 4.2 补充 mapper / repository / ViewModel 单元测试，覆盖结果类型映射、类型切换、详情关联目标生成与 cookie 自动透传约束
- [x] 4.3 补充搜索页 UI 验证，覆盖结果类型切换、不同类型结果展示与点击结果项抛出详情关联目标的行为
- [x] 4.4 按仓库要求运行 `./gradlew :playback-service:testDebugUnitTest`、`./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`，确认模块拆分与搜索类型展示改动未破坏现有构建
- [x] 4.5 补充会话内页状态复用与 pager 交互回归验证，并重新执行 `:feature-search`、`:app`、`:playback-service` 所需测试与构建命令
- [x] 4.6 补充全量类型切换栏与剩余类型展示回归验证，并重新执行 `:feature-search`、`:app`、`:playback-service` 所需测试与构建命令
- [x] 4.7 补充结果类型栏居中滚动回归验证，并重新执行相关 `feature-search` / `app` 验证命令
