## MODIFIED Requirements

### Requirement: Playback service is the authoritative playback state owner
The system SHALL manage the active playback execution state inside the background playback service. The currently projected queue, current media item, playback speed, play/pause readiness, seek position, and the playback-mode projection exposed through the media session MUST stay consistent with the business-layer playback state that drives the service.

#### Scenario: Service reflects business-layer mode without forcing item change
- **WHEN** the business layer updates the current playback mode while a media item is already active
- **THEN** the playback service keeps the current media item stable and the next media-session snapshot reflects the updated playback-mode projection

#### Scenario: Service follows projected queue on completion
- **WHEN** the current item completes naturally while the service is playing a queue projected from business-layer mode state
- **THEN** the playback service advances or repeats according to the currently projected queue / repeat behavior and publishes the resulting execution state through the media session

### Requirement: App playback UI projects remote playback state
The app SHALL derive playback UI state from remote playback snapshots and SHALL limit local playback state to UI-only concerns such as sheet visibility, drag state, optimistic request state, and the shuffle-only “show original order” display preference.

#### Scenario: Mode indicator updates without play-state toggle
- **WHEN** the playback service accepts a playback mode change while playback state remains unchanged
- **THEN** the app updates the visible playback-mode indicator from the remote snapshot on the next sync cycle without waiting for a separate play/pause transition

#### Scenario: Original-order toggle remains app-local
- **WHEN** the user toggles “显示原始顺序” while shuffle mode is active
- **THEN** the app updates playlist presentation locally without treating the toggle itself as a remote playback-state mutation

### Requirement: Shared playback contracts are isolated from service implementation
The system SHALL define playback commands, metadata extras, and shared playback DTOs in modules that can be consumed by the app without depending on playback-service implementation packages, including the snapshot fields needed to express the current playback-mode projection.

#### Scenario: App consumes shared playback-mode projection
- **WHEN** the app builds against playback control APIs that read or display the current playback mode
- **THEN** it imports the shared playback contract and playback client modules without referencing playback-service implementation packages other than the exported service entry point required for controller connection

### Requirement: MediaSession adapter remains a mapping layer
The media-session adapter SHALL map service runtime state to Media3 state and forward incoming playback commands to the service runtime without owning raw playlist-order or shuffle-generation business rules beyond command translation.

#### Scenario: Session queue-navigation command forwards to service runtime
- **WHEN** Media3 issues a queue-navigation command
- **THEN** the media-session adapter forwards the command to the playback service runtime and the resulting session state is produced from the updated projected queue state
