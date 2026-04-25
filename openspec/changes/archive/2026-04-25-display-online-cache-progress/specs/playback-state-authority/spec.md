## MODIFIED Requirements

### Requirement: App playback UI projects remote playback state
The app SHALL derive playback UI state from remote playback snapshots and SHALL limit local playback state to UI-only concerns such as sheet visibility, drag state, optimistic request state, and the shuffle-only “show original order” display preference. The remote snapshot MUST include the current queue item's shared display metadata, including current song identity, duration hint, and cover artwork, so the player page can render current content without refetching detail data. The remote snapshot MUST also include the authoritative cache-progress projection for the current playable item whenever the current source supports cache progress.

#### Scenario: Current-item cache progress comes from the remote snapshot
- **WHEN** the playback service can resolve cache progress for the current playable item
- **THEN** the next remote playback snapshot includes the current item's cache-progress fields
- **AND** the app projects those fields directly into playback UI state
- **AND** the app does not probe cache files or reconstruct cache progress from local storage on its own

#### Scenario: Cache progress resets when the current playable item changes
- **WHEN** the playback service switches to another current playable item
- **THEN** the authoritative remote snapshot clears or replaces the previous item's cache-progress projection
- **AND** the app does not keep rendering the previous item's cache-progress segment on the new song

#### Scenario: Fully cached current item projects as a full-width cache segment
- **WHEN** the playback service marks the current playable item as fully cached
- **THEN** the authoritative remote snapshot projects that current item as fully cached
- **AND** the app renders the cache segment as full width on the shared progress bar

#### Scenario: Estimated cache ratio may be projected before real total bytes are known
- **WHEN** the current playable item already has cache progress
- **AND** the playback service does not yet have stable total-byte metadata
- **THEN** the authoritative remote snapshot may expose an estimated cache ratio for UI display
- **AND** the app treats that ratio as a display projection rather than as a locally recomputed exact byte contract
