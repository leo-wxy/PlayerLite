## ADDED Requirements

### Requirement: 歌单、专辑与艺人详情能力采用一致的 feature 结构
系统 SHALL 为歌单、专辑与艺人详情能力建立一致的 feature 结构，使页面承载、数据访问、远端映射与宿主入口遵循统一边界，而不是继续在 `app` 内各自演化。

#### Scenario: 三类详情能力遵循统一分层
- **WHEN** 歌单、专辑与艺人详情能力被迁移或整理
- **THEN** 每个详情能力都提供稳定的页面入口
- **AND** 每个详情能力都在自身边界内承载 repository、remote data source 与 mapper 等实现
- **AND** 三者采用一致的组织模式而不是三套彼此分叉的结构

#### Scenario: 新增详情类能力时可沿用既有模板
- **WHEN** 后续需要新增新的音乐详情类能力
- **THEN** 开发者可以沿用现有 detail feature 的统一结构扩展
- **AND** 不需要再把新的详情实现默认落到 `app`

### Requirement: 共享 detail chrome 能力通过公共边界复用
系统 MUST 将可复用的 detail chrome、滚动 handoff 与通用详情 UI 支撑能力保持在公共边界中，以供不同 detail feature 共享，而不是在各个详情能力中重复复制实现。

#### Scenario: 多个详情 feature 共享同一套 detail chrome
- **WHEN** 歌单、专辑与艺人详情页使用沉浸式 hero、折叠顶栏、minibar chrome 或滚动 handoff
- **THEN** 这些共享能力由公共边界统一提供
- **AND** 各详情 feature 仅实现自身差异化内容

#### Scenario: 详情 feature 迁移后既有入口保持可用
- **WHEN** 用户从搜索结果、用户中心或其他入口打开歌单、专辑或艺人详情页
- **THEN** 系统仍能打开对应详情页
- **AND** 入口调用方不需要重新拼接详情 feature 内部依赖
