# AGENT.md

Guidelines for coding agents working in this repository.

## Project Overview

- Android audio player built with FFmpeg and JNI.
- Main modules:
    - `app/` - UI, ViewModel, playlist/domain behavior.
    - `player/` - Kotlin API plus native C++ playback/decoder.
- Player feature code is organized under:
    - `app/src/main/java/com/wxy/playerlite/feature/player/`
    - `app/src/main/java/com/wxy/playerlite/feature/player/ui/`
    - `app/src/main/java/com/wxy/playerlite/feature/player/ui/components/`
    - `app/src/main/java/com/wxy/playerlite/playlist/`

## Required Validation

Run these after meaningful code changes:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- If native C++ is touched, `:app:assembleDebug` is mandatory.

## Coding Rules

- Keep `MainActivity` thin (wiring and lifecycle bridge only).
- Put stateful behavior in `PlayerViewModel` (or dedicated coordinators if split further).
- Keep Compose UI mostly stateless: consume state and emit callbacks.
- Reuse `PlaylistController` for add/remove/move/active-index behavior.
- Preserve playback-state compatibility constants in `feature/player/PlaybackState.kt`.

## UI and UX Constraints

- Playlist entry button stays at bottom-right.
- Playlist uses bottom sheet style with overlay scrim.
- Main playback controls stay focused on three primary actions: previous, play/pause, next.
- Drag-sort should feel responsive and avoid accidental instant reordering.

## OpenSpec Workflow

- Main specs live in `openspec/specs/`.
- Active changes live in `openspec/changes/<change>/`.
- Archived changes live in `openspec/changes/archive/`.
- Keep specs and tasks in sync when behavior changes.

## Git Hygiene

- Do not revert unrelated local changes.
- Do not amend commits unless explicitly requested.
- Do not force push unless explicitly requested.
- Do not commit local/tooling folders:
    - `.kotlin/`
    - `.opencode/`

## Commit Message Style

Use concise conventional commit prefixes:

- `feat:` new behavior or capability
- `fix:` bug fix or regression fix
- `docs:` documentation-only change
- `refactor:` structural change without behavior change
- `test:` test additions or updates

## Workflow Orchestration

### Plan Mode Default

- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### Subagent Strategy

- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

### Demand Elegance (Balanced)

— For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### Autonomous Bug Fixing

- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

### Task Management

- Plan First: Write plan to tasks/todo.md with checkable items
- Verify Plan: Check in before starting implementation
- Track Progress: Mark items complete as you go
- Explain Changes: High-level summary at each step
- Document Results: Add review section to tasks/todo.md
- Capture Lessons: Update tasks/lessons.md after corrections
- Session Progress Log
- Always take stock of what was done and what remains, and save it in AGENT_PROGRESS.md At the start
  of a new session, always read AGENT_PROGRESS.md before making changes

### Self-Improvement Loop

- After ANY correction from the user: update tasks/lessons.md with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project
