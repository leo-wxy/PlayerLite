## ADDED Requirements

### Requirement: Playback service is the authoritative playback state owner
The system SHALL manage the active playback session state inside the background playback service. The active queue, current media item, playback speed, play/pause readiness, seek position, and natural-completion transition MUST be derived from service runtime state and exposed through the media session.

#### Scenario: Service updates speed and current item
- **WHEN** the app requests a playback speed change or a track skip
- **THEN** the playback service updates its runtime state and the next media-session snapshot reflects the new playback speed and active media item without requiring an app-local playback state mutation

### Requirement: App playback UI projects remote playback state
The app SHALL derive playback UI state from remote playback snapshots and SHALL limit local playback state to UI-only concerns such as sheet visibility, drag state, and optimistic request state.

#### Scenario: Speed indicator updates without play-state toggle
- **WHEN** the playback service accepts a playback speed change while playback state remains unchanged
- **THEN** the app updates the visible speed indicator from the remote snapshot on the next sync cycle without waiting for a separate play/pause transition

### Requirement: Shared playback contracts are isolated from service implementation
The system SHALL define playback commands, metadata extras, and shared playback DTOs in modules that can be consumed by the app without depending on playback-service implementation packages.

#### Scenario: App consumes shared playback contract
- **WHEN** the app builds against playback control APIs
- **THEN** it imports shared playback contract and playback client modules without referencing playback-service implementation packages other than the exported service entry point required for controller connection

### Requirement: MediaSession adapter remains a mapping layer
The media-session adapter SHALL map service runtime state to Media3 state and forward incoming commands to the service runtime without owning playback business rules beyond command translation.

#### Scenario: Session command forwards to service runtime
- **WHEN** Media3 issues a seek, play, pause, or queue-navigation command
- **THEN** the media-session adapter forwards the command to the playback service runtime and the resulting session state is produced from updated runtime state
