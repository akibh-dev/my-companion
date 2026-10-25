# my-companion

Android app for reviving features on my own terms. NO CAP.

Personal Android app (Kotlin) to bring back features often locked behind paid subscriptions:
- Custom notifications
- YouTube background playback
- Notification listener
- Quick settings tiles
- And more...

Built as a **learning vehicle** for Android development.

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [AGENTS.md](AGENTS.md) | Agent guidelines, code style, architecture rules |
| [plan/01-project-overview.md](plan/01-project-overview.md) | Project vision, features, timeline |
| [plan/02-tech-stack.md](plan/02-tech-stack.md) | Technical decisions with rationale |
| [plan/03-learning-roadmap.md](plan/03-learning-roadmap.md) | Phase-by-phase learning path |
| [Docs/learning/README.md](Docs/learning/README.md) | **Start here** — how to start + step-by-step learning path |
| [Docs/00-index.md](Docs/00-index.md) | Documentation index and templates |
| [Docs/03-whatsapp-call-and-reminders.md](Docs/03-whatsapp-call-and-reminders.md) | How calls and reminders work |
| [Docs/04-testing.md](Docs/04-testing.md) | Test layers, tools and patterns |
| [Docs/05-youtube-player.md](Docs/05-youtube-player.md) | YouTube background player, queue, playlists |
| [Docs/architecture/](Docs/architecture/) | Architecture Decision Records (ADRs) |
| [Docs/references/](Docs/references/) | Quick reference sheets (coroutines, lifecycle, Gradle) |
| [Docs/resources/](Docs/resources/) | Curated external learning resources |

## 🏗 Architecture

```
┌─────────────────────────────────────────┐
│           PRESENTATION (Compose)        │
│  ViewModels │ UI State │ Navigation     │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│              DOMAIN (Pure Kotlin)       │
│  Entities │ Repository Interfaces       │
│  Use Cases │ Business Logic             │
└──────────────────┬──────────────────────┘
                   ▲
┌─────────────────────────────────────────┐
│                 DATA                    │
│  Repository Impl │ Room │ DataStore     │
│  Retrofit │ Media3 │ WorkManager        │
└─────────────────────────────────────────┘
```

**Stack**: Kotlin, Jetpack Compose, Hilt, Room, Media3 (ExoPlayer), Coroutines/Flow, WorkManager

## 🚀 Getting Started

### Prerequisites
- Android Studio Koala (2024.1.1) or later
- JDK 17
- Android SDK 34

### Build
```bash
# Clone and open in Android Studio
git clone <repo-url>

# Or build from command line
./gradlew assembleDebug
```

### Run Tests
```bash
# Unit tests (JVM)
./gradlew test

# Instrumented tests (device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## 📁 Project Structure

```
app/
├── src/main/java/com/akib/mycompanion/
│   ├── di/                 # Hilt modules
│   ├── domain/             # Pure Kotlin: entities, repositories, usecases
│   ├── data/               # Repository impl, Room, DataStore, Media3
│   ├── presentation/       # ViewModels, Compose screens, call activity, shortcuts
│   ├── player/             # Media3 playback service and controller
│   ├── system/             # Broadcast receivers (alarms, boot)
│   └── ui/theme/           # Material 3 theme
└── src/test/               # JUnit, MockK, Turbine, Robolectric, Compose UI tests
```

## 🎯 Current Status

- [x] Gradle Kotlin DSL + version catalog, Hilt, Room, Compose + Material 3
- [x] **People**: one-tap WhatsApp voice calls, pick from phone book, edit/undo, home-screen call shortcuts
- [x] **Reminders**: custom notifications now / later / at a time, daily repeat, Snooze / Done / Call buttons, survive reboot
- [x] **Player**: share a YouTube video → play in the background (notification + lock-screen controls), queue, permanent playlists ([details](Docs/05-youtube-player.md))
- [x] Tests: domain, ViewModel, Robolectric (Room, alarms, notifications, receivers) and Compose UI ([Docs/04-testing.md](Docs/04-testing.md))
- [x] CI: ktlint, unit tests, Android lint, debug APK on every push

How it works: [Docs/03-whatsapp-call-and-reminders.md](Docs/03-whatsapp-call-and-reminders.md)

**Next**: notification listener, quick settings tile

### Install on your phone
Download `my-companion-debug-apk` from the latest green **Android CI** run (Actions tab), or run `./gradlew installDebug` with the phone connected.

## 📖 Learning Resources

See [Docs/resources/official-docs.md](Docs/resources/official-docs.md) for curated links to:
- Android fundamentals
- Jetpack Compose
- Media3/ExoPlayer
- Foreground Services
- WorkManager
- Testing

---

*Personal project - not for Play Store distribution.*