# Implementation guide (Phases 4–5)

Everything needed to build the remaining features of the implementation plan
(`Docs/PAApp Implementation Plan with new features.docx`) in parallel without breaking each other.
Phases 0–3 are done: modules, AI seam with dummy models, log/heartbeat, Home, permissions guide,
pause tile, context rules + places + offline map, messaging (adapters, auto-reply, missed-call SMS,
voice mode), encrypted vault.

## 1. Ground rules for every workstream

1. **Don't run Gradle.** Builds and tests run once, centrally, after all workstreams finish
   (parallel Gradle runs exhaust the 19 GB machine). Write code that compiles first time: check
   imports, types and API names carefully against existing code in the repo.
2. **Only create or edit files in your ownership list** (§4). Shared files (`settings.gradle.kts`,
   root `build.gradle.kts`, `AppNavigation.kt`, `FeatureScreensModule.kt`, `core/settings/*`,
   `libs.versions.toml`) are changed by the integrator only. If you need one changed, write it
   under "Integration notes" at the end of your report.
3. **Commit** your work on your branch with conventional commits (`feat:`, `test:`). Author is
   taken from git config; **no** `Co-Authored-By` or "Generated with" lines (see `CLAUDE.md`).
4. Write unit tests for logic (JUnit4 + Truth; Robolectric when Android classes are involved).

## 2. Toolchain and style facts (learned the hard way)

| Topic | Rule |
|-------|------|
| SDK | compile/target/min **36** (Android 16). No `Build.VERSION.SDK_INT` checks, no `-v26` folders |
| Kotlin | 2.2.21, Compose compiler plugin, kapt for Hilt/Room |
| Line length | **max 120 chars**, ktlint android style. Wrap long argument lists one per line with trailing comma |
| Imports | No wildcards. Lexicographic, `java.*`, `javax.*`, `kotlin.*`, `kotlinx.*` after the rest. No unused imports |
| No semicolons | `onClick = { a = 1; b = 2 }` is not allowed — use new lines |
| Trailing commas | Required on multi-line parameter/argument lists |
| Interfaces + companion objects | **Never** store a list of an interface's nested objects in its companion (`val presets = listOf(Now, …)`): with Kotlin 2.2's JVM default methods it captures `null`. Use a getter (`get() = listOf(…)`) |
| Hilt workers | Don't use `@HiltWorker` (kapt can't read it under Kotlin 2.2). Use an `@EntryPoint` inside the worker (see `core/health/Heartbeat.kt`) |
| Qualifiers on fields | `@Inject @IoDispatcher lateinit var x` (no `@field:`) |
| Android constants in JVM tests | Platform `String` constants (e.g. `TelephonyManager.EXTRA_STATE_IDLE`) are **null** in plain JVM tests. Use Robolectric or literal values |
| Lint `MissingPermission` | Check the permission explicitly, then `@SuppressLint("MissingPermission") // checked above` on the function |
| SharedPreferences | Use `prefs.edit { }` from core-ktx (`commit = true` only when it must be on disk) |
| Notifications | Check `POST_NOTIFICATIONS` before `notify` |
| Foreground services | Declare `android:foregroundServiceType` + the matching `FOREGROUND_SERVICE_*` permission. Prefer hosting background work in core's `HostService` via `HostedComponent` |
| Receivers | `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` for runtime receivers |
| Docs comments | Plain English, explain *why*; reference requirement IDs (FR-x.y) |

### Library module template (only the integrator adds new modules)

Modules already exist for every workstream; their `build.gradle.kts` lists dependencies. Shared
Android/Kotlin settings come from the root build. Available in feature modules: core (api),
coroutines, Hilt, Compose (material3, lifecycle-compose, hilt-navigation-compose, activity-compose),
Room (where listed), DataStore (via core), test stack (JUnit, Truth, MockK, Turbine, Robolectric,
coroutines-test, compose ui-test).

## 3. Contribution points (how a feature plugs in without touching the app)

| Point | Where | How |
|-------|-------|-----|
| Feature switch | `core.settings.Feature` (`CAPTURE`, `KEEP_SCREEN_ON` exist) | `settingsStore.settings.first().isActive(Feature.X)` — false while paused |
| Permissions guide | `core.permissions.Requirement` | `@Provides @IntoSet fun x() = Requirement(id, title, why, feature, isGranted, settingsIntent, steps, runtimePermissions)` |
| Background work | `core.host.HostedComponent` | `@Binds @IntoSet`; `isWanted(settings)`, `start(scope)`; runs in core's foreground service |
| Chat messages | `messaging.MessageConsumer` | `@Binds @IntoSet` |
| Screens | `com.akib.mycompanion.Screen(route, title) { s -> … }` in the **app** | Each workstream adds its own file `app/src/main/java/com/akib/mycompanion/di/<Name>ScreensModule.kt` (`@Module @InstallIn(SingletonComponent::class) object …`, `@Provides @IntoSet`). `s.modifier`, `s.navigate(route)`, `s.back()`, `s.snackbars`. Route args: `"thing/{id}"`, read via `SavedStateHandle.get<String>("id")` |
| Home links | `com.akib.mycompanion.presentation.home.HomeLink(title, description, route)` | `@Provides @IntoSet` in the same screens module |
| Logging | `core.log.AppLog` | `log.i(TAG, "…")` — never log message text or vault content |
| Shared state | `core.context.ContextStatus` | active context-rule names |
| AI | `ai.Transliterator`, `SpeechSynth`, `SpeechRecognizer`, `Summarizer` | inject the interface; on `AiResult.NotReady` use the fallback behaviour (models are dummies today) |
| Vault | `core.sinks.VaultSink` | `saveNote`, `saveLink`, `saveFile` — works while the vault is locked |
| Reminders | `core.sinks.ReminderSink` | implemented by the app (W3) |
| Encryption | `core.crypto.FieldCipher.keystore(alias)` | AES-GCM with a Keystore key |

## 4. Workstreams and file ownership

### W1 — Quick capture (Phase 4: FR-4.1–4.9) · module `feature-capture`

Owns: everything under `feature-capture/`, and `app/src/main/java/com/akib/mycompanion/di/CaptureScreensModule.kt`.

1. **Volume-key trigger (FR-4.1, spike S2):** `CaptureAccessibilityService` (accessibility service
   with `canRequestFilterKeyEvents`, `flagRequestFilterKeyEvents`) — when **both** volume keys are
   held for **1 second** it opens the capture panel. Never consume single presses (volume must keep
   working). Pure-Kotlin `VolumeComboDetector(clock)` with `onKey(code, down, time)` → `Boolean`
   (fire) so it's unit-tested. Accessibility XML in `res/xml/capture_accessibility.xml`, description
   string resource. Only acts while `Feature.CAPTURE` is active.
2. **Capture panel (FR-4.2):** see-through `CapturePanelActivity` (translucent theme in the
   module's `res/values/themes.xml`, `excludeFromRecents`, `taskAffinity=""`, `showWhenLocked` for
   FR-4.8) with three big buttons: **Note** (text field, save), **Voice** (record with `MediaRecorder`
   to AAC/m4a in `filesDir/captures/`, tap to stop; RECORD_AUDIO runtime permission; foreground
   service type `microphone` while recording: `CaptureRecordingService`), **Photo**
   (`ActivityResultContracts.TakePicture` into a `FileProvider` uri under `filesDir/captures/`;
   declare the provider with authority `${applicationId}.captures` in the module manifest and
   `res/xml/capture_paths.xml`). Also openable from a Quick Settings tile `CaptureTileService` and
   from the app.
3. **Storage:** Room `capture.db`: `captures(id, type TEXT|VOICE|PHOTO|SCREENSHOT|CLIPBOARD, created,
   text_enc, file, transcript_enc, title_enc, summary_enc, actions_enc, moved_to_vault)`. Encrypt
   text fields with `FieldCipher.keystore("captures")` (plan §11: Capture is encrypted). Files stay
   in private storage.
4. **Capture space (FR-4.3):** screen with a timeline (newest first, grouped by day), search over
   decrypted text/transcript/title, filter chips by type; detail screen: play voice (`MediaPlayer`),
   show photo, edit text, delete, **Move to vault** (FR-4.9) via `VaultSink` (then delete locally
   and mark moved).
5. **AI hooks (FR-4.4–4.6):** after a voice capture, call `SpeechRecognizer.transcribe(file)`; store
   the transcript when `Ready`, otherwise keep audio only. Detail screen has **Summarise** only
   when `Summarizer` returns Ready (try once when opening the detail; hide the button on NotReady).
6. **Screenshot & clipboard (FR-4.7):** in the panel: **Clipboard** button saves the current clip
   text (read while the panel activity has focus); **Screenshot** uses the accessibility service's
   `takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)` → save PNG.
7. **Lock screen (FR-4.8):** panel works over the lock screen for new captures only; the capture
   space requires the phone to be unlocked (`KeyguardManager.isKeyguardLocked` → show "Unlock to
   see captures").
8. Requirements: accessibility service (with "Allow restricted settings" steps), microphone,
   camera (only `TakePicture` intent — no CAMERA permission needed), notifications.
9. Screens module: routes `capture` (capture space), `capture/{id}` (detail), Home link "Capture
   space". Tests: `VolumeComboDetector`, search/filter logic, repository with in-memory Room +
   a software `FieldCipher` (construct `FieldCipher { SecretKeySpec(ByteArray(32), "AES") }`).

### W2 — Keep the screen on outdoors (Phase 4: FR-3.5, FR-3.10) · module `feature-context`

Owns: new package `feature-context/src/main/java/com/akib/mycompanion/context/screenon/` (all new
files), new test files under `feature-context/src/test/.../screenon/`, a **new** manifest snippet is
not possible per file — so add your manifest entries to `feature-context/src/main/AndroidManifest.xml`
(you own edits to that file), and `app/src/main/java/com/akib/mycompanion/di/ScreenOnScreensModule.kt`.

1. **Posture detector:** pure-Kotlin `PostureDetector` fed accelerometer + gravity samples: "raised
   and facing you" = device pitch between ~25° and ~80° (screen tilted up toward the face), roll
   within ±35°, and not lying flat/still on a table; debounce (enter after 1.5 s, leave after 3 s).
   Unit-test it with synthetic samples.
2. **Outside check (FR-3.10):** "outside" = not inside any saved place **and** no saved Wi-Fi
   network in range. Use `PlaceDetector.inside` (existing) and connected/scan Wi-Fi names (see
   `ContextSensors`/`PlaceDetector` for how). Pure function `isOutside(insidePlaces, wifiInRange)`.
3. **Keep-awake method (spike S3):** an invisible overlay window (`TYPE_APPLICATION_OVERLAY`,
   1×1 px, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_KEEP_SCREEN_ON`) added only while
   (screen on) ∧ outside ∧ raised; removed otherwise. Needs "Display over other apps"
   (`Settings.canDrawOverlays`). Fallback when not granted: nothing happens + Requirement shows it.
4. **Runtime:** `ScreenOnComponent : HostedComponent` (wanted when `Feature.KEEP_SCREEN_ON`
   active): registers sensors (`SensorManager`, `SENSOR_DELAY_UI`) **only while the screen is on**
   (listen to `ACTION_SCREEN_ON/OFF`), so it costs nothing in the pocket. The host service type is
   `specialUse` already; motion sensing is allowed there.
5. **Screen:** route `screen-on`: switch for the feature, live status ("Outside · raised · keeping
   the screen on"), sensitivity choice (Relaxed/Normal/Strict angles), explanation. Requirement
   for overlay permission (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with package uri).
6. Integration note to report: `featureRoute(Feature.KEEP_SCREEN_ON)` should become `"screen-on"`.

### W3 — Polish (Phase 5) + the leftover typing bug · module `app` (+ vault item screen)

Owns: `app/src/main/java/com/akib/mycompanion/presentation/**` except `home/HomeScreen.kt` &
`home/HomeViewModel.kt` (you may edit those two too), `app/src/main/java/com/akib/mycompanion/system/**`,
`app/src/main/java/com/akib/mycompanion/widget/**` (new), `app/src/main/java/com/akib/mycompanion/export/**`
(new), `app/src/main/java/com/akib/mycompanion/di/PolishModule.kt` (new),
`app/src/main/res/**`, `app/src/main/AndroidManifest.xml`,
`feature-vault/src/main/java/com/akib/mycompanion/vault/ui/VaultItemScreen.kt` and
`VaultViewModels.kt` (only to add the reminder button), app tests.

1. **Reminders on vault items (FR-2.16):** implement `ReminderSink` in the app
   (`AppReminderSink` using `ScheduleReminderUseCase` with `ReminderTime.AtTime` or an absolute
   time — add a small use-case method if needed) bound in `PolishModule`. Vault item screen: a
   "Remind me…" button → date + time pickers → `ReminderSink.remind(item.title, "From your vault", at)`.
   Don't put vault content in the reminder text beyond the title.
2. **Home-screen widget (Phase 5):** `QuickActionsWidget` (`AppWidgetProvider`, RemoteViews, no new
   library) with buttons: pause/resume all, voice mode on/off, capture panel (intent action
   `com.akib.mycompanion.action.CAPTURE` — W1's panel activity listens for it), open vault. Layout
   in `res/layout/widget_quick_actions.xml`, info in `res/xml/quick_actions_widget.xml`. Updates
   when settings change (from `CompanionApp` collect → `AppWidgetManager.updateAppWidget`).
3. **Export all data (NFR-11):** Home → "Export my data": writes a ZIP (SAF `CreateDocument`) with
   human-readable JSON per area: people, reminders, playlists, rules, places, auto-reply templates,
   settings. Use `org.json` (available at runtime; test with Robolectric). Vault excluded (it has
   its own encrypted backup) — say so on screen. Read data through existing repositories/DAOs
   (`ContactRepository`, `ReminderRepository`, `PlaylistRepository`, `ContextRepository`,
   `AutoReplyRepository`, `SettingsStore`).
4. **Settings backup:** in the same screen: export/import of `SettingsStore` + voice/auto-reply
   settings as JSON (import overwrites after a confirmation dialog).
5. **First-run guide:** shown once (flag in SharedPreferences): 4 pages — what the app does, the
   pause switch, permissions are per feature, nothing leaves the phone. "Open permissions" button
   at the end. Hosted in `MainActivity`'s compose tree before `CompanionRoot` (you may edit
   `MainActivity.kt`).
6. **Typing bug (device finding #5):** replace the `TextField`-via-StateFlow pattern in
   `RemindersScreen`/`RemindersViewModel` (title, message), `ContactEditorDialog`/`ContactsViewModel`
   (name, phone) and the Player link box with Compose `TextFieldState`
   (`androidx.compose.foundation.text.input.TextFieldState`, `rememberTextFieldState`,
   `OutlinedTextField(state = …)` from material3 if available in the BOM, otherwise
   `BasicTextField(state = …)` with `OutlinedTextFieldDefaults.DecorationBox`). Keep the
   ViewModels' public behaviour so existing tests still describe it (update tests where the API
   changes). The text must be owned by Compose state, not round-tripped through a StateFlow.
7. Screens module `PolishModule.kt`: route `export` ("Export & backup") + Home link.

## 5. Integration (done by the integrator after all workstreams)

1. Merge branches; resolve conflicts.
2. Add `featureRoute` changes, any new catalog entries or module dependencies listed in reports.
3. `./gradlew ktlintFormat ktlintCheck testDebugUnitTest lintDebug assembleDebug --max-workers=3`;
   fix failures; commit; update `Docs/decisions.md` and the README feature list.
