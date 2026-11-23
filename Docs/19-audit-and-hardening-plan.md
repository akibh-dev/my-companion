# Plan 19: Code audit and hardening

**Status:** audit done 2026-11-19. Section 1 (correctness) fixed 2026-11-23 on `fix/plan19-correctness`;
sections 2–5 not started.

A full read-through of every module (`app`, `core`, `ai-api`, `ai-models`, `adapters-messaging`,
`feature-context`, `feature-autoreply`, `feature-voice`) for correctness, crash resilience,
security, performance and release hygiene. Findings below were checked against the source; items
marked *suspected* were not confirmed. Nothing was measured on the phone, so the performance
costs are estimates from reading the code.

## Automated checks (baseline)

| Check | Result |
|---|---|
| `testDebugUnitTest` | 1,486 tests, 0 failures, 0 skipped |
| `ktlintCheck` | passes |
| `lintDebug` | 0 errors, ~40 warnings (mostly `UseKtx`, `SetTextI18n`; notable: `ExportedService`, `UnsafeProtectedBroadcastReceiver`, `MissingPermission`, `AppLinkWarning`) |

Builds need JDK 21: Gradle 8.14 fails on JDK 25 with a bare `25.0.4.1` error. Set
`org.gradle.java.home` in `~/.gradle/gradle.properties` to the JDK 21 Gradle already downloaded.

## 1. Bugs that make the app behave wrongly (fix first) — done

Every fix below has a regression test. Two database versions came with it (v7 `local_time`,
v8 `missed`, both migrated in place).

| # | Issue | Where | Fix |
|---|---|---|---|
| 1 | Reminder notifications use the reminder id with no tag; the host service uses id 41 and rule prompts 3101/3102. Reminder #41 replaces the foreground notification and "Done" can't clear it. | `data/notification/AndroidReminderNotifier.kt:61`, `core/.../host/ServiceHost.kt:145` | Post with a tag: `notify("reminder", id, …)` and cancel with the same tag. **Done:** posted and cancelled under the tag `reminder` (also from the Call button). |
| 2 | Daily/weekly/monthly reminders take the clock time from the last fire time read in the *current* zone, so they drift after a timezone change or DST gap (09:00 set in Dhaka fires at 06:00 for good in UTC+3). | `domain/time/ReminderTimeCalculator.kt:63,68,77`; `ReminderUseCases.kt:214` | Store the wanted local time and rebuild from it (as the calendar repeats do); recompute all repeats on `TIMEZONE_CHANGED`. **Done:** `Reminder.localTime` (Room v7, backfilled from the next trigger); daily/weekly/monthly rebuild from it, step by local date, and are recomputed on `TIMEZONE_CHANGED`. |
| 3 | Context rules can lose the settings they took over: `collectLatest` cancels `apply()` after `device.write` but before `storage.save`, so ringer/DND are never restored. | `feature-context/.../RulesEngine.kt:132`, `:80-97` | Run `apply` under `withContext(NonCancellable)` or use `conflate().collect`. **Done:** `apply` runs under `NonCancellable`. |
| 4 | A reminder is marked delivered even when `notifier.show()` returned false (notifications denied), so it is lost silently. | `domain/usecase/ReminderUseCases.kt:132-138` | Keep it undelivered or flag it missed, log it, show a "missed reminders" badge on Home. **Done:** flagged `missed` (Room v8); a one-off stays undelivered so the next restore retries it, a repeat moves on. Logged, "Missed" pill in the list, count on Home. |
| 5 | Settings restore does 12+ separate DataStore edits in `viewModelScope`; leaving the screen stops it halfway, and `runCatching` swallows the cancellation. | `export/SettingsBackup.kt:32-38`, `export/ExportViewModel.kt:86` | Run in the app scope or `NonCancellable`, one atomic edit per store, rethrow `CancellationException`. **Done:** `NonCancellable`, `SettingsStore.replace` (one edit), `CancellationException` rethrown. |
| 6 | Expired-stream retry happens once per item ever; a long video paused twice past the URL lifetime just stops. | `player/PlaybackService.kt:138-150` | Also reset `retriedMediaId` on `STATE_READY` / `onIsPlayingChanged(true)`. **Done:** re-armed on `onIsPlayingChanged(true)`. |
| 7 | Playlist "Shuffle" always starts on track 1. | `presentation/player/PlayerScreen.kt:773`, `player/MediaControllerPlayback.kt:55` | Pass a random start index when shuffling. **Done:** random start index in `PlayerViewModel`. |
| 8 | `+880 01711…` normalizes to `+88001711…` (WhatsApp/phonebook matching fails); `880123` becomes a "valid" `+880880123`. | `domain/model/PhoneNumbers.kt:31-36`, `domain/whatsapp/WhatsAppNumberMatcher.kt:62` | Strip a trunk `0` after `880`; reject short input starting with the country code. **Done** in both normalizers. |

Smaller ones:

- ~~Snoozing a paused reminder copies `paused = true`, so the snooze never fires (`ReminderUseCases.kt:153`).~~
  **Done:** the snoozed copy is a fresh, unpaused one-off.
- ~~Pause tile and widget read then write the flag separately; a double tap can leave it wrong
  (`system/PauseTileService.kt:38`, `widget/QuickActionsWidget.kt:45`).~~ **Done:** `SettingsStore.togglePaused` /
  `toggleEnabled` flip inside one `store.edit {}` (the voice tile too).
- A 00:00–00:00 "all day" rule window never matches (`feature-context/.../Rules.kt:131`).
  **Skipped, not a bug:** a window with the same start and end is empty by design (`RulesEdgeCasesTest`), and the
  rule editor won't save one. An "all day" option would be a new feature.
- ~~`Reconciler.kt:48-50`: a key whose `read` returns null goes into `applied` but not `before`, so it's never restored.~~
  **Done:** a setting whose old value can't be read is left alone until a run can read it; stray `applied` entries are dropped.
- ~~`ReminderTimeCalculator.kt:49` returns null on any exception, which then deletes a repeating reminder as "delivered".~~
  **Done:** only range errors mean "no next time", and a repeat without one stays in the list.
- ~~`MessageListener.kt:146` evicts reply actions from a `ConcurrentHashMap` in hash order, not oldest first.~~
  **Done:** an insertion-ordered map; a re-posted chat counts as newest.
- ~~Backup `version` is never checked (`export/ExportJson.kt:169`).~~ **Done:** a backup from a newer version is refused.
- ~~`ReminderTimeFormatter.kt:35` has no year, so next year's reminder reads like this year's.~~ **Done:** another year shows it.

Suspected (now confirmed by reading the code and fixed):

- ~~Auto-reply could answer its own message if a chat app re-posts it with a named "You" person.~~ **Done:** a
  MessagingStyle message whose person is the style's user has no sender, so it reads as your own.
- ~~Missed-call SMS treats a declined call as missed and may text an earlier caller.~~ **Done:** only the newest
  call log entry counts, and only when it is a missed call (a declined call logged as rejected is not texted).
- ~~`ServiceHost` race: unpausing while the service is stopping skips the restart.~~ **Done:** the service marks
  itself not running before `stopSelf(lastStartId)`, and re-enters the foreground on each start.
- ~~`RemindersViewModel.onSubmit` has no in-flight guard (double tap → two reminders).~~ **Done:** a second tap
  while saving is ignored. No `SavedStateHandle` yet (the form is lost on process death).

## 2. Crashes and failures that leave no trace

- No uncaught-exception handler and no `CoroutineExceptionHandler`; `AppLog` never sees a crash.
  **Fix:** in `CompanionApp.onCreate`, chain a default handler that writes `log.e("Crash", …)` + `flush()`, then calls the previous one.
- The app scope `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`di/AppModule.kt:89`) has no handler;
  one bad reminder in `RestoreRemindersUseCase` stops every later one being rescheduled on boot.
  **Fix:** add a logging handler; try/catch per reminder.
- `NewPipeYouTube.info()` catches only `ExtractionException`/`IOException`; a `RuntimeException`
  after a YouTube change kills the process. **Fix:** also catch `RuntimeException` → `YouTubeException.from`; log extractor failures.
- `ServiceHost.sync()` discards `startForegroundService` failures (`ForegroundServiceStartNotAllowedException` on background starts).
  **Fix:** log it, retry from `MainActivity.onResume`.

**Done (branch `fix/crash-visibility`):**

- `CrashLogger` (core) is installed first thing in `CompanionApp.onCreate`: writes `E Crash: crashed on <thread> at <top frames>`,
  waits up to 1 s for the log to reach the disk, then hands the crash to the previous handler (the process still dies as before).
- The app scope has a `CoroutineExceptionHandler` that logs `E AppScope: background job failed`. A failing job no longer
  ends the process; it is logged and the other jobs carry on.
- `RestoreRemindersUseCase` tries the clean-up and each reminder on its own and returns the failures;
  `DefaultReminderEvents` logs each as `E Reminders: couldn't restore reminder N`. Cancellation is rethrown.
- `NewPipeYouTube.info()` maps `RuntimeException` to `YouTubeException` too (cancellation passes through) and logs every
  extractor failure as `W YouTube: couldn't read <videoId>`. The extractor call is injectable for tests.
- `ServiceHost.sync()` logs a refused start (`W ServiceHost`), and `MainActivity.onResume` calls it again.
- Tests: `CrashLoggerTest`, `AppScopeTest`, `ReminderUseCasesTest` (one bad reminder, cancellation), `NewPipeYouTubeTest`,
  `ServiceHostTest` (refused start), `MainActivityTest` (retry on resume).

## 3. Security

No high-severity issue. `CallActivity` is safe: `CallToken` is a 32-byte `SecureRandom` secret
compared in constant time, checked before any side effect, and numbers come from Room, not the intent.

| Sev | Issue | Fix |
|---|---|---|
| Med | `PlaybackService.onGetSession` returns the session to any app (no `onConnect`). Any app can read now-playing, change the queue and write titles/artwork URIs into history. | Override `onConnect`: full access for own package and `controller.isTrusted`; others get play/pause/seek only. Validate `mediaId` (`^[A-Za-z0-9_-]{11}$`) and only allow `https://i.ytimg.com/` artwork. |
| Med/Low | Release is signed with the debug keystore (`app/build.gradle.kts:35`); anyone with it can ship an update that inherits all permissions. | Dedicated release keystore outside the repo. Needs one reinstall. |
| Low | `MainActivity` opens any registered route from an external intent's `route` extra. | Allowlist routes or accept them only from own PendingIntents. |
| Low | Settings import doesn't clamp values (negative `cooldownMinutes` disables throttling) and has no size cap. | Clamp numbers, cap at ~1 MB, list risky toggles in the confirm dialog. |
| Low | Backup includes `companion.db` but not `-wal`/`-shm`, so a restore can lose recent writes. | Include them, or `allowBackup=false` if cloud backup isn't wanted. |
| Low | `setAllowCrossProtocolRedirects(true)` (`PlaybackService.kt:53`). | Set `false`; googlevideo is HTTPS only. |

Checked and fine: share/view links (strict 11-char id), boot receiver, all PendingIntents immutable
and explicit (except the LocationManager one, which must be mutable), notification listener,
template-only auto-reply, no PII in logs, SAF-only exports, parameterized queries, no committed secrets.

**Done (branch `fix/player-access-control`):**

- Player session: `PlayerSessionCallback.onConnect` gives full access to this app's own controllers (screens,
  media notification) and to `controller.isTrusted` ones (System UI, Bluetooth, Auto, apps with notification access).
  Any other app gets play/pause, seek, and previous/next only: no queue, metadata or timeline, no session commands
  (there are no custom ones today).
- Items from any controller: `mediaId` must match `^[A-Za-z0-9_-]{11}$` (others are dropped, the start index follows
  the same item), and artwork is kept only for `https://i.ytimg.com/` URIs, so nothing else reaches history.
- `setAllowCrossProtocolRedirects(false)`.
- `MainActivity` opens a `route` extra only for the routes the app sends itself (Quran continue, library, export, permissions).
- Settings import clamps `cooldownMinutes` (5–240), `maxChars` (60–600) and `speechRate` (0.5–2, NaN → default)
  to the screens' ranges, and refuses files over 4 MB (a 1 MB cap could refuse a real backup with a big library and
  years of reading stats). The risky-toggle list in the confirm dialog is not done.
- Backups include `companion.db-wal`; `-shm` is left out on purpose (SQLite rebuilds it from the log).
- Release keystore: not part of this branch.
- Tests: `PlayerAccessTest`, `ExportJsonTest` (clamping, size cap), `FirstRunGuideTest` (route allowlist), `BackupRulesTest`.

## 4. Performance

| Impact | Issue | Fix |
|---|---|---|
| High | "Play now" runs `StreamInfo.getInfo` twice (`fetch()` and `audioStream()` in `NewPipeYouTube.kt`), ~1–3 s extra. | Cache `StreamInfo` per videoId for a few minutes. |
| High | No ExoPlayer disk cache; replays and seeking back download again. Separate HTTP stack from the app's OkHttp. | `SimpleCache` (~150 MB LRU) + `CacheDataSource`, cache key = mediaId; `OkHttpDataSource.Factory`. |
| High | ONNX models loaded via `readBytes()` (~47 MB heap) from compressed assets; two 30 s idle timers; `ModelLoader.keep()` only called in tests, so voice mode cold-loads often. | `noCompress += "onnx"`, copy to `filesDir`, `createSession(path)`; one idle timer; `keep(TRANSLITERATOR, true)` while voice mode is on; consider int8. |
| High | Transliteration beam search runs one decoder pass per beam over the full prefix (`IndicXlitPrototype.kt:139-151`). | Batch beams per step; cache results per word. |
| Med | Player screen recomposes twice a second: `positionMs` lives in the whole `PlaybackState`; queue rebuilt on every event. | Separate position `StateFlow`, read only in the seek bar; rebuild queue on timeline change. |
| Med | `MediaControllerPlayback` never releases its controller, so the service and ExoPlayer stay alive; stale controller after disconnect. | Release when idle and unobserved; null out on disconnect. |
| Med | History recorder ticks every second even when paused and prunes on every write. | Tick only while playing; prune on new videoId. |
| Med | Heartbeat worker every 15 min = 96 process wakeups/day. | 1 h+, or debug only. |
| Med | Startup builds the whole `ServiceHost` graph on main for every process start. | `dagger.Lazy<ServiceHost>`, touch it in the IO scope. |
| Med | Permission checks (IPC) on main in `HomeViewModel.kt:45-55`. | `.flowOn(Dispatchers.Default)`. |
| Low | Glass nav bar records and blurs the whole screen every frame; LazyColumns without keys (Log, Places, ReplyLog, Home); queue keys include index; `DotPattern` draws 1,000+ circles per frame; `runBlocking` in `ReminderSoundChannels.kt:31`; screen-off polling in `ForegroundApps.kt`. | See the respective files. |

Already good: all DAOs suspend/Flow, sensible indexes, history capped at 500, lazy DB/NewPipe/TTS
init, single OkHttp, no `GlobalScope`, `WhileSubscribed(5000)` + `collectAsStateWithLifecycle` everywhere.

## 5. Production hygiene

- **R8 off** and `proguard-rules.pro` has stale Moshi/Retrofit rules. Enable minify + resource shrinking, write real keep rules (NewPipe, Rhino, ONNX Runtime), test the release build on the phone.
- **Room `exportSchema = false`** in all three databases. Turn it on with the Room Gradle plugin's `schemaDirectory` and commit the JSON.
- **CI** runs only debug tasks. Add `assembleRelease lintRelease`.
- **Logging:** `AppLog` mirrors to logcat in release; gate on `BuildConfig.DEBUG`. Add StrictMode and LeakCanary to the debug build.
- **kapt → KSP** for Hilt and Room (faster builds).
- **Tests to add:** `NewPipeYouTube` throwing `RuntimeException`, `FireReminderUseCase` with `shown=false`, `SettingsBackup` round-trip, `BootReceiver` partial failure, `RetryExpiredStream`, timezone change for repeating reminders.
- **Runbook:** a "bump NewPipeExtractor" checklist in `Docs/releases`, since YouTube breakage needs a manual rebuild.
- **Cleanup:** delete the stale `out/device-workspace/` copy (old `com.akib` package) and the empty `feature-capture`/`feature-vault` build dirs.

## Fix order

Each step on its own branch and PR, each bug with a regression test.

1. ~~**Correctness** — section 1 items 1–5, then 6–8 and the smaller ones.~~ Done (`fix/plan19-correctness`).
2. **Crash visibility** — section 2.
3. **Player access control** — `onConnect` + input validation.
4. **Performance wins** — StreamInfo cache, ExoPlayer disk cache, then voice model loading.
5. **Release hardening** — real signing key, R8, schema export, release in CI. Needs one reinstall on the phone (export data first).
6. **Baseline profile** — [Plan 18](18-baseline-profile-plan.md), last as planned.

### Section 5 progress (2026-11-23, branch `build/release-hardening`)

- Done: R8 minify + resource shrinking with lean keep rules (ONNX Runtime, NewPipe/Rhino, Pdfium; the
  old Moshi/Retrofit/keep-everything rules are gone); release keystore outside the repo, read from
  `~/.gradle/gradle.properties` (debug key fallback for CI, or `-PMYCOMPANION_RELEASE_STORE_FILE=` to
  try a release build over a debug install); Room schema export on for all databases, schemas
  committed; CI builds `lintRelease assembleRelease`; `AppLog` mirrors to logcat only in debuggable
  builds.
- Not done: StrictMode/LeakCanary in debug, kapt → KSP, the extra tests and the runbook, the cleanup.
