## 1. Tests First

- [x] 1.1 Update settings screen tests to assert network cache restriction switches are absent.
- [x] 1.2 Update settings ViewModel/repository tests to remove network cache restriction persistence and only sync cache failure notifications.
- [x] 1.3 Update playback service/orchestrator tests so cache policy no longer carries mobile network no-cache state.
- [x] 1.4 Update playback preparation and prewarm tests to assert mobile/non-Wi-Fi network does not block disk cache or prewarm.

## 2. Settings Layer

- [x] 2.1 Remove `cacheOnlyOnWifi` and `mobileNetworkPlayWithoutDiskCache` from settings models and storage.
- [x] 2.2 Remove settings page callbacks and UI rows for both network cache restriction switches.
- [x] 2.3 Keep cache failure notification, playback cache limit and playback prewarm settings functional.

## 3. Playback Layer

- [x] 3.1 Simplify `setCachePolicyPreferences` contracts to only carry cache failure notification preference.
- [x] 3.2 Remove playback runtime reads/writes for old network cache restriction preferences.
- [x] 3.3 Make network playback always eligible for managed disk cache, including mobile network.
- [x] 3.4 Remove network policy from playback prewarm context and scheduling.

## 4. Specs And Verification

- [x] 4.1 Update main specs to remove network-type cache restrictions after implementation is coherent.
- [x] 4.2 Run targeted unit tests for settings, orchestrator, playback service and prewarm.
- [x] 4.3 Run OpenSpec validation, full unit tests and debug build.
- [x] 4.4 Clean residual removed-setting test identifiers from active test code.
