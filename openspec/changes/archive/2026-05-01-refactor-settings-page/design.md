## Context

设置页当前所有功能都在同一个 `SettingsActivity.kt` 文件内完成，UI 由 `SettingsScreen` 组合账户、播放偏好、缓存、音源四个卡片。现有 ViewModel 与 repository / controller contract 已经能支撑功能闭环，本次不需要重写状态来源。

本次目标是页面结构重构，不是新增设置能力，也不是拆 feature module。优先做低风险 UI 整理：让用户在真实设置页式的分组列表中扫读当前关键状态，并在同一分组内完成对应操作。

## Goals / Non-Goals

**Goals:**
- 首屏提供账户入口，并在后续分组内直接展示默认音质、缓存占用、当前音源。
- 分区标题更清晰，减少重复说明文案和长表单感。
- 音源区区分导入动作、当前音源和已保存音源列表。
- 视觉上收敛为轻量分组列表：减少大圆角、重阴影、图标标题和重复卡片感，保持账户域红白主题但提升扫读效率。
- 继续复用现有 state、callbacks、repository、controller contract。
- 保留既有关键 testTag 或提供新的稳定 testTag 覆盖。

**Non-Goals:**
- 不新增新的设置项。
- 不修改缓存清理、音源导入、默认音质保存的业务语义。
- 不拆分新的 Gradle module。
- 不引入新的持久化 schema 或迁移。

## Decisions

### 决策一：使用分组列表，而不是 dashboard 式总览

设置页是工具型页面，分组列表比顶部指标区更接近真实移动端设置页。账户入口放在首屏顶部；默认音质、缓存占用、当前音源分别在自己的分组首行展示，避免重复摘要造成页面变重。

### 决策二：播放偏好与缓存上限合并为“播放与缓存”

默认音质和歌曲缓存上限都属于播放体验偏好，放在同一分组更符合用户理解。详细缓存占用和清理仍保留单独“缓存明细”分组。

### 决策三：音源导入入口放在列表之前

音源管理的主动作是导入新音源，其次才是管理已有音源。导入区放在列表之前，并在列表上方展示当前音源摘要，减少用户寻找入口的成本。

### 决策四：设置页使用轻量分组列表而不是重卡片堆叠

设置页是工具型页面，信息密度优先于装饰。分组容器使用 12dp 圆角、浅边框、无阴影；行内通过标题、副标题和值形成层级，避免页面变成过度设计的 dashboard。

## UI Structure

```text
SettingsScreen
  -> TopAppBar
  -> SettingsAccountSection
       -> 账户状态 / 登录或退出
  -> SettingsPlaybackPreferencesSection
       -> 默认音质入口
       -> 歌曲缓存上限
  -> SettingsCacheSection
       -> 缓存明细
       -> 刷新 / 清理
  -> SettingsAudioSourcesSection
       -> 当前音源摘要
       -> 在线导入 / 本地导入
       -> 已保存音源列表
```

## Verification

- `openspec validate refactor-settings-page --type change`
- `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest"`
- `./gradlew :app:assembleDebug`
