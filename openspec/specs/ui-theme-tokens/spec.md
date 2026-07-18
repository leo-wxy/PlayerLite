## Purpose

定义应用跨首页、搜索页、播放器展开页与播放列表半浮层的共享主题 token，确保多页面复用同一套品牌色、语义色与容器层级，而不是各自维护分裂的临时视觉常量。

## Requirements

### Requirement: 应用提供共享主题色 token
系统 SHALL 为首页、搜索页、播放器展开页与播放列表半浮层提供共享主题色 token；浅色方案以 `primary #e53935`、`secondary #616161`、`tertiary #0087a0`、`neutral #f9f9fb` 作为核心基准，深色方案 SHALL 提供语义等价映射，而不是让各页面分别写死彼此不一致的色值。

#### Scenario: 浅色方案映射核心语义
- **WHEN** 系统以浅色主题渲染首页、搜索页、播放器展开页或播放列表半浮层
- **THEN** 主操作与激活态使用 `primary`
- **AND** 次级信息与辅助文字使用 `secondary`
- **AND** 辅助强调或补充状态使用 `tertiary`
- **AND** 大面积背景与浅底容器使用 `neutral` 或其派生中性色

#### Scenario: 深色方案保持语义等价
- **WHEN** 系统以深色主题渲染同一组页面
- **THEN** 系统为主操作、次级信息、辅助强调和中性基底提供语义等价的深色映射
- **AND** 不直接把浅色 `neutral` 作为深色大面积背景使用

### Requirement: 应用通过共享主题 contract 暴露默认皮肤
系统 SHALL 通过稳定的共享主题 contract 暴露默认皮肤，使 `app` 与 `feature-discovery` 使用同一主题源，而不是各自维护一套平行的色板定义。

#### Scenario: 多模块复用同一主题源
- **WHEN** app 主壳层与独立搜索页分别初始化 Compose 主题
- **THEN** 两者都从同一共享主题 contract 获取 light / dark color scheme
- **AND** 页面级 theme wrapper 只负责各自的 typography 或局部组合，不再复制核心色板

#### Scenario: 后续新增皮肤无需回到页面重写颜色
- **WHEN** 后续需要新增另一套品牌主题
- **THEN** 系统可以通过扩展共享主题 contract 中的 palette 与 token 映射完成主题接入
- **AND** 首页、搜索页、播放器和播放列表的页面代码不需要重新写死新的颜色常量

### Requirement: 页面通过语义 token 复用共享主题
系统 SHALL 通过稳定的语义 token 复用共享主题，以保证首页、搜索页、播放器和播放列表中的搜索框、banner、结果列表、minibar 与高亮项保持一致的视觉语言，而不是在每个界面单独拼接临时颜色和透明度。

#### Scenario: 主强调色仅用于关键操作与激活状态
- **WHEN** 页面渲染主操作按钮、当前选中类型、当前播放项或显式高亮入口
- **THEN** 系统使用同一主强调语义突出这些元素
- **AND** 不让多个高饱和色同时竞争主视觉注意力

#### Scenario: 中性色用于容器与弱分隔
- **WHEN** 页面渲染搜索框、榜单容器、胶囊底栏、sheet 背景或弱分隔线
- **THEN** 系统优先使用中性色与其派生层级表达容器关系
- **AND** 文本、图标与分隔保持足够可读性而不过度抢占视觉焦点
