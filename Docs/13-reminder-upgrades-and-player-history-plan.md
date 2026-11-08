# Custom reminder schedules, sounds, and player history

Planned: 2026-11-07. Status: proposal based on the current code; implementation has not started.

## Scope and current findings

1. Add freely entered repeat intervals in minutes, hours, calendar days and calendar months, plus a chosen first date/time.
2. Give each reminder a sound selection: default, silent, bundled preset or imported local audio.
3. Add a local History section beside Queue and Playlists in the YouTube player.

The app currently targets/minimally supports Android API 36. Relevant existing behavior:

- `domain/model/Reminder.kt`: `Repeat.Every(minutes)` exists, but accepts only 15 minutes through seven days. Daily, selected weekdays and monthly-on-a-day are also supported.
- `presentation/reminders/RemindersScreen.kt` and `RemindersViewModel.kt`: intervals are preset chips under “Every few hours”; monthly day comes from the first trigger date. There is no arbitrary interval input.
- `domain/time/ReminderTimeCalculator.kt`: calendar schedules keep local clock time; elapsed-minute intervals skip missed occurrences. Extend this distinction.
- `data/alarm/AndroidAlarmScheduler.kt`: schedules individual alarms, with exact-alarm permission checks and an inexact fallback. Keep this scheduler rather than introducing a periodic worker.
- `data/notification/AndroidReminderNotifier.kt`: all reminders use the same `reminders` notification channel and have no saved sound choice.
- `presentation/player/PlayerViewModel.kt`: `PlayerSection` has only Queue and Playlists. Playback lives in `player/PlaybackService.kt`; there is no persistent playback history.
- Room database version is currently 2. Preserve contacts, reminders and playlists through additive migrations.

## 1. Custom repeat schedules

### User flow

Keep existing quick choices and add **Custom repeat**:

- **Every** `[positive whole number]` `[minutes / hours / days / months]`.
- **First notification**: date and time, with a clear “Now” option.
- Show a readable summary and the next three scheduled occurrences before Save.
- Examples: every **12 minutes**, every **3 hours**, every **2 days at 09:30**, every **3 months on the 15th at 18:00**.
- Selected weekdays remain a separate existing choice. Disable Save for empty, zero, negative, nonnumeric or out-of-range values; use checked arithmetic and documented bounds.

Interpretation: days and months mean calendar recurrence at the selected local clock time. Minutes and hours mean elapsed intervals. “Every 24 hours” and “every day at 09:30” therefore have intentionally different daylight-saving behavior.

### Scheduling rules

- Allow 12 minutes by lowering the existing elapsed-interval validation minimum. Proposed form minimum is one minute, with sub-15-minute delivery explicitly described as best effort while the phone sleeps.
- Use `Repeat.Every` for minutes/hours; add calendar-day and calendar-month interval variants with explicit anchors. Existing encoded repeat values must decode exactly as before.
- Keep the requested day-of-month independent of the last delivered date. For a monthly schedule anchored on the 31st, clamp a short month to its last day and return to the 31st when available. Advance months from the original anchor to avoid drift.
- Skip missed occurrences and schedule the next future one, rather than sending a burst after reboot. Compute large gaps efficiently rather than looping once per missed minute/day.
- Keep the regular schedule anchored when Snooze is used; snoozing an occurrence must not permanently shift the repeat cadence. Define this independently of “Done,” which dismisses the current notification while repeating schedules continue.
- Recalculate correctly after timezone/time changes and reboot; cancel old pending alarms when editing/deleting a reminder. Cover permission revocation and the existing approximate scheduling fallback.

Android constrains idle-mode alarm delivery; entering a 12-minute interval cannot guarantee exact delivery every 12 minutes in all power states. Periodic WorkManager has a 15-minute minimum, so it cannot implement this requested interval. Use individual alarms and show the existing exact-alarm access state. [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

Primary changes: repeat model/encoding, time calculator, reminder form and formatter, scheduling use cases, alarm recovery, persistence mappings and reminder export. Add repeat fields/anchors through a migration only where needed; do not encode essential scheduling state solely in UI state.

## 2. Per-reminder notification sounds

### User flow

Add a **Sound** row to the reminder editor:

- System default notification sound (existing reminders keep this behavior).
- Silent.
- A few short bundled presets, such as Bell, Chime and Beep, with clear redistribution rights.
- **Choose audio file** using the system document picker.
- Show the selected name, Preview/Stop controls, and a way to replace/reset it. Preview is explicit and stops when leaving the editor or losing audio focus.

Treat the requested “oog” format as **OGG**. Initial support: MP3, OGG Vorbis/Opus, WAV PCM and AAC/M4A where the device can decode the actual file. Check content/playability, not only the extension; reject corrupt, unsupported and oversized files with a clear message. Proposed import limits: 10 MB and 30 seconds, displayed before selection. Long-song playback, looping alarms and trimming are outside the first version. [Android supported formats](https://developer.android.com/media/platform/supported-formats).

### Storage and Android channels

- Model the selection in pure Kotlin, e.g. `ReminderSound.Default`, `Silent`, `Preset(id)` and `Imported(id)`; keep Android `Uri` handling in the data layer.
- Import using `ACTION_OPEN_DOCUMENT` with audio MIME types. Copy validated audio into an app-created `MediaStore.Audio` entry under `Notifications/My Companion`; the reminder should not depend on the original Downloads file continuing to exist. Use a stable published content URI accessible to Android's notification sound player. No broad storage scan permission is needed for this selected-file flow on the current target. Verify sound playback with the app process stopped. [MediaStore storage guidance](https://developer.android.com/training/data-storage/shared/media).
- Document that the copied sound appears in shared notification storage and may remain after uninstall. Remove incomplete imports on cancellation/failure. If the copied file later disappears or becomes unreadable, fall back to the default and show the missing-file state in the editor.
- Add sound records and a reminder sound reference with backward-compatible defaults. Deduplicate identical imports, avoid deleting assets referenced by other reminders, and make migration/backup behavior explicit. A URI alone is not a portable audio backup; unresolved imported sounds reset to default after restoration unless audio export is separately implemented.
- Use a stable channel per distinct sound configuration, reusing it across reminders. Preserve the old `reminders` channel for default sound and its existing user settings. Changing a reminder's sound selects another channel rather than trying to mutate the old channel.
- Keep sound/channel mappings stable across updates, respect user changes in Android's channel settings, and provide a shortcut to those settings. Do not recreate channels to bypass a muted setting. New imported sound content gets a new sound identity. Bound and clean up unused channels/assets carefully, considering posted and snoozed notifications.

Channel sound behavior is fixed after creation from the app's perspective, while the user retains control in system settings. This is why simply calling `setSound()` on each notification builder will not implement arbitrary per-reminder sounds correctly on this app's Android target. [Notification channel guidance](https://developer.android.com/develop/ui/compose/notifications/channels).

Use normal notification sounds, with notification audio attributes and normal system volume/DND behavior. Do not start a separate background media player when a reminder fires. Preview and delivered notifications must use the same chosen audio asset.

## 3. YouTube player History

### User experience

- Player sections become **Queue | Playlists | History**, keeping current transport controls available.
- Show most recently played first: title, channel, thumbnail, last-played time and saved progress. Group into Today, Yesterday and Earlier using the current timezone.
- Tap to replay from the beginning. An explicit **Resume** action uses saved progress where meaningful. Offer existing Add to queue and Add to playlist actions, Remove from history and Clear history (with confirmation).
- Store this app's listening history locally; do not imply access to the user's YouTube account history. Keep the newest 500 distinct videos initially and provide an empty state.

### Recording rules

- Add a history repository and Room table keyed by YouTube video ID. Store metadata, last-played timestamp, last position and duration. Replaying moves the existing row to the top rather than producing duplicate rows.
- Record after five accumulated seconds of actual playback during a visit; count a successfully completed shorter video too. Do not record merely resolving a link, adding to queue, buffering, seeking over the threshold, a failed start or a paused item.
- Attach recording to the ExoPlayer instance in `PlaybackService`, not a Compose screen or ViewModel, so playback through background, headset and lock-screen controls is captured. Snapshot player state on its application looper; dispatch persistence through coroutines.
- Combine `isPlaying` changes, media-item transitions and discontinuities. A transition alone is not proof that audio played. Use a monotonic clock for time actually playing and handle automatic queue advance and repeat-one transitions explicitly. [Media3 event guidance](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).
- Save progress periodically while playing (approximately every 15 seconds), and on pause, item switch, completion and orderly service shutdown. Flush the previous item's snapshot before switching; do not depend solely on `onDestroy`, since process death can skip it.
- Serialize writes and removal/clear operations. Removing or clearing history suppresses reinsertion from delayed writes/progress for that current visit; a later explicit playback can create history again.
- Never persist expiring stream URLs. Replay/resume uses the stored video ID through the existing resolver; unavailable/deleted videos produce the existing playback error flow without breaking the history list.

Primary changes: new domain history model/repository and data implementation, `PlaybackService`, `PlayerViewModel`, `PlayerScreen`/`PlayerRoute`, video action reuse, Hilt bindings and Room migration. History export, account sync and reconstructing a history from old playlists are outside the first version.

## Delivery and verification

Implement in separate feature branches from the latest `main`, with PRs targeting `main`; do not merge the unrelated voice PR implicitly. Recommended order:

1. Custom interval form, recurrence calculations and compatibility tests.
2. Sound import/presets, per-sound channels, migrations and editor controls.
3. Service-level history recording, Room storage and the History section.

For sequential delivery, sound storage can migrate Room v2→v3 and history v3→v4. Verify the actual schema at implementation time and test the complete upgrade path. Preserve existing reminders, playlists, contacts and channel preferences. No destructive migration fallback.

Acceptance checks:

- Recurrence: 12-minute, multi-hour, multi-day and multi-month examples; persistence round trips; month ends/leap years; daylight-saving/timezone changes; skipped downtime; overflow/invalid input; editing, Done and Snooze behavior.
- Sounds: each supported real codec, invalid/corrupt files, picker cancellation, missing source after import, missing copied sound, process death/reboot, repeat/snooze retaining sound, switching two reminders between distinct sounds, system channel overrides, silent and DND behavior.
- History: foreground/background/lock-screen playback, automatic advance, repeat-one, pause/resume without duplicate entries, failed starts, threshold versus seeking, progress persistence, pruning and clear/delete during playback.
- Room upgrades and repository integration tests; focused domain and Compose tests; existing notification/player regressions. Run `ktlintCheck testDebugUnitTest lintDebug assembleDebug` and the relevant device tests. Use the pinned-model fetch prerequisite for a clean build as documented in README.
- On the CMF Phone 1, audibly test imported MP3 and OGG notifications while the screen is locked and the app is stopped, measure actual repeat timing under normal/idle conditions, and confirm history after a background playback session and app restart.

Planning verification: inspected the existing implementation and official Android references. No runtime behavior changed and no build or device tests were run for this documentation-only task.
