# Testing

All tests run on the JVM with `./gradlew testDebugUnitTest` (no emulator needed). CI runs them on every push, together with ktlint, Android lint and the debug APK build.

## Layers and tools

| Layer | Where | Tools | Examples |
|-------|-------|-------|----------|
| Domain rules & use cases | `test/.../domain` | JUnit, Truth, MockK, fakes | phone normalisation, number matching, reminder times (incl. DST), call fallback, snooze/fire/restore |
| ViewModels | `test/.../presentation/*ViewModelTest` | coroutines-test, Turbine, fakes | add/edit/undo, call messages, form state, scheduling messages |
| Data (Android) | `test/.../data` | Robolectric | Room in-memory DB, WhatsApp gateway with a fake Contacts provider, AlarmManager, NotificationManager |
| System | `test/.../system` | Robolectric + Hilt testing | alarm/boot receivers, the invisible call activity |
| UI | `test/.../presentation/*ScreenTest` | Compose UI test on Robolectric | buttons, chips, menus, dialogs, banner |
| Smoke | `MainActivityTest` | Hilt + Compose | real app graph starts, tabs switch |

## Patterns worth knowing

- **Fakes over mocks** for repositories, scheduler and notifier (`fakes/Fakes.kt`): they keep state, so tests read like stories. MockK is used where only an interaction matters (WhatsApp gateway, receivers).
- **`FakeClock`** fixes "now" (Fri 23 Oct 2026, 10:00 Dhaka) so time maths is deterministic.
- **`MainDispatcherRule`** swaps `Dispatchers.Main` so `viewModelScope` runs synchronously. ViewModels are created `by lazy` because rules run after field initialisers.
- **Hilt tests:** `@HiltAndroidTest` + `@Config(application = HiltTestApplication::class)`; `@UninstallModules` + `@BindValue` replace one binding with a mock.
- `robolectric.properties` uses a plain `Application` by default so non-Hilt tests don't build the app graph.

## Commands

```bash
./gradlew testDebugUnitTest            # all tests
./gradlew testDebugUnitTest --tests '*ReminderUseCasesTest'
./gradlew ktlintCheck                  # style (ktlintFormat to auto-fix)
./gradlew lintDebug                    # Android lint
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`.
