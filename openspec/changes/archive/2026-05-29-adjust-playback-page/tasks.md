## 1. Reference And Scope

- [x] 1.1 Confirm `/Users/wxy/Projects/player-lite/aa.html` renders as the agreed visual reference for the playback page.
- [x] 1.2 Locate the current playback page Compose implementation and identify the smallest UI files needed for the change.
- [x] 1.3 Map each visible element in the HTML reference to the existing playback page state, callback, or static visual treatment.

## 2. Playback Page UI

- [x] 2.1 Update the playback page background to use the existing cover-derived dark base, blurred cover layer, and readable dark overlay.
- [x] 2.2 Adjust the top bar to keep back and more actions, remove visible `歌曲 / 歌词` tabs, and use the former tab position for centered song title / artist.
- [x] 2.3 Rework the song page layout order to match the reference: song title, artist, cover, lyric summary, progress, quality/effect status, three-button core controls, secondary actions.
- [x] 2.4 Tune cover size, rounded corners, shadow, and buffering badge placement so the cover remains the primary visual anchor.
- [x] 2.5 Restyle the progress area with a thin track, small thumb, stable time labels, and the existing quality/effect status line.
- [x] 2.6 Restyle the core controls so play/pause remains dominant and previous/next remain the only side transport controls.
- [x] 2.7 Place lower-priority actions such as mode, detail, audio effect, playlist, and more into a visually secondary action row without changing their behavior.
- [x] 2.8 Add small-screen layout adjustments that compress cover size and vertical spacing before reducing control usability.

## 3. Behavior Preservation

- [x] 3.1 Verify existing callbacks still handle play, pause, previous, next, playback mode, playlist sheet, more actions, seek, artist click, detail, favorite, and share.
- [x] 3.2 Verify the lyrics page remains a full-page sibling of the song page and is not wrapped in card-style containers, while the visible top tab is removed.
- [x] 3.3 Verify local audio, online audio with songId, and missing-cover states remain readable and do not expose broken placeholders.

## 4. Validation

- [x] 4.1 Run focused UI/unit validation for playback page changes where available.
- [x] 4.2 Run `./gradlew :app:testDebugUnitTest`.
- [x] 4.3 Run `./gradlew :app:assembleDebug`.
- [ ] 4.4 Visually inspect the playback page on at least one standard portrait viewport and one smaller-height viewport.
