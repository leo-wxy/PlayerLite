## 1. OpenSpec Artifacts

- [x] 1.1 Create proposal.md, design.md and delta specs for cache, prewarm, playback service and observability.
- [x] 1.2 Validate the change artifacts before implementation.

## 2. Complete Cache Playback Path

- [x] 2.1 Add a playback-service test proving complete cache hit prepares from local cache when URL resolution fails.
- [x] 2.2 Adjust playback preparation logic if the test exposes a dependency on network resolution for complete cache hits.
- [x] 2.3 Add or update diagnostics for complete cache local-hit decisions.

## 3. Prewarm Cancellation Isolation

- [x] 3.1 Add tests for stale prewarm Ready, Completed, Failed and Canceled callbacks after a context switch.
- [x] 3.2 Harden prewarm state publishing so only the active context signature and active job can publish snapshots.
- [x] 3.3 Add coverage for audio quality or source changes not being overwritten by old prewarm results.

## 4. Cache And Prewarm Observability

- [x] 4.1 Review current cache progress, complete-cache and prewarm logs and identify noisy or missing fields.
- [x] 4.2 Converge logs to stable state-boundary messages with key, track, quality, progress, byte counts, signature and reason fields.
- [x] 4.3 Ensure normal cache read loops do not emit high-frequency default logs.
- [x] 4.4 Add regression coverage that prewarm state-boundary logs include the resolved resource key and stale tasks cannot pollute the active target key.
- [x] 4.5 Allow incomplete current online tracks to schedule a bounded current-ahead prewarm instead of publishing an empty next-track candidate.

## 5. Verification

- [x] 5.1 Run targeted playback-service unit tests for complete cache, cache progress and prewarm isolation.
- [x] 5.2 Run app projection tests affected by cache progress state.
- [x] 5.3 Run app assembleDebug.
- [x] 5.4 Run git diff --check.
