# ADR 002: Hilt vs Koin for Dependency Injection

## Status
Accepted (Hilt)

## Context
Need a DI framework for:
- ViewModel injection
- Repository/UseCase injection
- Service/Worker injection (Foreground Service, WorkManager)
- Compile-time safety
- Learning industry-standard tool

## Decision
**Hilt** (Dagger-based, Google-maintained)

## Consequences

### Positive (Hilt)
- **Compile-time validation**: Catches missing bindings at build time
- **Android integration**: Built-in support for ViewModel, WorkManager, Service, Application, Activity, Fragment
- **Standard in industry**: Used in most professional Android projects
- **Generated code readable**: Can inspect `Dagger*Component` classes
- **Qualifiers + Scopes**: `@Singleton`, `@ActivityScoped`, `@ViewModelScoped`, custom scopes
- **Entry Points**: For classes not supported by Hilt (e.g., ContentProvider, some Services)
- **Testing support**: `@HiltAndroidTest`, `@UninstallModules`, `HiltTestApplication`

### Negative (Hilt)
- **Learning curve**: Dagger concepts (Component, Module, Scope, Qualifier)
- **Build time**: Annotation processing (mitigated by KSP)
- **Magic feeling**: Harder to debug when things go wrong
- **Verbose**: More annotations than Koin

### Positive (Koin)
- **Pure Kotlin**: No annotation processing, faster builds
- **Simple DSL**: `module { single { Repo() } }`
- **Easy to learn**: Service locator pattern, readable
- **Great for small projects**: Less ceremony

### Negative (Koin)
- **Runtime resolution**: Errors caught at runtime, not compile time
- **No built-in Android component support**: Manual `get()` in ViewModel/Service
- **Service locator pattern**: Hides dependencies, harder to trace
- **Less common in enterprise**: Fewer job postings require Koin

## Alternatives Considered
| Option | Verdict |
|--------|---------|
| **Manual DI** | Too much boilerplate for growing app |
| **Koin** | Good for learning, but Hilt is industry standard |
| **Dagger (raw)** | Hilt is Dagger with Android integrations built-in |

## Implementation Notes

### Setup (build.gradle.kts)
```kotlin
plugins {
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)  // Hilt supports KSP since 2.48
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}
```

### Application Class
```kotlin
@HiltAndroidApp
class MyApplication : Application()
```

### Modules
```kotlin
// Domain module (interfaces bound to implementations)
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideNotificationRepository(
        impl: NotificationRepositoryImpl
    ): NotificationRepository = impl
}

// Data module (implementation details)
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideMediaLocalDataSource(db: AppDatabase): MediaLocalDataSource =
        MediaLocalDataSourceImpl(db.mediaDao())

    @Provides
    @Singleton
    fun provideMediaRemoteDataSource(api: YouTubeApi): MediaRemoteDataSource =
        MediaRemoteDataSourceImpl(api)
}
```

### ViewModel Injection
```kotlin
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val playMediaUseCase: PlayMediaUseCase
) : ViewModel() { ... }

// In Compose
@Composable
fun MediaScreen(vm: MediaViewModel = hiltViewModel()) { ... }
```

### WorkManager / Service Injection
```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: SyncRepository
) : CoroutineWorker(context, params) { ... }

// Foreground Service
@AndroidEntryPoint
class PlaybackService : MediaSessionService() { ... }
```

### Testing
```kotlin
@HiltAndroidTest
class MediaViewModelTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var playMediaUseCase: PlayMediaUseCase

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testPlay() = runTest {
        // Use fake/mock use case
    }
}
```

## References
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android)
- [Dagger Hilt vs Koin Comparison](https://proandroiddev.com/hilt-vs-koin-which-one-should-you-choose-2023-update)
- [Migrating from Dagger to Hilt](https://developer.android.com/training/dependency-injection/hilt-migration)