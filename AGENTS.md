# AGENTS.md - my-companion Android App

## Project Overview
Personal Android app (Kotlin) to revive features often locked behind paid subscriptions:
- Custom notifications
- YouTube background player
- Other personal utilities

## Development Stack
- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt / Koin
- **Async**: Coroutines + Flow
- **UI**: Jetpack Compose (preferred) or XML + ViewBinding

## Agent Guidelines

### Code Style
- Follow Kotlin official coding conventions
- Use ktlint with Android style guide
- Prefer sealed classes for state modeling
- Use coroutines for async operations (no callbacks)
- Use Flow/LiveData for reactive streams

### Architecture Rules
- **Domain layer**: Pure Kotlin, no Android dependencies
- **Data layer**: Repository implementations, data sources
- **Presentation layer**: ViewModels, Compose UI / Fragments
- Dependencies point inward (Presentation → Domain ← Data)

### Testing
- Unit tests for Domain layer (JUnit + MockK)
- Integration tests for Data layer (Room, Retrofit)
- UI tests for Compose (Compose Testing) or Espresso

### Git Workflow
- Feature branches from `main`
- Conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`
- PR required for main (even for personal project - builds habit)
- **All commits must be in the owner's name only** (`akib35`, the repo's configured `git config user.name` / `user.email`):
  - Never override the author/committer (no `--author`, no `GIT_AUTHOR_*` / `GIT_COMMITTER_*` env vars)
  - No `Co-Authored-By:` trailers or any AI/tool attribution in commit messages
  - No "Generated with ..." attribution lines in PR descriptions

### Learning Focus
This project is also a learning vehicle. Document decisions in `Docs/`:
- Why specific libraries/patterns were chosen
- Android concepts learned (lifecycle, permissions, services, etc.)
- Links to official docs and helpful articles

## Key Android Concepts to Master
- [ ] Activity/Fragment lifecycle
- [ ] Foreground Services (for background playback)
- [ ] Notifications (channels, actions, media style)
- [ ] MediaSession / Media3 (for YouTube background)
- [ ] Permissions (runtime, special: overlay, notification listener)
- [ ] WorkManager (periodic tasks)
- [ ] Room Database (local storage)
- [ ] DataStore (preferences)
- [ ] Dependency Injection (Hilt/Koin)

## Commands
```bash
# Build
./gradlew assembleDebug

# Test
./gradlew test

# Lint
./gradlew lint

# Format check
./gradlew ktlintCheck

# Auto-format
./gradlew ktlintFormat
```

## Project Structure (Planned)
```
app/
├── src/main/java/com/akib/mycompanion/
│   ├── di/                 # Hilt/Koin modules
│   ├── domain/             # Pure Kotlin: entities, repositories, usecases
│   ├── data/               # Repository impl, Room, Retrofit, DataStore
│   ├── presentation/       # ViewModels, UI (Compose/XML)
│   └── utils/              # Extensions, constants
├── src/test/               # Unit tests
└── src/androidTest/        # Instrumented tests
```