## Purpose

定义首页能力作为独立 Home feature 模块的职责边界、宿主接入契约与对外动作语义，确保首页发现流不再默认落回 `app` 内部实现。

## Requirements

### Requirement: 首页能力采用独立 Home feature 模块承载
系统 SHALL 将首页发现流、首页页面状态管理、首页数据访问、首页远端映射与首页专属 UI 模型收敛到独立的 Home feature 模块中，而不是继续让这些实现默认落在 `app` 内部。

#### Scenario: 独立 Home feature 承载首页核心实现
- **WHEN** 首页能力被迁移到独立模块
- **THEN** 首页 screen、ViewModel、repository、remote data source、JSON mapper、UI model 与 layout spec 由 Home feature 自身承载
- **AND** `app` 不再继续保存这些首页核心实现的副本

#### Scenario: 首页迁移后主壳入口保持不变
- **WHEN** 用户启动应用并进入首页 Tab
- **THEN** 系统仍通过主壳展示首页概览态
- **AND** 首页悬浮搜索框、首页发现流、横向歌曲推荐区块与每日推荐入口继续可用

### Requirement: 宿主通过稳定入口接入 Home feature
系统 MUST 让宿主通过 Home feature 暴露的稳定入口、宿主依赖契约或工厂方法接入首页能力，而不是继续直接构造 Home feature 的内部 repository、remote data source 或 mapper。

#### Scenario: 宿主通过工厂与宿主依赖契约接入首页能力
- **WHEN** `app` 需要装配首页能力
- **THEN** 宿主通过 Home feature 暴露的 service factory、host dependencies 或等价稳定入口完成接入
- **AND** `app` 不需要直接引用首页内部的 remote data source、mapper 或默认实现类

#### Scenario: 宿主继续以主壳方式承载首页
- **WHEN** `MainActivity` 或其他主壳宿主接入 Home feature
- **THEN** 宿主仍负责 Tab 选择、Activity 宿主和顶层 route 适配
- **AND** 首页页面内容本身由 Home feature 提供的稳定 screen / state 边界承载

### Requirement: 首页 feature 对外暴露宿主中立的动作语义
系统 SHALL 让 Home feature 使用宿主中立的首页动作与内容目标模型描述跳转、播放与次级操作，而不是继续直接依赖宿主内部的路由类型或其他 feature 的内部目标类型。

#### Scenario: 首页内容跳转使用语义化内容目标
- **WHEN** 首页 feature 需要表达“打开歌手”“打开专辑”“打开歌单”“打开每日推荐”或“打开外部链接”等动作
- **THEN** Home feature 通过自身定义的内容目标模型表达这些语义
- **AND** 宿主负责将这些语义目标翻译为实际的 Activity 跳转或外部链接打开行为

#### Scenario: 首页播放动作不依赖宿主内部内容路由类型
- **WHEN** 首页歌曲推荐区块触发“替换队列并打开播放器”或“下一首播放”等播放相关动作
- **THEN** Home feature 通过自身定义的首页动作模型对外表达这些动作
- **AND** 宿主不需要再通过首页 mapper 直接消费宿主内部路由类型才能完成播放闭环
