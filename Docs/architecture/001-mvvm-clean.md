# ADR 001: MVVM + Clean Architecture

## Status
Accepted

## Context
Need a maintainable, testable architecture for a personal Android app that will grow over time. The app will have:
- Multiple features (notifications, media playback, settings)
- Complex background operations (foreground services, WorkManager)
- Need for unit testing business logic without Android dependencies
- Learning goal: understand industry-standard patterns

## Decision
Adopt **MVVM (Model-View-ViewModel)** with **Clean Architecture** layering:

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  Compose UI / ViewModels / State / Navigation               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ (depends on interfaces)
┌─────────────────────────────────────────────────────────────┐
│                       DOMAIN LAYER                          │
│  Entities / Repository Interfaces / Use Cases (Pure Kotlin) │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │ (implements interfaces)
┌─────────────────────────────────────────────────────────────┐
│                        DATA LAYER                           │
│  Repository Impl / Room / DataStore / Retrofit / Media3     │
└─────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Contains | Rules |
|-------|----------|-------|
| **Domain** | `Entity`, `Repository` (interface), `UseCase` | **Zero Android deps**, pure Kotlin, unit-testable |
| **Data** | `RepositoryImpl`, `DataSource` (Room/Retrofit/DataStore), `Mapper` | Implements Domain interfaces, knows Android/framework |
| **Presentation** | `ViewModel`, `UiState`, `Compose Screen`, `Navigation` | Observes Domain via UseCases, handles UI logic only |

### Dependency Rule
> **Dependencies point inward**. Presentation → Domain ← Data
> Domain knows nothing about outer layers.

## Consequences

### Positive
- **Testability**: Domain layer unit tests run on JVM (fast, no emulator)
- **Swapability**: Can replace Room → DataStore, Retrofit → Mock, without touching ViewModels
- **Separation of Concerns**: Business logic isolated from UI and framework
- **Learning Value**: Industry standard, transfers to any Android job
- **Scalability**: Adding features follows clear pattern

### Negative
- **Boilerplate**: More files/classes for simple features
- **Learning Curve**: Requires understanding DI, interfaces, coroutines
- **Over-engineering Risk**: For very simple features, may feel heavy

### Neutral
- Need to decide: Use Cases per action vs. Repository methods directly in ViewModel
- Need to decide: Mapper location (Data vs Domain vs Presentation)

## Alternatives Considered

### 1. MVC (Activity/Fragment as Controller)
- **Rejected**: Hard to test, lifecycle issues, tight coupling

### 2. MVP (Presenter + View Interface)
- **Rejected**: Boilerplate interfaces, ViewModel lifecycle handling is superior

### 3. MVI (Unidirectional Data Flow)
- **Considered**: Great for complex state, but steeper learning curve
- **Decision**: MVVM with StateFlow achieves similar unidirectional flow with less ceremony

### 4. Clean Architecture without MVVM (just UseCases + Compose)
- **Considered**: Possible, but ViewModel provides lifecycle-surviving state holder
- **Decision**: Keep ViewModel for configuration change survival + `viewModelScope`

## Implementation Notes

### Domain Layer Structure
```
domain/
├── model/
│   ├── NotificationSettings.kt
│   └── PlaybackQueue.kt
├── repository/
│   ├── NotificationRepository.kt      // interface
│   └── MediaRepository.kt             // interface
└── usecase/
    ├── notification/
    │   ├── CreateNotificationUseCase.kt
    │   └── ScheduleNotificationUseCase.kt
    └── media/
        ├── GetPlaybackStateUseCase.kt
        └── PlayMediaUseCase.kt
```

### Use Case Pattern
```kotlin
// Single responsibility, suspend function
class PlayMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(mediaId: String): Result<PlaybackState> {
        // Business logic: validation, transformation, orchestration
        return mediaRepository.play(mediaId)
    }
}
```

### Repository Interface (Domain)
```kotlin
interface MediaRepository {
    fun play(mediaId: String): Result<PlaybackState>
    val playbackState: StateFlow<PlaybackState>
    suspend fun search(query: String): Result<List<MediaItem>>
}
```

### Repository Implementation (Data)
```kotlin
class MediaRepositoryImpl @Inject constructor(
    private val localDataSource: MediaLocalDataSource,
    private val remoteDataSource: MediaRemoteDataSource,
    private val mediaPlayer: MediaPlayerController  // Media3 wrapper
) : MediaRepository {
    override suspend fun play(mediaId: String): Result<PlaybackState> {
        // Coordinate data sources, handle errors, map to domain
    }
}
```

### ViewModel (Presentation)
```kotlin
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val playMediaUseCase: PlayMediaUseCase,
    private val getPlaybackStateUseCase: GetPlaybackStateUseCase
) : ViewModel() {

    val uiState: StateFlow<MediaUiState> = getPlaybackStateUseCase()
        .map { MediaUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaUiState.Loading)

    fun onPlayClick(mediaId: String) {
        viewModelScope.launch {
            val result = playMediaUseCase(mediaId)
            if (result.isFailure) {
                _events.emit(MediaEvent.Error(result.exceptionOrNull()!!))
            }
        }
    }
}
```

## References
- [Google Guide to App Architecture](https://developer.android.com/topic/architecture)
- [Clean Architecture - Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Android Architecture Components](https://developer.android.com/jetpack/guide)