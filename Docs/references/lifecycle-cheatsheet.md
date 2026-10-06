# Android Lifecycle Cheatsheet

## Activity Lifecycle

```
onCreate() → onStart() → onResume() → [App in foreground]
                    ↑           ↓
               onRestart()   onPause()
                    ↑           ↓
               onStop() ← onStop()
                    ↓
             onDestroy()
```

| Callback | When | Typical Use |
|----------|------|-------------|
| `onCreate()` | First creation | Set content view, init ViewModel, bind views |
| `onStart()` | Visible to user | Register observers, start animations |
| `onResume()` | Foreground, interactive | Resume camera, sensors, exclusive resources |
| `onPause()` | Partially obscured | Pause animations, save draft data (quick) |
| `onStop()` | Fully hidden | Release heavy resources, save persistent state |
| `onDestroy()` | Finishing/config change | Cleanup, unregister listeners |
| `onRestart()` | After stop, before start | Re-initialize if needed |

### Configuration Changes
- Rotation, language, theme, font size → **Activity recreated**
- ViewModel survives (scoped to Activity lifecycle)
- Use `rememberSaveable` / `onSaveInstanceState` for Compose/View state

---

## Fragment Lifecycle

```
onAttach() → onCreate() → onCreateView() → onViewCreated()
                  ↓              ↓               ↓
             onStart()      onStart()        onResume()
                  ↓              ↓               ↓
             onPause()      onPause()       onStop()
                  ↓              ↓               ↓
           onDestroyView() onDestroyView() onDestroy()
                  ↓              ↓               ↓
            onDetach()       onDetach()      (detached)
```

| Callback | Purpose |
|----------|---------|
| `onAttach()` | Context available, get parent activity |
| `onCreate()` | Init non-UI (ViewModel, args) |
| `onCreateView()` | Inflate layout (return View) |
| `onViewCreated()` | ViewBinding, setup adapters, observers |
| `onDestroyView()` | **Critical**: Clear ViewBinding, observers! |
| `onDetach()` | Clean up activity references |

### Fragment + ViewModel
```kotlin
// Activity-scoped (shared with activity)
val vm = ViewModelProvider(requireActivity())[MyViewModel::class.java]

// Fragment-scoped (default)
val vm = ViewModelProvider(this)[MyViewModel::class.java]

// Navigation graph-scoped
val vm = ViewModelProvider(navBackStackEntry)[MyViewModel::class.java]
```

---

## Compose Lifecycle

### Composition vs Recomposition
| Phase | What Happens |
|-------|--------------|
| **Initial Composition** | Run composables, build UI tree |
| **Recomposition** | Re-run composables when `State` changes |
| **Disposal** | Leave composition, run `DisposableEffect` cleanup |

### Side Effects (Lifecycle-Aware)
```kotlin
// Run on composition/disposal (like useEffect)
DisposableEffect(key1, key2) {
    onDispose { cleanup() }  // Called when leaving composition or keys change
}

// Run when keys change (like useEffect with deps)
LaunchedEffect(key1, key2) {
    // Coroutine scope tied to composition
    // Auto-cancelled when keys change or leave composition
}

// Remember with lifecycle awareness
val state = rememberSaveable { mutableStateOf(initial) }

// Observe Lifecycle.State
val lifecycle = LocalLifecycleOwner.current.lifecycle
LaunchedEffect(lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        // Only runs when STARTED or RESUMED
        flow.collect { ... }
    }
}
```

### ViewModel in Compose
```kotlin
// Hilt
@Composable
fun Screen(vm: MyViewModel = hiltViewModel()) { ... }

// Koin
@Composable
fun Screen(vm: MyViewModel = viewModel()) { ... }

// Manual
@Composable
fun Screen(vm: MyViewModel = viewModel(factory = MyFactory())) { ... }
```

---

## Service Lifecycle

### Started Service
```
onCreate() → onStartCommand() → [Running] → onDestroy()
```
- `START_STICKY` - Recreate if killed
- `START_NOT_STICKY` - Don't recreate
- `START_REDELIVER_INTENT` - Recreate + redeliver last intent

### Foreground Service (Android 14+)
```xml
<!-- Manifest -->
<service
    android:name=".PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
```
```kotlin
// In Service
val notification = buildMediaNotification()
startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

// Stop
stopForeground(true)  // Remove notification
stopSelf()
```

### Bound Service
```
onCreate() → onBind() → [Clients bound] → onUnbind() → onDestroy()
```
- Returns `IBinder` for IPC
- Dies when all clients unbind (unless also started)

---

## WorkManager Lifecycle

```
ENQUEUED → RUNNING → SUCCEEDED/FAILED
                ↓
           (constraints met)
                ↓
           (retry/backoff if failed)
```

| Constraint | Behavior |
|------------|----------|
| `NetworkType.CONNECTED` | Waits for network |
| `BatteryNotLow` | Waits for battery OK |
| `StorageNotLow` | Waits for storage |
| `DeviceIdle` | Waits for doze/maintenance window |

```kotlin
val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "sync", ExistingPeriodicWorkPolicy.KEEP, request
)
```

---

## Process Lifecycle & Death

### When Process Dies
| Scenario | What Survives |
|----------|---------------|
| User swipes away (recent) | Nothing (process killed) |
| System low memory | Nothing (process killed) |
| `finish()` / `finishAffinity()` | Nothing |
| Config change | ViewModel, onSaveInstanceState |
| `onStop()` → background | Process *may* be cached |

### Surviving Process Death
| Data | Mechanism |
|------|-----------|
| UI state (scroll, text) | `rememberSaveable` / `onSaveInstanceState` |
| Business state | ViewModel (survives config change only) |
| Persistent data | Room, DataStore, Preferences |
| Work requests | WorkManager (survives reboot) |
| Alarms | AlarmManager (survives reboot with `setExactAndAllowWhileIdle`) |

---

## Lifecycle-Aware Components

### LifecycleObserver
```kotlin
class MyObserver : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() { ... }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() { ... }
}

// Register
lifecycle.addObserver(MyObserver())
```

### repeatOnLifecycle (Preferred for Flows)
```kotlin
lifecycleScope.launch {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collect { ... }  // Auto-cancel when STOPPED
    }
}
```

### ProcessLifecycleOwner (App-level)
```kotlin
ProcessLifecycleOwner.get().lifecycle
    .addObserver(AppLifecycleObserver())
// ON_START = app enters foreground
// ON_STOP = app enters background
```

---

## Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Leaking Context in static fields | Use `WeakReference` or Application context |
| Updating UI after `onStop()` | Use `lifecycleScope.launchWhenStarted` / `repeatOnLifecycle` |
| Fragment ViewBinding leak | Set `_binding = null` in `onDestroyView()` |
| Starting foreground service from background | Use `ContextCompat.startForegroundService()` + permission |
| WorkManager not running | Check constraints, battery optimization, `adb shell dumpsys jobscheduler` |

---

## Quick Reference: Which Scope to Use?

| Situation | Scope |
|-----------|-------|
| ViewModel | `viewModelScope` |
| Activity/Fragment (UI) | `lifecycleScope` |
| Compose composition | `rememberCoroutineScope()` / `LaunchedEffect` |
| Service | `CoroutineScope(SupervisorJob() + Dispatchers.IO)` |
| WorkManager Worker | `coroutineScope` (provided by `CoroutineWorker`) |
| Application class | Custom `SupervisorJob()` + `Dispatchers.Default` |