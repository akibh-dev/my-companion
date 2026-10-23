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
| [Docs/00-index.md](Docs/00-index.md) | Documentation index and templates |
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
│   ├── presentation/       # ViewModels, Compose UI, Services, Receivers
│   └── ui/theme/           # Material 3 theme
├── src/test/               # Unit tests (JUnit, MockK, Turbine)
└── src/androidTest/        # Instrumented tests (Compose Testing)
```

## 🎯 Current Status

- [x] Project setup with Gradle Kotlin DSL + Version Catalog
- [x] Hilt DI configured
- [x] Compose + Material 3 theme
- [x] Basic Home screen with ViewModel
- [x] Notification domain + data layer (skeleton)
- [x] Documentation structure
- [x] Contacts tab with one-tap WhatsApp call icon ([details](Docs/03-whatsapp-call-and-reminders.md))
- [x] Custom + scheduled notifications with snooze action

**Next**: Persist reminders across reboot, then YouTube background playback

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