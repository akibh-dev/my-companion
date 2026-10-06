# Tech Stack Decisions

## Language & Build
| Choice | Rationale |
|--------|-----------|
| **Kotlin** | Modern, null-safe, coroutines, Google's preferred language |
| **Gradle Kotlin DSL** | Type-safe build scripts, better IDE support |
| **Min SDK 24** | Covers ~95% devices, supports modern APIs |
| **Target SDK 34** | Latest stable, required for Play Store (if ever) |

## Architecture
| Choice | Rationale |
|--------|-----------|
| **MVVM + Clean Architecture** | Separation of concerns, testable, scalable |
| **Repository Pattern** | Abstracts data sources, easy to swap/mock |
| **Use Cases / Interactors** | Single-responsibility business logic, reusable |
| **Domain = Pure Kotlin** | Unit testable without Android context, portable |

## UI Framework
| Choice | Rationale |
|--------|-----------|
| **Jetpack Compose** | Declarative, less boilerplate, modern, Kotlin-first, learning investment |
| **Material 3** | Latest design system, theming support |
| **ViewBinding (fallback)** | If Compose interop needed for legacy components |

## Dependency Injection
| Choice | Rationale |
|--------|-----------|
| **Hilt** | Google-backed, compile-time validation, integrates with ViewModel/WorkManager/Service, standard in industry |
| *Alternative: Koin* | Lighter, pure Kotlin, easier to learn - consider if Hilt feels too magical |

## Async & Reactive
| Choice | Rationale |
|--------|-----------|
| **Kotlin Coroutines** | Structured concurrency, cancellation, easy to read |
| **Flow** | Cold streams, operators, integrates with Compose `collectAsStateWithLifecycle()` |
| **StateFlow / SharedFlow** | Hot streams for UI state / events |

## Data & Persistence
| Choice | Use Case |
|--------|----------|
| **Room** | Structured local data (settings history, cached metadata) |
| **DataStore (Preferences)** | Simple key-value settings (booleans, strings) |
| **DataStore (Proto)** | If complex typed settings needed later |

## Networking
| Choice | Rationale |
|--------|-----------|
| **Retrofit + OkHttp** | Standard, type-safe, interceptors for auth/logging |
| **Moshi / kotlinx.serialization** | JSON parsing (Moshi: reflection-free, Kotlin-friendly) |

## Media & Background Playback
| Choice | Rationale |
|--------|-----------|
| **Media3 (ExoPlayer + MediaSession)** | Modern, maintained, handles foreground service, lock screen, Bluetooth, PiP |
| **Foreground Service (mediaPlayback type)** | Required for background audio on Android 14+ |
| **NewPipeExtractor / yt-dlp (via JNI or local)** | YouTube metadata extraction without official API |

## Notifications
| Choice | Rationale |
|--------|-----------|
| **NotificationCompat / NotificationManager** | Standard API |
| **MediaStyle Notification** | For playback controls on lock screen / notification shade |
| **NotificationChannel** | Required for Android 8+, user-controlled importance |

## Testing
| Layer | Tools |
|-------|-------|
| Unit (Domain) | JUnit 5, MockK, Turbine (Flow testing) |
| Unit (Data) | Room in-memory, MockWebServer (Retrofit) |
| UI (Compose) | Compose Testing, Robolectric |
| Instrumented | Espresso (if XML), Compose Test Rules |

## Code Quality
| Tool | Purpose |
|------|---------|
| **ktlint** | Code style (Android style guide) |
| **detekt** | Static analysis, code smells |
| **Android Lint** | SDK/API issues, performance, security |
| **GitHub Actions / Local** | CI pipeline |

## Libraries to Evaluate Later
- **Coil** - Image loading (if needed)
- **Accompanist** - Compose extras (permissions, pager, etc.)
- **KSP** - Faster annotation processing (Room, Hilt, Moshi)
- **Multiplatform** - If ever sharing domain with desktop/iOS

## Version Catalog (libs.versions.toml)
Use Gradle Version Catalog for centralized dependency management.