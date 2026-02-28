## 1. 工程接入与服务骨架

- [x] 1.1 在 `app` 模块引入 Media3 session/controller 依赖并同步构建配置
- [x] 1.2 新建 `MediaSessionService` 实现类并在 `AndroidManifest.xml` 完成服务声明与前台服务能力配置
- [x] 1.3 补齐媒体通知渠道与服务启动/停止入口，确保播放态可进入前台服务

## 2. MediaSessionService 核心能力

- [x] 2.1 在服务内创建并维护唯一 `MediaSession`，实现启动初始化与销毁释放
- [x] 2.2 建立 `MediaSession` 标准传输控制（播放/暂停/上一首/下一首）到现有播放器命令的映射
- [x] 2.3 实现服务端命令串行处理机制，避免 UI 与外部媒体入口并发命令竞争

## 3. 播放内核适配与状态同步

- [x] 3.1 实现播放器适配层，将现有播放内核状态映射为会话可消费状态模型
- [x] 3.2 同步 `PlaybackState` 与 `MediaMetadata`，覆盖播放状态变化与媒体项切换
- [x] 3.3 根据队列边界动态更新可执行操作，正确限制首项上一首与末项下一首
- [x] 3.4 完成服务重建恢复流程，支持恢复最小上下文并在异常时安全降级为空闲态

## 4. 应用内控制通道迁移

- [x] 4.1 在应用层接入 `MediaController`，建立 UI 到服务的统一控制通道
- [x] 4.2 将 `PlayerViewModel` 的播放控制调用迁移到会话控制路径并保持现有交互语义
- [x] 4.3 打通外部控制（通知栏/耳机按键/系统媒体中心）与应用内状态一致性回传

## 5. 验证与回归

- [x] 5.1 为状态映射、命令分发与队列边界行为补充单元测试
- [x] 5.2 验证后台播放、界面重建重连、服务回收恢复等关键场景并补充集成测试
- [x] 5.3 运行 `./gradlew :app:testDebugUnitTest` 与 `./gradlew :app:assembleDebug`，修复失败后再次验证

## 6. 进程隔离与包结构整理

- [x] 6.1 将 `PlayerMediaSessionService` 迁移到独立播放进程并移除对主进程业务 Runtime 的依赖
- [x] 6.2 按职责拆分 package：`com.wxy.playerlite.playback.process`（播放进程）与 `com.wxy.playerlite.playback.client`（主进程桥接）
- [x] 6.3 将播放服务相关代码下沉到独立 `:playback-service` 模块并通过 app 依赖接入
- [x] 6.4 将 Service 声明与前台播放权限迁移到 `:playback-service` 的 Manifest，主模块仅保留业务入口

## 7. 跨进程播放契约重构（合并到当前变更）

- [x] 7.1 新增统一 `MusicInfo` 模型，统一主进程与播放进程的数据交互契约（替代分散参数传递）
- [x] 7.2 将播放队列交互改为整队列同步（`setMediaItems`），并保留 activeIndex 作为主进程业务选择的唯一输入
- [x] 7.3 播放进程按队列维护当前曲目与边界能力，补齐上一首/下一首命令可用性与索引切换逻辑
- [x] 7.4 主进程通过远端回传的 `currentMediaId` 同步本地 active item，保证系统媒体入口与 UI 选择一致
- [x] 7.5 修正播放进程 `playWhenReady` 状态语义与 `STATE_BUFFERING` 映射，避免点击播放后 UI 误回落到 stopped
- [x] 7.6 修复通知栏 seek 同曲目时触发重新 `playCurrent()` 导致从头播放的问题，改为同曲目仅执行位置 seek
- [x] 7.7 打通播放进程 `PlaybackOutputInfo` 到主进程 UI 的跨进程透传，恢复输出路由信息展示
- [x] 7.8 修正 `RemotePlaybackSnapshot` 读取来源为当前曲目 `MediaMetadata`（含 extras/subtitle），避免从会话级 metadata 读取导致 output 丢失
- [x] 7.9 修复首次播放时 `currentMediaItem.extras` 不含 output 字段导致透传回退失效的问题，改为“当前曲目 extras 优先，缺失时回退会话级 extras”
- [x] 7.10 引入 `MediaSession.setSessionExtras` 作为 output/status 的稳定跨进程侧信道，解决首次播放期间 `currentMediaItem.metadata` 更新时序导致的展示缺失
- [x] 7.11 为 `MediaSession` 显式设置 `sessionActivity`，修复系统媒体控件点击后无法回到 App 的问题
- [x] 7.12 修复播放启动时重置 `playbackOutputInfo` 导致首播输出信息被覆盖的问题，保留最新 output 配置直到曲目切换

## 8. 工程结构重整（合并到当前变更）

- [x] 8.1 将播放列表能力迁移到 `com.wxy.playerlite.core.playlist`，统一核心域能力命名与目录层级
- [x] 8.2 将 `feature/player` 下运行时协作类下沉到 `feature/player/runtime`，减少 Feature 根目录拥挤
- [x] 8.3 同步重构单元测试目录与包名（`PlaylistControllerTest`、`PreparedSourceSessionTest`）以匹配新结构
- [x] 8.4 更新根 README 的模块划分与工程结构示意，补充 `playback-service` 与新的分层目录说明
- [x] 8.5 将 `feature/player` 的状态模型下沉到 `feature/player/model`，集中管理 `PlayerUiState` 与播放状态常量
- [x] 8.6 将 `feature/player` 的交互编排器下沉到 `feature/player/runtime/action`，明确“入口层 / 运行时 / action”边界
- [x] 8.7 清理迁移后遗留的空目录（旧 `playlist`、旧 `playback`、旧 `service`、空测试目录），减少目录噪音
