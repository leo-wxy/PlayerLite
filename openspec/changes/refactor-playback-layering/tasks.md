## 1. Module Split

- [x] 1.1 Create `:playback-contract` and move shared playback DTOs, session commands, and metadata extras into it
- [x] 1.2 Create `:playback-client` and move `PlayerServiceBridge` plus remote snapshot types into it
- [x] 1.3 Update Gradle settings and module dependencies so `:app` depends on `:playback-client` and `:playback-contract`, while `:playback-service` depends on `:playback-contract`

## 2. Service Authority

- [x] 2.1 Consolidate queue, active item, seek, playback speed, and completion decisions inside `PlaybackProcessRuntime`
- [x] 2.2 Keep `PlayerSessionPlayer` limited to state mapping and command forwarding against service runtime
- [x] 2.3 Preserve current playback behaviors, including playlist navigation, speed continuity, and auto-next/stop-at-tail semantics, with service runtime as the single authority

## 3. App Runtime Slimming

- [x] 3.1 Reduce `PlayerRuntime` to UI projection responsibilities, keeping only local UI state, optimistic state, and remote snapshot mapping
- [x] 3.2 Route playback commands from `PlayerViewModel` exclusively through `PlayerServiceBridge`
- [x] 3.3 Retain playlist editing/persistence and UI progress smoothing without reintroducing app-side playback truth

## 4. Regression Verification

- [x] 4.1 Add or update tests covering shared contract/client mapping after module migration
- [x] 4.2 Add or update service tests covering authoritative queue, speed, seek, and completion behavior
- [x] 4.3 Add or update app tests covering remote snapshot projection, speed label refresh, and progress display consistency
- [x] 4.4 Run `./gradlew :playback-service:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
