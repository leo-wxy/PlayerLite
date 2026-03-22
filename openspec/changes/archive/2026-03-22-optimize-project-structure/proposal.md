## Why

The repository has already started modularizing core capabilities, but most business implementation still accumulates inside `app`, which keeps `app` as the default destination for new feature code and weakens module boundaries. At the same time, playback implementation details, duplicated detail-page data patterns, and repeated build/runtime configuration are increasing the cost of change and making future feature work harder to isolate, verify, and evolve safely.

## What Changes

- Re-scope `app` to act as the application shell and composition root instead of continuing to host full feature implementations.
- Tighten playback integration boundaries so UI and shell code depend on stable playback client contracts rather than playback service implementation classes.
- Standardize the structure used by detail-oriented features so playlist, album, and artist detail flows stop diverging in repository, remote-data, and mapper organization.
- Centralize shared build configuration and runtime environment configuration so module scripts and environment-dependent values stop drifting independently.

## Capabilities

### New Capabilities
- `app-shell-composition-root`: Define `app` as the shell, routing, and dependency composition layer rather than the default home for feature implementation code.
- `playback-service-boundary`: Expose playback startup and control through stable client-facing boundaries without leaking playback service implementation details into `app`.
- `detail-feature-architecture`: Establish a consistent structure for playlist, album, and artist detail features so their data and UI layers can evolve without copy-paste divergence.
- `build-config-governance`: Provide centralized project build conventions and environment configuration so shared Android and runtime settings are managed from stable sources.

### Modified Capabilities

None.

## Impact

- Affected code: `app`, `playback-client`, `playback-contract`, `playback-service`, `feature-search`, `design-system`, and the detail feature code currently under `app/src/main/java/com/wxy/playerlite/feature/*`.
- Affected systems: dependency composition, playback process integration, detail-page feature organization, Gradle module configuration, and runtime environment configuration.
- Expected outcome: smaller `app` responsibilities, cleaner dependency direction, more repeatable feature structure, and lower maintenance cost for future changes.
