# Coroutines & Flow Cheatsheet

## Coroutine Basics

### Launching Coroutines
```kotlin
// Fire-and-forget (returns Job)
scope.launch { ... }

// Returns result (Deferred)
val result = scope.async { compute() }
val value = result.await()

// RunBlocking (blocking, for tests/main)
runBlocking { ... }
```

### Structured Concurrency
```kotlin
// Parent waits for children
scope.launch {
    launch { child1() }  // child
    launch { child2() }  // child
    // Parent suspends here until both complete
}

// Cancellation propagates downward
parentJob.cancel() // cancels all children
```

### CoroutineScope vs CoroutineContext
| Concept | Purpose |
|---------|---------|
| `CoroutineScope` | Entry point, holds Job, defines lifecycle |
| `CoroutineContext` | Elements: Job, Dispatcher, CoroutineName, SupervisorJob |
| `Job` | Handle to coroutine lifecycle (cancel, join) |
| `SupervisorJob` | Children failures don't cancel siblings |

### Dispatchers
```kotlin
Dispatchers.Main      // UI thread (Compose, View updates)
Dispatchers.IO        // Network, disk, database
Dispatchers.Default   // CPU-intensive (sorting, parsing)
Dispatchers.Unconfined // Runs on caller until first suspend
```

```kotlin
// Switch dispatcher
withContext(Dispatchers.IO) { ... }

// In ViewModel: use viewModelScope (auto-cancelled on clear)
viewModelScope.launch { ... }

// In Lifecycle: lifecycleScope
lifecycleScope.launchWhenStarted { ... }
```

## Flow Basics

### Cold vs Hot
| Type | Behavior |
|------|----------|
| **Cold Flow** | Starts fresh for each collector (`flow { }`) |
| **Hot Flow** | Shared, independent of collectors (`stateIn`, `shareIn`) |

### Creating Flows
```kotlin
// Simple flow builder
val flow = flow {
    emit(1)
    emit(2)
    delay(100)
    emit(3)
}

// From callback API
val flow = callbackFlow {
    val listener = object : SomeListener {
        override fun onEvent(data: Data) { trySend(data) }
    }
    awaitClose { removeListener(listener) }
}
```

### Terminal Operators (collect)
```kotlin
// Collect in coroutine
flow.collect { value -> ... }

// Collect in Compose (lifecycle-aware)
val state by flow.collectAsStateWithLifecycle(initialValue)

// Convert to StateFlow (hot)
val stateFlow = flow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = initial
)

// Convert to SharedFlow (hot, replay)
val sharedFlow = flow.shareIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    replay = 1
)
```

### Intermediate Operators
```kotlin
flow
    .filter { it > 0 }
    .map { it * 2 }
    .distinctUntilChanged()
    .onStart { emit(initial) }
    .catch { emit(ErrorState(it)) }
    .onCompletion { cleanup() }
    .debounce(300)          // Wait for pause
    .flatMapLatest { fetch(it) }  // Cancel previous
    .combine(otherFlow) { a, b -> Result(a, b) }
    .zip(otherFlow) { a, b -> Pair(a, b) }
```

### StateFlow & SharedFlow
```kotlin
// StateFlow: always has value, conflates
val uiState = MutableStateFlow(InitialState)
uiState.value = NewState  // Updates immediately

// SharedFlow: no initial value, replay configurable
val events = MutableSharedFlow<Event>(replay = 0)
events.emit(Event.Clicked)
```

## Common Patterns in Android

### ViewModel with Flow
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repo: MyRepository
) : ViewModel() {

    // Expose immutable StateFlow to UI
    val uiState: StateFlow<UiState> = repo.getData()
        .map { UiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading
        )

    // One-shot events (navigation, toast)
    private val _events = MutableSharedFlow<Event>()
    val events = _events

    fun onAction() {
        viewModelScope.launch {
            repo.doSomething()
            _events.emit(Event.Done)
        }
    }
}
```

### Retry with Exponential Backoff
```kotlin
flow.retryWhen { cause, attempt ->
    if (attempt < 3 && cause is IOException) {
        delay(1000 * (2 ^ attempt))
        true
    } else false
}
```

### Testing Flows (Turbine)
```kotlin
// testImplementation "app.cash.turbine:turbine:1.0.0"
flow.test {
    assertEquals(expected, awaitItem())
    awaitItem { it == condition }
    awaitCompletion()
}
```

## Cancellation & Cleanup
```kotlin
// Ensure cleanup runs
flow
    .onCompletion { releaseResources() }
    .catch { handleError(it) }
    .collect { ... }

// Non-cancellable block
withContext(NonCancellable) {
    saveCriticalData()
}
```

## Key Rules to Remember
1. **Never block** - use `suspend` functions, not `.blockingGet()`
2. **Structured concurrency** - always launch in a scope tied to lifecycle
3. **Flow is cold by default** - use `stateIn`/`shareIn` for UI state
4. **Collect on Main** - UI updates must be on Main dispatcher
5. **Cancel on destroy** - `viewModelScope`, `lifecycleScope` handle this
6. **Exception handling** - `.catch { }` upstream, try/catch in collect