# Device test report (in progress)

First run of the whole app on a real Android system instead of only JVM tests.
**Status: paused after testing — fixes and the documentation rewrite are the next steps (see the end).**

| | |
|---|---|
| Date | 2026-10-25 |
| Code | `main` at `4aa8313` (YouTube player merged), branch `test/device-testing` |
| Device | Emulator `Pixel_8`, Android 17 (API 37), x86_64, **16 KB memory pages**, Google Play image |
| Build | JDK 17 (Corretto 17.0.20.1), Gradle 8.14.5, AGP 8.4.0, Kotlin 1.9.22 |
| Tools | `adb`, `uiautomator`, `dumpsys`, `monkey`, and the helpers in [`tools/device-test/`](../tools/device-test/) |

---

## 1. Build and JVM tests

`./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug` (same as CI) → **BUILD SUCCESSFUL**.

| Check | Result |
|-------|--------|
| ktlint | clean |
| Unit + Robolectric + Compose UI tests | **202 passed**, 0 failed (docs still say 133 — fix) |
| Android lint | 0 errors, 5 warnings (listed in §4) |
| Debug APK | 16.5 MB, **no native libraries** → nothing to align for 16 KB-page devices |

## 2. What was tested on the device

✅ = works as designed. Numbers in brackets point to findings in §3.

### People (one-tap WhatsApp calls)

| Test | Result |
|------|--------|
| Add a person, local number `01711-123456` | ✅ saved as `+8801711123456` |
| Duplicate number written differently (`+880 1711 123456`) | ✅ "Already saved as Mum"; error clears while typing |
| Invalid number (`12345`) | ✅ "Enter a valid number…" |
| Edit name, Remove → snackbar → Undo | ✅ comes back with the same id |
| First call → READ_CONTACTS prompt → **Don't allow** | ✅ still works (falls back) — "WhatsApp isn't installed" |
| Call with (fake) WhatsApp installed + contacts allowed | ✅ `ACTION_VIEW content://com.android.contacts/data/3` + voip MIME type — **direct voice call path works** |
| Call someone without a WhatsApp row in the phone book | ✅ `https://wa.me/8801811123456` sent to package `com.whatsapp` |
| Snackbar explaining the chat fallback | ❌ lost if you stay in WhatsApp > 10 s [#3] |
| "Choose from phone contacts" (Android 17 routes it through the new system ContactsPicker → Google Contacts) | ✅ name + number filled (one failure on Google Contacts' very first run, not our bug [#2]) |
| ⋮ → Add to home screen → launcher sheet → tap the icon | ✅ call starts via `CallActivity` (icon looks off [minor]) |
| Another app starting `CallActivity` directly | ❌ it can make the phone call your saved people [#4] |
| "Add person" button with TalkBack | ❌ read as just "Button" [#1] |

### Reminders (custom notifications)

| Test | Result |
|------|--------|
| Banner when notifications are off; "Send now" asks for POST_NOTIFICATIONS | ✅ banner disappears after Allow |
| Send now | ✅ high-importance notification, channel `reminders`, Snooze / Done |
| Typed text | ❌ "Stay hydrated" arrived as "Stay atedh" once [#5] |
| Done | ✅ notification removed, delivered row deleted |
| In 5 min + "Call Mom" button | ✅ exact alarm (`window=0`, `exactAllowReason=policy_permission`), **fired 08:33:20 for 08:33:19** |
| "Call Mom" button on the notification | ✅ voice call started, notification cleared |
| Snooze 10 min | ✅ one-off copy at +10 min, alarm re-armed, original removed |
| Pick time (platform time picker) + Repeat every day | ✅ "Reminder set for Today, 8:38 AM, then every day" |
| **Reboot** (`adb reboot`) | ✅ both alarms back within ~10 s of boot (`BootReceiver`) |
| Daily reminder fires | ✅ 08:38:01, re-armed for **tomorrow 08:38** |
| ✕ on an upcoming reminder | ✅ row and alarm both removed |

### Player (YouTube background audio)

| Test | Result |
|------|--------|
| Paste `https://youtu.be/dQw4w9WgXcQ` → Open link | ✅ NewPipeExtractor v0.26.5 works against live YouTube (~10 s first time, ~1 s once warm); title, channel, length, thumbnail |
| Play now | ✅ PLAYING ~3.5 s after the tap; audio focus `USAGE_MEDIA/MUSIC`; foreground service type `mediaPlayback` |
| Home button, then screen off | ✅ keeps playing (position 18 s → 27 s while asleep) |
| System media controls (shade) | ✅ artwork, title, artist, progress; pause from the shade → app UI updates |
| Share from another app (`ACTION_SEND text/plain`) | ✅ "My Companion — Play in My Companion" in the share sheet; bottom sheet appears |
| Share sheet → Add to queue | ✅ queue = 2 videos; tap the 2nd → it plays |
| Share sheet → Add to playlist → new name | ❌ "Create & add" hidden behind the keyboard [#7]; ✅ works once the keyboard is closed |
| Share sheet look | ❌ black status/navigation bars, clock invisible [#6] |
| Rename playlist, Play all (after a reboot) | ✅ |
| Seek (tap middle of the bar) | ✅ 140.8 s of 282 s |
| Repeat off → all → one → off, Shuffle on/off | ✅ round-trips through the player |
| Non-YouTube text | ✅ "That's not a YouTube video link" |
| Video that doesn't exist | ✅ error shown, ❌ but as "JSON response is too short" [#8] |

### Robustness

| Test | Result |
|------|--------|
| Rotate with a half-typed reminder, and with the Add-person dialog open | ✅ text, choice, dialog all kept |
| Process death (`am kill` in background), reopen from Recents | ✅ tab restored; drafts are lost (ViewModel only — by design, see §5) ❌ player service comes back as a zombie [#9] |
| Dark mode, font scale 1.5 | ✅ all three tabs readable, nothing clipped |
| `monkey` 5 000 + 15 000 random events (seeds 42, 7) | ✅ **no crash, no ANR**; data with emoji/newlines stored and shown fine |

---

## 3. Findings

### #1 "Add person" button has no accessible name — accessibility
- **Evidence:** `uiautomator dump` shows the FAB as `NAF="true"` (clickable, no text). Material 3 1.2.1's
  `ExtendedFloatingActionButton(text, icon, …)` wraps `text` in `clearAndSetSemantics {}` (checked in
  the bytecode), and our icon has `contentDescription = null`.
- **Fix:** give the icon `contentDescription = "Add person"`; add a Compose test that finds the FAB by it.
- File: `presentation/contacts/ContactsScreen.kt`.

### #2 Contact pick lost on Google Contacts' first run — not our bug
- Android 17 sends `ACTION_PICK` through `com.android.contactspicker` → chooser → Google Contacts with
  `FLAG_ACTIVITY_FORWARD_RESULT`. On its very first run Google Contacts asked for its own permission and
  then opened a **new** picker, which broke the result chain, so we got "cancelled". Second try worked
  (`dumpsys activity activities` showed `resultTo=MainActivity`). Our code handled the cancel correctly.
- **Lesson:** results can be lost by apps in the middle of the chain — always handle `null`.

### #3 Messages shown while the app is in the background are lost
- **Evidence:** call someone without a WhatsApp row → WhatsApp opens → come back after > 10 s: no
  snackbar. Come back within 2 s: the snackbar is there.
- **Cause:** each `*Route` collects `viewModel.events` in a plain `LaunchedEffect`, which keeps running
  while the activity is stopped; the snackbar plays and expires unseen.
- **Fix:** collect events only while STARTED (`lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`),
  e.g. a small `ObserveAsEvents(flow) { }` helper used by all three routes. Events wait in the
  `Channel` until you return.

### #4 Any app can make the phone call your saved people — security
- **Evidence:** `adb shell am start -n com.akib.mycompanion/.presentation.call.CallActivity --el contact_id 2`
  opened WhatsApp for Dad with no tap (for Mom it would start a voice call). Ids are small and sequential.
- **Cause:** `CallActivity` is `exported="true"` (pinned shortcuts on Android 7.x need that) and trusts
  any intent.
- **Fix (planned):** a per-install secret token (random, stored privately) added to shortcut and
  notification intents and checked in `CallActivity`; refresh existing pinned shortcuts at startup
  with `ShortcutManagerCompat.updateShortcuts` so they keep working. (Note: on Android 8+ the launcher
  starts shortcuts *as our app* — the log showed `from uid 10230 (com.akib.mycompanion)`.)

### #5 Fast typing right after focusing a field can drop or reorder letters
- **Evidence:** "Stay hydrated" → "Stay atedh"; in a controlled replay (title → message, typed
  immediately) 2 of 5 runs were corrupted ("Drk wateri", "nk w" / "atertay hydrated").
  Typing into an already-focused field was 6/6 correct.
- **Cause:** text goes TextField → ViewModel `MutableStateFlow` → `combine` → `stateIn` →
  `collectAsStateWithLifecycle()`. The last step runs on Compose's frame dispatcher, so for a moment
  the field gets an **older** value back; the keyboard re-syncs to it and the cursor lands in the wrong
  place. (This is the "don't use async state for TextField" problem from the Compose docs.)
- **Fix:** collect UI state with `collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)`
  so the new text is written synchronously (or keep text in Compose `mutableStateOf` in the ViewModel).
  Re-run the 5-run replay afterwards (expect 0/5).

### #6 Share sheet: black system bars, invisible clock — cosmetic
- `Theme.MyCompanion.Invisible` (`Theme.Translucent.NoTitleBar`) doesn't draw system-bar backgrounds,
  so the bars are black and the light-theme (dark) icons disappear on them.
- **Fix:** `android:windowDrawsSystemBarBackgrounds=true` + transparent bar colours in that theme and
  `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))` in `ShareActivity`.

### #7 Share sheet: "Create & add" hidden behind the keyboard
- `ShareActivity` is edge-to-edge, so the window isn't resized for the keyboard; the sheet only pads for
  the navigation bar. The field's ✓ key doesn't submit either.
- **Fix:** `Modifier.imePadding()` on the sheet; `keyboardActions = KeyboardActions(onDone = …)` on the
  new-playlist field. (Check the main screens too: same edge-to-edge setup.)

### #8 Raw extractor errors shown to the user
- "Couldn't load the video: **JSON response is too short**" for a missing video.
- **Fix:** in `NewPipeYouTube`, map exceptions (most specific first) to plain messages:
  `AgeRestrictedContentException`, `PrivateContentException`, `GeographicRestrictionException` /
  `UnsupportedContentInCountryException`, `PaidContentException` / `YoutubeMusicPremiumContentException`,
  `AccountTerminatedException`, other `ContentNotAvailableException`, `SignInConfirmNotBotException`,
  `ReCaptchaException`, `ParsingException` ("extractor may need an update"), plain `IOException`
  ("check your connection"). Same idea for player errors (`errorCodeName` → readable text).

### #9 Player service restarts as an idle "zombie" after process death — resources
- **Evidence:** after `am kill`, a new process started at once with `PlaybackService` running:
  `startCommandResult=1` (START_STICKY), `restartCount=1`. Its queue is empty (the queue lives in memory),
  so it just holds an ExoPlayer + media session in the background.
- **Cause:** Media3 1.4.1 `MediaSessionService.onStartCommand` returns `START_STICKY` on every path.
- **Fix:** override `onStartCommand`, call `super` (keeps media-button handling), return `START_NOT_STICKY`.

### Minor / polish (also planned)
- People empty state: description text is left-aligned under centred content (`textAlign = Center`).
- Pinned-shortcut icon isn't adaptive → launcher puts it in a white circle. Use an adaptive icon.
- Removing a person leaves their pinned shortcut working (tap → "removed" toast). Disable on remove,
  re-enable on Undo (`ShortcutManagerCompat.disableShortcuts/enableShortcuts`).
- Delivered one-off reminders stay in the database until the next reboot/app update
  (only `RestoreRemindersUseCase` cleans up). Also clean up at app start.
- Reminder times are always 12-hour (`h:mm a`) even when the phone uses 24-hour time.
- Tapping a reminder notification opens the app but not the Reminders tab.
- `CallActivity` queries the Contacts provider on the main thread (the People screen uses IO).
- Media3 logs "implement `onPlaybackResumption()`" when a media button arrives with an empty queue —
  a future feature (persist the last queue, then resume from headphones / system UI).
- `System: A resource failed to call release` appeared 4× during monkey testing — not investigated yet
  (enable StrictMode `detectLeakedClosableObjects()` in debug to get stack traces).
- Lint warnings: redundant `@field:` on Dagger qualifiers (`ReminderReceiver`, `BootReceiver`);
  adaptive icon without `<monochrome>` (no themed icon on Android 13+); `KaptUsageInsteadOfKsp`
  (documented decision); `ExportedService` (expected for `MediaSessionService` — could restrict
  untrusted controllers in `onConnect`).

## 4. Not tested yet

Paste button (clipboard), "Open with" from a browser (`ACTION_VIEW`), headphones unplugged / Bluetooth
buttons, audio focus during a phone call, delete playlist, Shuffle play, Save queue as playlist,
notification permission denied twice → settings, Android 7.x (minSdk 24), and a real phone with the real
WhatsApp.

## 5. Design notes worth knowing (not bugs)

- Drafts (reminder form, add-person dialog) live in ViewModels: they survive rotation, not process
  death. `SavedStateHandle` would keep them — a nice learning exercise.
- The player queue lives in the service; it's gone when the service stops (README says so). Saving the
  last queue would enable playback resumption.

## 6. How to continue (office PC)

```bash
git fetch origin && git switch test/device-testing
export JAVA_HOME=/path/to/jdk-17          # Kotlin 1.9 / AGP 8.4 do not run on JDK 21+
./gradlew testDebugUnitTest               # should still be 202 green
```

Next steps, in order:
1. Fix #1, #3–#9 and the minor items — each with a test (JVM test where possible).
2. Re-run the device checks above, including the 5-run typing replay for #5
   (tools and gotchas: [`tools/device-test/README.md`](../tools/device-test/README.md)).
3. Add a few on-device tests (`androidTest`): Room migration on a real SQLite, and the player service
   playing a local test stream end to end.
4. Rewrite the documentation with detailed learning notes (outdated today: `01-hello-world-explained.md`,
   `02-quick-reference.md`, the learning path in `learning/` still assumes the Hello World app,
   `references/*` have errors such as `2 ^ attempt` and deprecated APIs, and the index lists files that
   don't exist).
