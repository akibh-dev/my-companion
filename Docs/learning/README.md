# Learning Path — Start Here

A step-by-step path from "Hello World" to a working personal companion app
(custom notifications, background YouTube audio, notification listener, tiles).

Each step is **one small feature you build yourself**, plus the Android concepts behind it.
The steps are ordered so that every step only uses things you learned earlier.

---

## How to Start (Do This Once)

### 1. Check your tools

| Tool | Required | Check with |
|------|----------|------------|
| JDK | 17 | `java -version` |
| Android SDK | Platform 34 + platform-tools | `adb version` |
| IDE | Android Studio or IntelliJ IDEA (with Android plugin) | — |
| Device | Phone with wireless debugging, or an emulator (API 24+) | `adb devices` |
| Git | Configured as `akib35` | `git config user.name` |

If `adb` is not found, see the ADB section of [`../02-quick-reference.md`](../02-quick-reference.md).

### 2. Confirm the Hello World app still runs

```bash
./gradlew :app:installDebug
adb shell am start -n com.akib.mycompanion/.MainActivity
```

You should see "Hello World!" on your phone. If the build fails, check the version table in
[`../02-quick-reference.md`](../02-quick-reference.md) (JDK 17 is the usual culprit).

### 3. Read these once (≈30 min)

1. [`../01-hello-world-explained.md`](../01-hello-world-explained.md) — what every file does
2. [`../02-quick-reference.md`](../02-quick-reference.md) — commands and versions
3. [`../architecture/001-mvvm-clean.md`](../architecture/001-mvvm-clean.md) — skim only; it will make sense around Step 05

### 4. Open Step 01 and follow the loop below

---

## The Loop (Every Step)

```
 ┌─▶ 1. Read the step file (concepts first, code second)
 │   2. Create a branch:   git switch -c feat/step-NN-short-name
 │   3. Build it yourself — type the code, don't paste it
 │   4. Run it on the device, try the "Break It" experiments
 │   5. Answer the checkpoint questions without looking
 │   6. Fill in "My Learning Note" at the bottom of the step file
 │   7. Commit (conventional commits, in your name only), push, open a PR, merge
 └── 8. Tick the step in the table below → next step
```

**Rules of thumb**
- One step per branch/PR. Small PRs are easier to review — even by yourself.
- Stuck for more than 30 minutes? Write the problem in your note, read the linked official docs, then ask.
- Don't skip "Break It" experiments. Seeing the wrong behaviour is how the right one sticks.

---

## The Full Path

Status: ⬜ not started · 🟨 in progress · ✅ done

### Phase 0 — Compose & App Foundations
Goal: be comfortable building screens and structuring an app.

| # | Step | You will build | Key concepts | Status |
|---|------|----------------|--------------|--------|
| 01 | [Compose State & Events](01-compose-state.md) | Counter + feature toggles on Home | Recomposition, `remember`, state hoisting, `rememberSaveable` | ⬜ |
| 02 | Layouts, Lists & Theming | Scrollable feature list with your own Material 3 theme | `Column`/`Row`/`Box`, `LazyColumn`, `Modifier` order, `ui/theme` | ⬜ |
| 03 | Gradle Housekeeping | Move deps to the version catalog, Compose BOM, ktlint | `libs.versions.toml`, BOM, plugins, `./gradlew ktlintCheck` | ⬜ |
| 04 | Activity Lifecycle | Lifecycle logger; survive rotation & process death | `onCreate`…`onDestroy`, config changes, `adb` process kill | ⬜ |
| 05 | ViewModel & UI State | Home screen driven by a ViewModel | `ViewModel`, `StateFlow`, sealed `UiState`, unidirectional data flow | ⬜ |
| 06 | Navigation | Home ⇄ Settings ⇄ About screens | `NavHost`, routes, arguments, back stack | ⬜ |
| 07 | Dependency Injection (Hilt) | `@HiltAndroidApp` app, injected ViewModels | `@Inject`, `@Module`, scopes, KSP/kapt | ⬜ |
| 08 | Clean Layers & Unit Tests | `domain/` + `data/` packages, first use case with tests | Repository pattern, JUnit, MockK, Turbine | ⬜ |

### Phase 1 — Custom Notifications (MVP feature P0)
Goal: create, schedule and act on your own notifications.

| # | Step | You will build | Key concepts | Status |
|---|------|----------------|--------------|--------|
| 09 | First Notification | "Send test notification" button | `NotificationChannel`, `NotificationCompat`, `POST_NOTIFICATIONS` runtime permission | ⬜ |
| 10 | Notification Actions | "Snooze" / "Done" buttons on the notification | `PendingIntent` (immutable/mutable), `BroadcastReceiver` | ⬜ |
| 11 | Settings with DataStore | Persisted settings screen | Preferences DataStore, `Flow` from disk | ⬜ |
| 12 | Scheduled Notifications | Daily / periodic reminders | `WorkManager` vs `AlarmManager`, constraints, exact alarm permission | ⬜ |
| 13 | Room Database | Saved reminder templates + history | `@Entity`, `@Dao`, migrations, Room + Flow | ⬜ |

### Phase 2 — Background Media (foundation for P1)
Goal: play audio that survives the screen turning off.

| # | Step | You will build | Key concepts | Status |
|---|------|----------------|--------------|--------|
| 14 | Services 101 | A foreground service with an ongoing notification | Service lifecycle, foreground service types (Android 14) | ⬜ |
| 15 | ExoPlayer Basics | Play a local / sample audio file | Media3 `ExoPlayer`, `MediaItem`, player states | ⬜ |
| 16 | MediaSessionService | Lock-screen & Bluetooth controls | `MediaSessionService`, `MediaController`, audio focus, "becoming noisy" | ⬜ |

### Phase 3 — YouTube Background Playback (P1)
Goal: share a YouTube link to the app and listen with the screen off.

| # | Step | You will build | Key concepts | Status |
|---|------|----------------|--------------|--------|
| 17 | Networking Basics | Fetch & parse JSON from a public API | Retrofit/OkHttp, coroutines, error handling | ⬜ |
| 18 | Share Intent | "Share → My Companion" from the YouTube app | Intent filters, `ACTION_SEND`, parsing URLs | ⬜ |
| 19 | Stream Extraction | Video ID → audio stream URL | NewPipeExtractor, background threads, expiring URLs | ⬜ |
| 20 | Streaming Playback & Queue | Background YouTube audio with a queue | Progressive/DASH sources, buffering, caching, retries | ⬜ |

### Phase 4 — System Integrations (P2–P4)

| # | Step | You will build | Key concepts | Status |
|---|------|----------------|--------------|--------|
| 21 | Notification Listener | Read & filter other apps' notifications | `NotificationListenerService`, special-access permissions | ⬜ |
| 22 | Quick Settings Tile | Toggle playback from the shade | `TileService` | ⬜ |
| 23 | Home Screen Widget | Playback widget | Jetpack Glance | ⬜ |
| 24 | Floating Overlay | Floating playback controls | `SYSTEM_ALERT_WINDOW`, `WindowManager` | ⬜ |

> Step files are written one at a time, just before you start each step,
> so they match what the code actually looks like by then.

---

## Where Things Go

| What | Where |
|------|-------|
| Step guide + your learning note for that step | `Docs/learning/NN-name.md` |
| A decision you made ("why X over Y") | `Docs/architecture/NNN-title.md` (ADR template in [`../00-index.md`](../00-index.md)) |
| A cheat sheet you keep coming back to | `Docs/references/` |
| A great article or video | `Docs/resources/` |
