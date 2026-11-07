# Plan 13 implementation — 2026-11-07

Implemented in the isolated worktree `/tmp/my-companion-plan13`, branch `feat/reminders-sounds-player-history`, from merged main `4b3dc5e`. Work was split across recurrence, sound and history agents, with shared database integration and combined validation.

## Behavior

- Reminders support custom minute/hour intervals (including 12 minutes), calendar-day/month intervals, a first date/time, three-occurrence previews and edit-in-place. Calendar anchors retain requested month-end days and local clock times. Missed repeats are skipped efficiently; Snooze makes a separate one-off without shifting the repeating cadence. A changed calendar interval re-anchors to the chosen first date; unrelated edits preserve its original anchor.
- Each reminder saves Default, Silent, Bell, Chime, Beep or an imported sound. Original CC0 presets and generation recipe are in `tools/reminder-sounds`. Imports accept device-decodable MP3, OGG Vorbis/Opus, WAV PCM, ADTS AAC and MP4 M4A, limited to 10 MB/30 seconds. Files are copied to MediaStore under Notifications/My Companion, deduplicated by content, and available independently of the source file. Missing copies fall back to default. Referenced or currently posted assets cannot be removed. Preview stops on lifecycle/focus changes.
- Per-sound channels retain Android user settings. Default uses the existing reminders channel. Repairing a missing imported copy uses a new URI-specific channel while carrying forward existing user sound/mute preferences. Imported files may remain in shared storage after uninstall. No arbitrary song-looping or cloud audio service was added.
- Player has Queue, Playlists and History. History records after five actual playing seconds (or completion of a shorter clip), saves progress, keeps the latest 500 distinct videos, and offers replay/resume, queue/playlist actions, removal and confirmed clearing. Recording is attached to the service player; queued or failed starts and seek distance alone do not qualify. Generation tokens prevent delayed progress from recreating a cleared/deleted current visit. Stream URLs are never saved.
- Room upgrades v2→v3 for sounds and v3→v4 for history without destroying contacts, reminders or playlists. Existing repeat encodings remain compatible. Reminder JSON exports include sound identity; audio bytes and playback history are not added to backup/export.

## Validation

- Actual API 37 x86_64 emulator: **5 tests passed**. Real SQLite migration/keystore tests, AlarmManager→receiver→notification delivery and 12-minute rearming with a saved silent sound, all five audio-container imports and missing-file handling, and actual local-WAV ExoPlayer→history persistence.
- **630 JVM/domain/Robolectric/Compose tests passed**, zero failures, errors or skips. `testDebugUnitTest lintDebug assembleDebug :app:assembleDebugAndroidTest assembleRelease` passed, including release vital lint. `ktlintCheck` also passed independently.
- Fixes found by validation: numeric generation defaults needed explicit Long values to allow initial history writes; removing an already-deleted MediaStore URI needed missing-copy handling; the longer reminder form required lazy-list scrolling in the navigation smoke test.

## Practical limits

Custom short intervals are accepted but Android idle/power rules can delay delivery. Channel sound, DND and notification volume remain under system/user control. Emulator checks establish delivery, decoding/import and persistence; they do not establish human audibility on the CMF phone or every headset/OEM power state. The phone was not connected for this run. Final phone listening, long idle timing, timezone changes while travelling and normal-day background playback remain manual acceptance checks.

The original scope and references are in [Plan 13](13-reminder-upgrades-and-player-history-plan.md).
