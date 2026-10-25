# Feature test report — 2026-10-25

Branch: `feat/capture-photo-orientation`, based on `origin/test/phase-0-5-coverage`.

All **675 unit/Robolectric/Compose JVM tests passed**, with no failures, errors or skips.
Test result caching was disabled for the fresh run. Formatting and Android lint also passed;
lint still reports warnings. The debug app and instrumentation APKs build successfully.

## Environment

- Gradle: JDK 17; JVM test toolchain: JDK 21.
- Emulator: dedicated `Companion_Test`, Android 17 / API 37, x86_64, 16 KB pages.
- App: compile/target/min SDK 36. This run does not establish Android 16 device acceptance.
- Hardware GPU renderer, Vulkan disabled. Fedora's SELinux audit log identified an `execheap`
  denial in the software renderer; the host renderer booted successfully.
- An Android Studio build was using the checkout's generated files concurrently. Final validation
  used a source copy at `out/device-workspace/` with separate outputs and two Gradle workers.
- Every ADB action targeted `emulator-5554` explicitly. The other running AVD was not used.
- Fixtures contain invented names, numbers, messages and notes. No real chat account was used.

## JVM results by module

| Module | Passed | Coverage exercised by its existing suites |
|---|---:|---|
| `app` | 286 | People, reminders and recurrence, player/queues/playlists, ViewModels, Compose screens, receivers, shortcuts, widgets, export and graph wiring |
| `core` | 48 | Settings, encryption, service host, heartbeat, log, utility logic |
| `ai-api` | 21 | Model routing, fallbacks and loader behavior |
| `ai-models` | 11 | Dummy model implementations |
| `adapters-messaging` | 30 | Chat adapters, notification extraction and reply handling |
| `feature-context` | 89 | Rules, places, device settings, repositories, posture/outside logic and screen-on components |
| `feature-autoreply` | 42 | Audience, templates, cooldown, settings, repository, message and missed-call consumers |
| `feature-voice` | 24 | Privacy, reading queue, text formatting, audio situation, reader, consumer and settings |
| `feature-vault` | 62 | Crypto, items, search, backup, inbox, previews, ViewModels and auto-lock |
| `feature-capture` | 62 | Volume combo, search, repository, processors, ViewModels, setup and all eight EXIF orientations |
| **Total** | **675** | These counts are tests, not a measured code-coverage percentage. |

## Automated tests on the emulator

**22 passed, 0 failed** in the final run (24.5 seconds), using the exact app and test APKs
built from the isolated source copy. No tests were skipped.

The new suites under `app/src/androidTest/` use the real application and Android framework.

| Suite | Tests | What is checked |
|---|---:|---|
| `DeviceStorageTest` | 4 | Android Keystore round-trip/tamper rejection; encrypted capture repository on real SQLite; multi-chunk vault crypto and truncation rejection; v1→v2 Room migration preserving contacts and reminders |
| `DeviceComponentsTest` | 2 | Exact system alarm → real receiver → notification with Snooze/Done; real MessagingStyle extraction for all six app variants and a RemoteInput reply delivered through PendingIntent to a fixture receiver |
| `FeatureDeviceTest` | 16 | Master pause and restore; contact save/normalization/removal; five rapid reminder field replacements; invalid YouTube link; playlist create/open/delete; rules/places/history navigation; auto-reply busy state/template editor; missed-call settings/chat diagnostics; voice sample; capture search and note save/open/delete; screen-on sensitivity; vault authentication gate; export/backup controls; four AI engines; permissions and heartbeat log |

The MessagingStyle test constructs synthetic notifications. It does **not** prove that each real
chat app exposes the same notification structure or accepts replies on a phone.
The voice sample test exercises the action and displayed sample; it does not assess audible voice quality.

## Additional interactive emulator checks

| Area | Result and evidence |
|---|---|
| Live YouTube sharing/playback | A shared YouTube URL resolved title/author/duration, then played through `PlaybackService`. System service dump confirmed `isForeground=true`, media playback type `0x2`. |
| Screen-off/background playback | `dumpsys media_session`: PLAYING at position 26,870 ms before sleeping, then PLAYING at 59,909 ms while backgrounded with the screen off; no playback error. System media pause also worked. |
| WhatsApp direct call | Seeded a phone-book WhatsApp row; tapping the app's call button reached the fake WhatsApp APK with `ACTION_VIEW`, `content://com.android.contacts/data/3`, and WhatsApp voice-call MIME type. |
| WhatsApp fallback | A contact without that row reached the fake APK with `https://wa.me/8801811123456`. No real call or message was sent. |
| Keyboard focus replay | Five runs switched between title and message and immediately injected text via Android input events. All five retained both expected strings; 0/5 corruptions. This complements the Compose semantics test. |
| Vault | Set a synthetic emulator PIN, authenticated through Android's system credential prompt, created/read a note, locked/unlocked and read it again, moved it to trash and restored it. PIN was only set on the temporary test AVD. |
| Export | Saved the data ZIP through the real Storage Access Framework picker, pulled it from emulator Downloads, opened it, and parsed every JSON entry. UI reported eight files saved. Vault exclusion was displayed. |
| Settings backup | Saved settings through the real picker. Restore controls are covered by the device suite; settings serialization/import logic is covered by JVM tests. |

Raw evidence is in git-ignored `out/emulator-test/`: build/unit/lint logs, instrumentation output,
IME replay JSON, media-session/service dumps, fake WhatsApp intents, and the synthetic export ZIP.
JVM XML/HTML reports are under `out/device-workspace/*/build/`; APKs are under
`out/device-workspace/app/build/outputs/apk/`.

## What remains unverified

This is not complete acceptance of every requirement. The following need dedicated further checks:

- A real Android 16 phone: OEM battery management, reboot/permission-revocation survival, physical
  volume-key combination, real chat apps and missed-call SMS delivery, headset/Bluetooth/call audio
  focus, GPS/Wi-Fi arrival detection, and the posture-driven overlay over other apps.
- Camera/recording/screenshot capture end to end, photo EXIF appearance on a real camera output,
  actual launcher pinning/widget placement and tile interactions. Their logic has JVM coverage;
  the device suite covers capture notes and screen-on settings, not these hardware/system flows.
- A local PMTiles map file and airplane-mode rendering. No map asset was installed during this run.
- Vault image/PDF viewers and passphrase backup/restore through the picker, settings restore through
  the picker, longer player/network stress, and process-death/reboot flows on this expanded app.
- Actual AI models and voice quality: the model implementations remain dummies by design.
- Battery/RAM targets and the seven-day soak test from the implementation plan.

## Repeating the checks

Start a disposable emulator (on this Fedora host, select host graphics), confirm it is authorized
with `adb devices`, then run from the repository root:

```bash
export JAVA_HOME="$HOME/.jdks/corretto-17.0.20.1"
ADB_SERIAL=emulator-5554 tools/device-test/run-feature-tests.sh
```

The script runs every module's unit suite with cached test results disabled, checks formatting/lint,
builds both APKs, installs them, and invokes AndroidJUnitRunner on the explicit serial. It treats a
missing JUnit success footer as failure even if `am instrument` returns exit status zero.
Run it on a test emulator: UI tests create/remove synthetic records, change and restore several
settings, and the alarm integration test grants notification permission.
Avoid concurrent builds in the same checkout. The current run used an isolated source copy to
avoid changing Android Studio's build outputs.

The instrumentation dependency explicitly selects Espresso 3.7.0 because the older transitive
version used reflection on `InputManager.getInstance`, which is absent on API 37.
See [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test)
and [Compose testing](https://developer.android.com/develop/ui/compose/testing).

---

## Session 2 — plan gaps closed and a UI walk-through (2026-10-25, home laptop)

Emulator: `Pixel_8`, Android 17 / API 37, 16 KB pages, **with its window shown** (host GPU,
`-feature -Vulkan`). Same checkout, no isolated copy.

### What the plan still lacked, now built (each with tests)

| Plan item | What was missing | Now |
|---|---|---|
| FR-3.7 "app in the foreground" trigger (Phase 1) | No trigger type at all | `Trigger.AppInForeground`; `ForegroundApps` reads the usage log (special access "Usage access") every 3 s, **only while an enabled rule uses it**; nothing while the screen is off; app picker in the rule editor; permission-guide entry |
| NFR-6 speed checks (Phase 5) | Not measured | `SpeedChecksTest` on the device: vault unlock + search with 500 items, capture panel open |
| Handover leftover: `PlaceDetector` shared | Only rules started it; keep-screen-on fell back to the last fix | Reference-counted `start(scope)`: alerts registered once, handed over when one feature stops, removed when the last stops; keep-screen-on starts it too |

### Found while testing the UI, fixed (each with a test)

| Finding | Fix |
|---|---|
| Home: the pause switch was unlabelled; "off" meant running | "Pause" label and a screen-reader description |
| Vault: "Set a screen lock first" had no way forward, and didn't notice a lock set meanwhile | "Set a screen lock" button (Android's lock chooser); state re-read on every resume |
| Capture space: the volume-key switch said "Hold both for a second…" while the accessibility service was off | Says the service is off and offers "Turn on the accessibility service"; re-checked on resume |
| Photo capture: Android 17 logs that the implicit URI grant for `IMAGE_CAPTURE` ends with Android 18 | Explicit grant (ClipData + read/write flags) in `TakePictureWithGrant`; warning gone on the device |
| Permission guide step text for Usage access | Matches Android 17's wording ("Permit access to app usage data") |
| Feature description on Home | Mentions the app-in-use trigger |

### Results

- **JVM:** 710 tests, 0 failures (core 48, ai-api 21, ai-models 11, adapters-messaging 30,
  feature-context 114, feature-autoreply 42, feature-voice 24, feature-vault 64, feature-capture 69,
  app 287). ktlint clean; Android lint 0 errors.
- **On the device:** 24 instrumented tests, 0 failures (the 22 above + 2 speed checks).

| Speed check (plan NFR-6) | Target | Emulator |
|---|---|---|
| Capture panel opens | < 1 s | 440–634 ms |
| Vault unlock + index of 500 items | < 1 s | 69–77 ms |
| Vault search per keystroke (5 queries) | < 1 s | 3–23 ms |

### Checked by hand in the UI (emulator window)

| Flow | Result |
|---|---|
| Home → Fix → Usage access → "Open settings" | Lands directly on My Companion's page; granted (adb install: no restricted-settings block); guide goes 2 → 3 of 15 on return |
| Rule editor → Add "when" → App in use | Real installed apps, alphabetical, searchable; OK disabled until one is picked; "Using Chrome" shown; rule saved |
| Rule "Using Chrome → Vibrate" | Chrome opened → ringer VIBRATE in **2 s**; Home → NORMAL in **2 s**; screen off → NORMAL in 2 s; screen on with Chrome in front → VIBRATE again; every change in History |
| Vault with no screen lock → button → set PIN in Android's screens → back | Vault switches to "Your vault · Set up" by itself |
| Vault set-up with the system credential prompt, add a note, search "locker 44" | Found |
| Vault data on disk (`run-as` copy of `vault.db` + WAL) | Note text, title and code appear **nowhere** in plain text; payload is an encrypted blob |
| Vault screen capture | Completely black (FLAG_SECURE) |
| Vault auto-lock | Locked after 65 s away; PIN unlock shows the note again |
| Capture: switch on, service off → "Turn on" → Accessibility → allow → back | Status updates to "Hold both for a second…" |
| Capture: Photo through the panel | Camera app → accept → "saved PHOTO"; detail shows it upright |
| Capture: Screenshot through the panel | Saved; shows the screen *under* the panel, not the panel |
| All ten feature screens, light mode | Laid out correctly, nothing clipped |

### Not verifiable on this emulator

- **Holding both volume keys (FR-4.1, spike S2):** neither `adb emu event send` nor
  `input keycombination` changes even the ring volume on this image — with our service **off** as
  well — so injected volume keys don't reach the system here. The combo logic has JVM tests; the
  real check stays on the phone with the physical buttons.
- Everything in "What remains unverified" above still applies.

### Testing gotchas learned

- `uiautomator dump` connects a UiAutomation, which **suspends every accessibility service** while it
  runs. Dumping with the capture panel open made the service disconnect and reconnect, the panel
  briefly showed "screenshots unavailable" and a tap landed on the wrong button. While an
  accessibility service matters, locate elements from a plain `screencap` instead.
- uiautomator writes attribute values in single quotes when they contain `"` (e.g. `Add a "when"`);
  `tools/device-test/ui.py` now reads both quote styles.
- With nothing playing, the volume keys change the **ring** stream, not media — measure stream 2.

Emulator state left behind: test PIN **1234** removed again at the end; usage access, the quick
capture accessibility service and one rule ("Quiet in Chrome") stay on the test emulator.
