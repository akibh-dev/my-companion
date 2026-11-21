# my-companion

[![Android CI](https://github.com/akibh-dev/my-companion/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/akibh-dev/my-companion/actions/workflows/android.yml)

Android app for reviving features on my own terms. NO CAP.

Personal Android app (Kotlin) for the Nothing CMF Phone 1 (Android 16), on-device only:
- **People**: WhatsApp voice/video call handoffs with permission recovery and chat fallback, home-screen call shortcuts
- **Reminders**: custom notifications now, later or at a time; repeat daily, on chosen days, monthly or every few hours; custom sounds; snooze / done / call buttons; per-reminder pause; survive reboot
- **Player**: YouTube background audio with notification and lock-screen controls, queue, playlists and listening history
- **Context rules**: sound mode, Do Not Disturb, volume, brightness (and Wi-Fi/Bluetooth prompts) by time, place, charging, Bluetooth, Wi-Fi or app in use; places picked on a Google Map or found by search ([setup](Docs/20-google-maps.md))
- **Auto-reply & missed-call SMS**: replies through WhatsApp, Messenger, Signal, Telegram and Messages notifications
- **Voice mode**: reads notifications from any app aloud on the media volume, with muted apps and block rules (by title or text); Banglish is converted to Bangla offline (lexicon + IndicXlit)
- **Quran** (Arabic only): Madani pages in the exact King Fahd page fonts and Indo-Pak 15-line pages, an ayah list, search with or without harakat, go to surah/ayah/page/juz, bookmarks, a khatm goal, a daily reminder, a widget and a tile
- **Library**: add PDF, EPUB and CBZ files from Downloads (linked, not copied), read them with your place remembered, search inside, bookmarks, shelves and tags, reading time with a daily goal and streak, a widget
- **Keep screen on outdoors**, quick-actions widget, pause tile, data export and settings backup

Built as a **learning vehicle** for Android development. Current version: **0.3.1**.

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [AGENTS.md](AGENTS.md) | Agent guidelines, stack, modules, commands |
| [Docs/learning/README.md](Docs/learning/README.md) | **Start here**: step-by-step learning path |
| [Docs/00-index.md](Docs/00-index.md) | Documentation index |
| [Docs/decisions.md](Docs/decisions.md) | Every decision so far |
| [Docs/implementation-guide.md](Docs/implementation-guide.md) | Conventions, pitfalls, where to add code |
| [Docs/19-audit-and-hardening-plan.md](Docs/19-audit-and-hardening-plan.md) | Current work: audit findings and fix order |
| [Docs/18-baseline-profile-plan.md](Docs/18-baseline-profile-plan.md) | Baseline profile (last step) |
| [Docs/03-whatsapp-call-and-reminders.md](Docs/03-whatsapp-call-and-reminders.md) | How calls and reminders work |
| [Docs/05-youtube-player.md](Docs/05-youtube-player.md) | YouTube background player, queue, playlists |
| [Docs/12-production-voice.md](Docs/12-production-voice.md) | Voice reader setup and limitations |
| [Docs/04-testing.md](Docs/04-testing.md) | Test layers, tools and patterns |
| [Docs/releases/](Docs/releases/) | Release notes |
| [Docs/architecture/](Docs/architecture/) | Architecture Decision Records (ADRs) |
| [tools/](tools/) | Model fetcher, device-test helpers, reminder sound generator |

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
│  Media3 │ NewPipe │ WorkManager │ ONNX  │
└─────────────────────────────────────────┘
```

**Stack**: Kotlin 2.2, Jetpack Compose + Material 3, Hilt, Room, DataStore, Media3 (ExoPlayer), NewPipeExtractor, ONNX Runtime, Coroutines/Flow, WorkManager

### Modules

| Module | What it holds |
|---|---|
| `app` | Shell, navigation, People, Reminders, Player, export, widget, tiles |
| `core` | Log, heartbeat, settings, foreground `ServiceHost`, reconciler |
| `ai-api` / `ai-models` | AI interfaces; IndicXlit transliterator, Banglish lexicon, placeholder models |
| `adapters-messaging` | Notification listener and chat-app adapters |
| `feature-context` | Context rules, places (Google Maps + search), keep screen on outdoors |
| `feature-autoreply` | Auto-reply and missed-call SMS |
| `feature-voice` | Voice mode |
| `feature-quran` | Quran reader: pages, ayah list, search, bookmarks, khatm goal |
| `feature-library` | Book library: linked PDF/EPUB/CBZ files, reader, shelves, reading stats |
| `design` | The monochrome dotted theme, `DotText` and `DotIcon`, shared by all screens |

## 🚀 Getting Started

### Prerequisites
- Android Studio (recent stable)
- JDK 17 or 21 for Gradle (JDK 25 doesn't work with Gradle 8.14); unit tests use JDK 21, which Gradle downloads automatically
- Android SDK 36 (Android 16)
- Python 3 (to fetch the transliteration models and build the Quran data)

### Build
```bash
git clone https://github.com/akibh-dev/my-companion.git
cd my-companion
python3 tools/banglish/fetch_models.py
pip install -r tools/quran/requirements.txt
python3 tools/quran/fetch_sources.py   # Quran text, layouts and fonts (~210 MB, pinned)
python3 tools/quran/build_quran_db.py  # builds feature-quran/src/main/assets/quran
./gradlew assembleDebug
```

The APK contains only `arm64-v8a` native libraries. For an x86_64 emulator, add `-Pabis=x86_64`.

### Install on your phone
With the phone connected (USB or wireless debugging):
```bash
./gradlew installDebug
```
Or download `my-companion-debug-apk` from the latest green **Android CI** run (Actions tab).

### Run Tests
```bash
# Full check, same as CI (use --max-workers=3 on <= 16 GB RAM)
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug --continue

# Instrumented tests (phone or emulator)
./gradlew connectedAndroidTest
```

## 🎯 Current Status

- [x] People, Reminders, Player, context rules, auto-reply, voice mode, keep screen on outdoors, widget, export
- [x] Monochrome dotted UI with smooth navigation (0.3.x)
- [x] Offline Banglish → Bangla for voice mode
- [x] Quran reader, Madani and Indo-Pak ([Plan 21](Docs/21-quran-plan.md))
- [x] Book library: PDF, EPUB and CBZ ([Plan 22](Docs/22-library-plan.md))
- [x] About 1,500 unit, Robolectric and Compose UI tests; CI on every push
- [ ] **Plan 19** hardening: correctness fixes, crash logging, player access control, performance, release signing + R8
- [ ] **Plan 18** baseline profile

Secure Vault and volume-button Quick Capture have been removed. See [decision D19](Docs/decisions.md#d19--remove-secure-vault-and-quick-capture-2026-10-30).

## 📖 Learning Resources

See [Docs/resources/official-docs.md](Docs/resources/official-docs.md) for curated links to Android fundamentals, Jetpack Compose, Media3, foreground services, WorkManager and testing.

---

*Personal project - not for Play Store distribution.*
