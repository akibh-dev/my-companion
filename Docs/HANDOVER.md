# Handover — where to start (written 2026-10-25, office PC)

Read this first on the home laptop. Then `Docs/implementation-guide.md` (conventions, pitfalls,
architecture contribution points) and `Docs/decisions.md` (every decision so far).

## 1. Where the code is

All work is pushed to GitHub (`akibh-dev/my-companion`). The branches are **stacked**: each one
starts from the one above it. Merge them into `main` in this order (one PR each, or one big PR of
the last branch):

| # | Branch | What's in it | Checked |
|---|--------|--------------|---------|
| 1 | `test/device-testing` | Fixes for device-test findings #1, #3–#9 and the minor list | CI green, partly checked on emulator |
| 2 | `feat/recurring-reminders` | Repeat: every day / chosen weekdays / monthly / every N hours | CI green |
| 3 | `phase-0/setup` | SDK 36, AGP 8.13, Kotlin 2.2; modules; AI interfaces + dummy models; log, heartbeat, Home, permissions guide, pause tile | CI green |
| 4 | `phase-1/context-rules` | Context rules, saved places, offline map picker | CI green |
| 5 | `phase-2/messaging` | Notification adapters, auto-reply, missed-call SMS, voice mode | CI green |
| 6 | `phase-3/vault` | Encrypted vault, share target, viewers, backup | CI green |
| 7 | `phase-4-5/parallel` | Implementation guide; W1 quick capture; W2 keep screen on outdoors; W3 Phase 5 polish + typing fix (see §3) | Full check green: 377 unit tests, ktlint, lint, APK |

`main` itself still has only the old app (4aa8313). Nothing from 1–7 is merged yet.

## 2. Set up the home laptop

```bash
git fetch origin
git switch phase-4-5/parallel        # the newest branch, contains everything
git config user.name akib35
git config user.email mdakibhasan554@gmail.com
```

- JDK **17** for Gradle (`JAVA_HOME`), e.g. Corretto 17. Unit tests need **JDK 21**: Gradle
  downloads it automatically (foojay plugin) the first time.
- Android SDK platform 36 is downloaded by Gradle if missing (licences must be accepted).
- If the laptop has ≤ 16 GB RAM, always build with `--max-workers=3` (parallel Robolectric test
  JVMs crashed the office PC once).

The full check (same as CI):

```bash
./gradlew ktlintFormat ktlintCheck testDebugUnitTest lintDebug assembleDebug --continue --max-workers=3
```

## 3. State of the last branch (`phase-4-5/parallel`)

Built from `Docs/implementation-guide.md` by three parallel workstreams:

- **W1 Quick capture (Phase 4)** — merged, module compiles, its 24 tests pass, app compiles.
- **W2 Keep screen on outdoors (Phase 4)** — merged, compiles, tests pass.
- **W3 Phase 5 polish + typing fix** — merged, compiles, tests pass: typing bug #5 fixed with
  `TextFieldState` (text owned by Compose, not a StateFlow), reminders on vault items
  (`ReminderSink`), quick-actions widget, first-run guide, "Export my data" + settings backup.

Everything on this branch compiles and the full check is green, but **none of Phases 0–5 has
run on a real phone yet**, and the new Phase 4–5 code hasn't run on the emulator either.

## 4. Next steps, in order

1. **Run the full check** (§2) once to confirm the laptop's setup (should be green).
2. **Small items left over:**
   - Capture photos ignore EXIF rotation (no ExifInterface dependency yet). Add
     `androidx.exifinterface` and rotate when decoding, or accept it.
   - `PlaceDetector` is only started by context rules; keep-screen-on falls back to last-known
     location + Wi-Fi. Optional: reference-counted `start/stop` so both features can share it.
3. **Open PRs** in the order of §1 and merge them.
4. **Device testing on the real phone (CMF Phone 1)** — the plan's spikes S1–S10
   (`Docs/decisions.md` lists them): notification access + restricted settings (S8), reply
   actions per chat app (S4, use Home → Auto-reply → Chat app self-test), volume-key capture
   (S2), keep-screen-on overlay (S3), places detection (S7), background survival with the
   heartbeat log (S6, Home → Log), offline map file (S10, `Docs/offline-map.md`). Write each result
   in `Docs/decisions.md`.
   Also re-run the typing replay from device finding #5 (expect 0 of 5 corrupted now).
5. **Tune on the phone:** keep-screen-on angles/hand-shake thresholds (`screenon/PostureDetector.kt`),
   the 1-second volume combo, auto-reply wording.
6. **Track B (models):** replace dummies in `ai-models/` one at a time (they implement the
   interfaces in `ai-api/`; switch per engine in Home → AI engines). Nothing else changes.
7. Later: move to AGP 9 (separate step), Docs rewrite for learning (old docs still describe the
   Hello-World app), remove the unused `ksp.*` lines in `gradle.properties`.

## 5. Things to know

- Commits are authored as `akib35`, with **no** AI attribution lines (`CLAUDE.md`).
- Push with the GitHub account `akibh-dev` (`gh auth status`).
- Uncommitted on the office PC on purpose: `.idea/misc.xml`, the `gradle.properties` line Android
  Studio added, and the plan `.docx` in `Docs/` (copy it to the laptop by hand if needed).
- The emulator AVD on the office PC is `Medium_Phone_API_37.0`; device-test helpers are in
  `tools/device-test/` (fake WhatsApp, UI driver).
