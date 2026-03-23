## Why

当前“个人主页/我的（User Center）”页面已经具备基础结构（头部资料区 + 内容承载区 + 本地歌曲入口 + 收藏内容 Tab），但仍偏“骨架化”，缺少用户在个人页最常用的高频信息与入口（例如最近播放、用户统计、更多收藏维度等），同时部分接口对齐存在改进空间（例如“收藏专栏”应使用 `/topic/sublist`）。

本变更基于现有页面实现做增量调整，并参考 [cc.html](/Users/wxy/Projects/player-lite/cc.html) 的信息层级与节奏，让个人页更像“可用的个人中心”，同时补齐所需的接口调用与数据映射。

## What Changes

- 在不推翻现有结构的前提下，调整个人页头部资料区与页面内容的排版密度、信息层级与操作入口布局，视觉节奏参考 `cc.html`（头像主视觉、昵称与会员信息、快捷入口等）。
- 增加“最近”相关承载位，用于展示与进入最近播放内容（歌曲为主，后续可扩展到视频/声音/歌单/专辑/播客），并接入对应接口。
- 补齐个人页需要展示的用户信息与统计数据（如账号信息、订阅计数、等级信息等），以提升已登录态下的“信息完整度”。
- 对齐并扩展个人中心的收藏内容数据源能力：
- 收藏歌手沿用现有 `/artist/sublist`。
- 收藏专栏调整为使用 `/topic/sublist`，并更新映射逻辑以适配实际返回结构。
- 增加收藏 MV 列表能力，并预留收藏/取消收藏视频与 MV 的动作入口（用于后续功能扩展）。
- 保持游客态与已登录态的边界清晰：游客态不发起需要登录的请求，并继续提供清晰的登录引导与本地歌曲入口。

## Capabilities

### New Capabilities

- 无（本次以增强现有“用户中心/个人主页”能力为主，不额外拆出全新 spec；如后续“最近播放”扩展为独立页面与完整交互，可再抽出独立 capability）。

### Modified Capabilities

- `user-center-tab-shell`: 扩展个人主页的信息架构（快捷入口、最近播放入口/区域、内容承载区结构微调），并对齐“收藏专栏”等内容来源与交互反馈。
- `user-account-session`: 扩展个人页需要的用户侧信息读取与投影（账号信息、订阅计数、等级信息等），确保登录态变化下个人页一致更新。

## Impact

- Affected UI / Modules:
- `app` 的用户中心页面组合与布局（`UserCenterScreen` / `UserCenterProfileHeader` 等）。
- `feature/main` 的个人页数据装配（Repository / ViewModel）以支撑新增的最近播放与用户信息展示。
- `user` module 的用户信息与会话相关远端数据源扩展（在不破坏现有会话恢复与鉴权逻辑的前提下）。
- `network-core` 的接口请求与 JSON 映射补齐（新增 endpoint 映射与模型）。

- Affected APIs (auth required for logged-in areas):
- 最近播放（可选 `limit`，默认 100）:
- `/record/recent/song`
- `/record/recent/video`
- `/record/recent/voice`
- `/record/recent/playlist`
- `/record/recent/album`
- `/record/recent/dj`
- 收藏/订阅:
- `/artist/sublist`（收藏歌手列表）
- `/topic/sublist`（收藏专栏）
- `/mv/sublist`（收藏 MV 列表）
- 收藏动作（后续扩展使用）:
- `/video/sub`（收藏/取消收藏视频）
- `/mv/sub`（收藏/取消收藏 MV）
- 用户信息与统计:
- `/user/detail`
- `/user/account`
- `/user/subcount`
- `/user/level`
- `/user/binding`
- 用户内容与动态（按页面规划逐步接入）:
- `/user/record`（播放记录，需 `uid`）
- `/user/follows`（关注列表，需 `uid`）
- `/user/followeds`（粉丝列表，需 `uid`）
- `/user/event`（用户动态，需 `uid`）
- `/user/playlist`（用户歌单，需 `uid`，当前能力已存在，新增内容需复用与对齐）

