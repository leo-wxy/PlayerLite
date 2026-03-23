# AGENTS.md

本文件是本仓库内唯一的 agent 规则源，已吸收旧 `AGENT.md` 的内容。

## 核心原则

- 默认使用中文回复，除非用户明确要求其他语言。
- 任何问题报告，第一步先听用户说明当前症状、复现路径、约束和希望的处理方式，再决定是否动代码。
- 对播放链路、状态机、网络时序、设备专属、难复现问题，先收集现场证据并分析，再改代码。
- 如果用户要求先抓日志、先 review、先讨论，就先做这些，不要擅自切换到实现。
- 现场一旦不可复现，不要继续补丁式修改；应停止、保留证据，等待下一次稳定复现。
- 复现证据优先级高于主观推断、孤立截图解释或从代码反推状态。
- 避免宽泛的猜测性修复，证据拿到后一次只改一个根因链路。
- 修改任何文件前，必须先列出准备修改的文件和具体改动点。
- 对配置、构建、工作流、环境变量、Manifest、权限和工具配置等文件，修改前必须额外说明影响范围和回滚面。

## 停手与确认规则

- 用户一旦给出明确负反馈、质疑当前修改方向、或指出“不要自己改 / 不要继续猜 / 先别动代码”，必须立即停止继续改代码。
- 触发停手后，下一步只允许做以下三类动作之一：
  - 基于现有代码和现场证据定位问题，不改文件。
  - 明确说明当前判断里的已验证事实、推断和不确定项。
  - 先给出修改清单，等待用户确认后再改。
- 在用户负反馈后，禁止以“顺手修一下”“先试一版”“我先直接改掉”为理由继续自主修改。
- 如果连续两次判断未命中用户问题，必须停止补丁式修改；后续只能先给证据、定位结论和修改计划，得到用户明确同意后才能继续改文件。
- 未经验证，不得声称“已经修好”“已经去掉”“就是这个原因”。
- 对 UI、交互、视觉问题，截图、真机结果和用户指出的现场现象优先级高于主观代码判断。
- 没有真机、截图、日志或测试证据，不要下“已经修好”的结论。
- 对当前仓库中的任何 UI 修复任务，都优先按本文件中的停手、定位、确认流程执行。
- 当用户要求“强制按规则执行”时，不得再依赖默认自主模式，必须优先遵守本文件中的停手确认规则。

## 项目概览

- Android 音频播放器，技术栈为 FFmpeg + JNI + Media3。
- 主要模块：
  - `app/`：Compose UI、ViewModel、歌单域和应用侧运行时。
  - `playback-service/`：`MediaSessionService` 宿主、桥接契约和播放进程运行时。
  - `player/`：Kotlin API 和原生 C++ 播放/解码能力。
- 播放服务运行在独立的 `:playback` 进程；应用侧通过 `PlayerServiceBridge` 控制。

## 关键路径

- `app/src/main/java/com/wxy/playerlite/core/playlist/`
- `app/src/main/java/com/wxy/playerlite/feature/player/model/`
- `app/src/main/java/com/wxy/playerlite/feature/player/runtime/`
- `app/src/main/java/com/wxy/playerlite/feature/player/runtime/action/`
- `app/src/main/java/com/wxy/playerlite/feature/player/ui/`
- `playback-service/src/main/java/com/wxy/playerlite/playback/client/`
- `playback-service/src/main/java/com/wxy/playerlite/playback/model/`
- `playback-service/src/main/java/com/wxy/playerlite/playback/process/`
- `openspec/specs/`
- `openspec/changes/archive/`

## 网络基址

- 当前仓库配置的 API Base URL 是 `http://139.9.223.233:3000`。
- 该地址定义于：
  - `app/src/main/java/com/wxy/playerlite/core/AppContainer.kt`
  - `playback-service/src/main/java/com/wxy/playerlite/playback/process/PlaybackProcessRuntime.kt`
- 在验证或探测本仓库接口行为时，默认先检查并使用仓库内实际配置的基址。
- 除非用户明确要求，不要切换到外部镜像或公共代理域名做接口验证。

## 必要验证

在有实际行为改动后，默认执行以下验证：

```bash
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- 如果改动涉及原生 C++，`./gradlew :app:assembleDebug` 是必跑项。
- 对播放状态、设备专属或难复现问题，不要先跑这一套；先复现并抓日志，再在修复后验证。

## 编码规则

- 保持 `MainActivity` 足够薄，只承担 wiring 和生命周期桥接。
- Compose UI 尽量保持无状态：消费 state、抛出 callbacks。
- 编排逻辑放在 `PlayerRuntime` 和 runtime action handlers。
- 歌单增删改动和 active-index 行为，优先复用 `core/playlist/PlaylistController`。
- 保留 `feature/player/model/PlaybackState.kt` 中的播放状态常量语义。
- 跨进程队列契约统一使用 `MusicInfo`，不要散落成零散参数。

## OpenSpec 工作流

- 当前主 specs 包括：
  - `openspec/specs/background-playback-service/spec.md`
  - `openspec/specs/media-session-integration/spec.md`
  - `openspec/specs/playlist-management/spec.md`
  - `openspec/specs/playlist-persistence/spec.md`
- 活跃变更位于 `openspec/changes/<change>/`。
- 已归档变更位于 `openspec/changes/archive/<date>-<change>/`。
- 保持 tasks/specs 与实现同步；归档前先把 delta specs 同步回 main specs。

## Git 规范

- 不要回退无关的本地修改。
- 除非用户明确要求，不要 force push。
- 不要提交本地或工具产物：
  - `**/build/`
  - `.kotlin/`
  - `.opencode/`
- 只有在用户明确要求且提交未 push 的情况下，才允许 amend。

## Multiagent 默认规则

- 当用户明确允许或要求使用 multiagent 时，可以并行使用 multiagent 推进实现，但写入范围必须清晰隔离，主线程负责整合和最终验证。
- 对本仓库，所有 spawned multiagents 默认使用 `gpt-5.4`，除非用户明确要求其他模型。

## Commit Message 规范

- 使用简洁的 conventional commit 前缀：`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、`chore:`。
- 本仓库优先使用中文主题行。
- 第一行聚焦意图和原因，不要写成纯机械的文件清单。

## 执行补充

- 若当前任务只是修正规则、文档或协作方式，优先修改项目本地规则文件，不要顺带改业务代码。
- 用户可通过明确指令覆盖本文件中的任意规则。
