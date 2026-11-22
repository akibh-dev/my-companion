# AGENTS.md - my-companion Android App

## Project Overview
Personal Android app (Kotlin) for one phone, the **Nothing CMF Phone 1 (Android 16)**. On-device only,
not for the Play Store. It brings back features usually locked behind paid apps:
- **People**: WhatsApp voice/video call handoffs, chat fallback, home-screen call shortcuts
- **Reminders**: custom notifications with repeats, sounds, snooze, per-reminder pause
- **Player**: YouTube background audio (NewPipeExtractor + Media3) with queue, playlists, history
- **Context rules**: sound mode, DND, volume, brightness by time, place, charging, Bluetooth, Wi-Fi, app in use
- **Auto-reply & missed-call SMS**, **voice mode** (reads any app's notifications aloud, with muted apps and block rules; Banglish → Bangla offline),
  keep screen on outdoors, quick-actions widget, pause tile, data export/backup

## Development Stack
- **Language**: Kotlin 2.2 (AGP 8.13, Gradle Kotlin DSL + version catalog `gradle/libs.versions.toml`)
- **SDK**: `compileSdk`/`minSdk`/`targetSdk` = **36**. One phone, so no code for older Android versions.
- **ABI**: `arm64-v8a` only; for an emulator build with `-Pabis=x86_64`
- **Architecture**: MVVM + Clean Architecture, multi-module
- **DI**: Hilt (kapt)
- **Async**: Coroutines + Flow (`StateFlow`, `WhileSubscribed(5000)`, `collectAsStateWithLifecycle`)
- **UI**: Jetpack Compose + Material 3, approved monochrome "dotted" design (`Docs/design/`)
- **Storage**: Room, DataStore
- **Media**: Media3 (ExoPlayer + MediaSession), NewPipeExtractor (JitPack)
- **Maps**: Google Maps (maps-compose) + Places API for the place editor; key `MAPS_API_KEY` in `local.properties` (`Docs/20-google-maps.md`)
- **AI**: ONNX Runtime, IndicXlit model in `ai-models/src/main/assets/indicxlit/` (fetch with `tools/banglish/fetch_models.py`)
- **JDK**: Gradle runs on JDK 17 or 21 (JDK 25 fails); unit tests run on JDK 21 (auto-downloaded toolchain)

## Modules
| Module | What it holds |
|---|---|
| `app` | Shell, navigation, People, Reminders, Player, export, widget, tiles, receivers |
| `core` | Log, heartbeat, settings (DataStore), `ServiceHost` foreground service, reconciler, field cipher |
| `ai-api` | AI engine interfaces, routing, fallbacks, model loader |
| `ai-models` | IndicXlit transliterator, Banglish lexicon, dummy models for the other slots |
| `adapters-messaging` | Notification listener, per-chat-app adapters, generic `NoticeParser` for any app |
| `feature-context` | Context rules, places (Google Maps picker + Places search), keep-screen-on-outdoors |
| `feature-autoreply` | Auto-reply and missed-call SMS |
| `feature-voice` | Voice mode (reads notifications aloud: muted apps, block rules) and its tile |

Package root: `dev.akibh.mycompanion` (each module adds its own sub-package, e.g. `.context`, `.voice`).

## Agent Guidelines

### Code Style
- Follow Kotlin official coding conventions
- ktlint with Android style (`.editorconfig`); run `ktlintFormat` before committing
- Prefer sealed classes for state modeling
- Coroutines for async (no callbacks); Flow for reactive streams
- Rethrow `CancellationException`; don't swallow it in `runCatching`

### Architecture Rules
- **Domain layer**: Pure Kotlin, no Android dependencies
- **Data layer**: Repository implementations, data sources
- **Presentation layer**: ViewModels, Compose UI
- Dependencies point inward (Presentation → Domain ← Data)
- Fragile or optional parts live in their own module; models sit behind `ai-api` interfaces

### Testing
- Unit tests for Domain (JUnit + MockK + Truth + Turbine)
- Robolectric for Room, alarms, notifications, receivers and Compose UI (in `src/test`)
- Instrumented tests in `app/src/androidTest` for the real phone
- Every bug fix comes with a regression test

### Git Workflow
- Feature branches from `main`; one PR per change, merged on GitHub (`akibh-dev/my-companion`)
- Conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `build:`, `ci:`, `test:`
- CI (`.github/workflows/android.yml`) must be green: ktlint, unit tests, lint, debug APK
- **All commits must be in the owner's name only** (`akib35`, the repo's configured `git config user.name` / `user.email`):
  - Never override the author/committer (no `--author`, no `GIT_AUTHOR_*` / `GIT_COMMITTER_*` env vars)
  - No `Co-Authored-By:` trailers or any AI/tool attribution in commit messages
  - No "Generated with ..." attribution lines in PR descriptions

### Learning Focus
This project is also a learning vehicle. Document decisions in `Docs/`:
- Why libraries/patterns were chosen (`Docs/decisions.md`, `Docs/architecture/`)
- Android concepts learned (`Docs/learning/`)
- Plans and reports are numbered (`Docs/NN-*.md`); release notes in `Docs/releases/`

## Current Work
- **Plan 19** (`Docs/19-audit-and-hardening-plan.md`): audit done, fixes not started. Order:
  correctness → crash visibility → player access control → performance → release hardening.
- **Plan 18** (`Docs/18-baseline-profile-plan.md`): baseline profile, last.

## Commands
```bash
# Models (once, before the first build)
python3 tools/banglish/fetch_models.py

# Full check (same as CI); use --max-workers=3 on machines with <= 16 GB RAM
./gradlew ktlintFormat ktlintCheck testDebugUnitTest lintDebug assembleDebug --continue --max-workers=3

# Build / install on the connected phone
./gradlew assembleDebug
./gradlew installDebug

# Single module tests
./gradlew :feature-context:testDebugUnitTest

# Instrumented tests on the phone
./gradlew connectedAndroidTest
```

`adb` lives at `~/Android/Sdk/platform-tools/adb` (not on PATH by default). The phone usually
connects over wireless debugging.
