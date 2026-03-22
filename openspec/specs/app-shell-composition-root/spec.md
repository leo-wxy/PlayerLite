## Purpose

定义 `app` 模块作为应用壳层与依赖装配根的职责边界，确保业务 feature 迁移后，宿主仍以稳定入口完成集成，而不是继续把实现细节堆回 `app`。

## Requirements

### Requirement: `app` 只承载应用壳层与依赖装配职责
系统 SHALL 将 `app` 模块定义为应用入口、主导航、宿主路由与依赖装配层，而不是继续作为新业务实现代码的默认落点。

#### Scenario: 主壳仍可承载现有主流程
- **WHEN** 应用启动并进入主壳层
- **THEN** `app` 继续提供 `Application`、主 `Activity`、一级导航与跨 feature 路由绑定
- **AND** 首页、用户中心、搜索入口、播放器入口与详情页入口保持可达

#### Scenario: 已迁移 feature 不再把核心实现留在 `app`
- **WHEN** 详情类或搜索类 feature 已被定义为独立 feature 能力
- **THEN** `app` 不再继续保存该 feature 的 repository、remote data source、json mapper 或页面实现副本
- **AND** `app` 仅通过稳定入口消费这些 feature

### Requirement: 宿主对 feature 的接入通过稳定入口完成
系统 MUST 让 `app` 通过 feature 暴露的稳定入口、宿主依赖契约或工厂方法接入 feature，而不是直接拼接 feature 内部实现细节。

#### Scenario: 宿主接入独立 feature 时不依赖内部实现类
- **WHEN** `app` 需要接入某个独立 feature
- **THEN** `app` 通过 feature 暴露的 Activity 入口、依赖契约或工厂方法完成集成
- **AND** `app` 不需要直接构造该 feature 的内部 repository 或 remote data source

#### Scenario: feature 迁移后宿主路由保持稳定
- **WHEN** 某个已迁移 feature 被从 `app` 内部实现迁移到独立模块
- **THEN** 宿主侧路由与调用入口保持可预期
- **AND** 现有调用方不需要感知该 feature 的内部包结构变化
