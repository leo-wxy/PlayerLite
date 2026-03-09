## Automated Verification

- `./gradlew :app:testDebugUnitTest --tests "*PlaybackModeCycleTest" --tests "*PlayerRuntimeProjectionTest" :playback-service:testDebugUnitTest --tests "*QueueSyncPolicyTest" --tests "*PlayerSessionPlayerTest"`
- `./gradlew :playback-service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`

## Manual Checks

- 打开播放器主界面，确认主控区左侧显示图标化的播放模式入口，而不是倍速入口。
- 连续点击播放模式入口，确认按“列表 → 单曲 → 随机”循环切换，且不弹出对话框。
- 每次切换播放模式后确认出现对应的 Toast 提示，且当前曲目不被强制切换。
- 确认列表循环、单曲循环、随机播放三种模式在主控区入口上具有足够可区分的图标/强调色反馈，其中随机播放具备更活跃的轻动态强调。
- 进入随机播放后打开播放列表，确认默认按随机顺序展示，并出现 `显示原始顺序` 复选框。
- 勾选 `显示原始顺序` 后确认列表切回原始顺序展示，但当前播放项和后续随机顺序不变。
- 在随机顺序视图中确认拖拽排序不可用；切回原始顺序视图后确认拖拽排序恢复可用。
- 在随机模式下点击播放列表任意项，确认只切换当前播放项，不重新洗牌。
- 关闭并重新启动应用，确认播放模式、随机顺序与 `显示原始顺序` 状态可以恢复，且不会被空闲 service 的默认模式覆盖。
