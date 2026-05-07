## 1. Artifact Status

- [x] proposal.md
- [x] design.md
- [x] specs/settings-page/spec.md
- [x] specs/settings-page-playback-preferences/spec.md
- [x] specs/audio-source-management/spec.md

## 2. Implementation

- [x] 2.1 在设置页新增账户入口与分组状态行，展示登录态、默认音质、缓存占用和当前音源摘要
- [x] 2.2 重组设置页分区顺序与文案层级：账户、播放与缓存、缓存明细、音源
- [x] 2.3 优化音源分组结构，分离当前音源摘要、导入入口和已保存音源列表
- [x] 2.4 保留现有设置页 callbacks、testTag 和业务链路兼容性
- [x] 2.5 更新设置页 Compose UI 测试，覆盖分组状态行、分组结构和关键操作回调
- [x] 2.6 调整设置页视觉风格，收敛大圆角、重阴影和指标卡网格，改为更轻的状态行与分区面板
- [x] 2.7 按本地 HTML 预览确认后的方向，将设置页落地为账户入口 + 分组列表，移除 dashboard 式总览卡和图标标题

## 3. Verification

- [x] 3.1 `openspec validate refactor-settings-page --type change`
- [x] 3.2 `./gradlew :app:testDebugUnitTest --tests "*SettingsScreenRobolectricTest" --tests "*SettingsViewModelTest"`
- [x] 3.3 `./gradlew :app:assembleDebug`
