# AGENT.md

Guidelines for coding agents working in this repository.

## Project Overview

- Android audio player built with FFmpeg + JNI + Media3.
- Main modules:
  - `app/`: Compose UI, ViewModel, playlist domain, app-side runtime.
  - `playback-service/`: `MediaSessionService` host, bridge contract, playback process runtime.
  - `player/`: Kotlin API plus native C++ playback/decoder.
- Playback service runs in the dedicated `:playback` process; app-side control goes through `PlayerServiceBridge`.

## Key Paths

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

## Network Base URL

- The current repo-configured API base URL is `http://139.9.223.233:3000`.
- This base URL is defined in:
  - `app/src/main/java/com/wxy/playerlite/core/AppContainer.kt`
  - `playback-service/src/main/java/com/wxy/playerlite/playback/process/PlaybackProcessRuntime.kt`
- When verifying or probing API behavior for this repository, always check the in-repo configured base URL first and use it by default.
- Do not switch to external mirrors or public proxy domains for API verification unless the user explicitly asks for that.

## Required Validation

Run these after meaningful behavior changes:

```bash
./gradlew :playback-service:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- If native C++ is touched, `:app:assembleDebug` is mandatory.

## Coding Rules

- Keep `MainActivity` thin (wiring + lifecycle bridge only).
- Keep Compose UI mostly stateless: consume state and emit callbacks.
- Place orchestration logic in `PlayerRuntime` and runtime action handlers.
- Reuse `core/playlist/PlaylistController` for add/remove/move/active-index behavior.
- Preserve playback-state constants in `feature/player/model/PlaybackState.kt`.
- Use `MusicInfo` as the cross-process queue contract instead of scattered parameters.

## OpenSpec Workflow

- Main specs currently include:
  - `openspec/specs/background-playback-service/spec.md`
  - `openspec/specs/media-session-integration/spec.md`
  - `openspec/specs/playlist-management/spec.md`
  - `openspec/specs/playlist-persistence/spec.md`
- Active changes live in `openspec/changes/<change>/`.
- Archived changes live in `openspec/changes/archive/<date>-<change>/`.
- Keep tasks/specs in sync with implementation; sync delta specs to main specs before archive.

## Git Hygiene

- Do not revert unrelated local changes.
- Do not force push unless explicitly requested.
- Do not commit local/tooling artifacts:
  - `**/build/`
  - `.kotlin/`
  - `.opencode/`
- Amend only when explicitly requested and when the commit has not been pushed.

## Multiagent Defaults

- When the user explicitly allows or requests multiagent execution, you may use multiagent parallelization to advance implementation, provided write scopes stay disjoint and the main thread handles integration and final verification.
- For this repository, default all spawned multiagents to model `gpt-5.4` unless the user explicitly requests a different model.

## Commit Message Style

- Use concise conventional commit prefixes (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- Prefer Chinese subject lines for this repository.
- Keep the first line focused on the intent/why.
