# 08 — Read this repository

[Previous](07-android-compose.md) · [Index](../README.md) · [Next](09-testing-debugging.md)

The source links below are relative to this chapter. Read one trace at a time rather than trying to understand every module before making progress.

## Modules and layers

| Module | Responsibility |
|---|---|
| app | App entry points, People/Reminders/Player, navigation, wiring, export, widgets |
| core | Shared settings, contracts, host service, logging, utilities |
| feature-context | Rules, places, foreground-app detection, keep-screen-on |
| adapters-messaging | Translate notification information into shared messaging contracts |
| feature-autoreply | Reply decisions and missed-call SMS behavior |
| feature-voice | Reading messages aloud and audio coordination |
| ai-api | Model-facing contracts |
| ai-models | Placeholder implementations behind those contracts |

Gradle modules and architectural layers are different dimensions: app contains several layers, while feature-context also contains its own domain/data/device/UI divisions. Domain code should express rules without Android framework dependencies. Presentation depends on domain contracts; data/device implementations satisfy those contracts. Dependency direction is about imports, not the direction information travels at runtime.

## Trace a reminder

1. Read [Reminder.kt](../../../app/src/main/java/com/akib/mycompanion/domain/model/Reminder.kt). Identify values versus behavior, nullable fields, defaults, and recurrence alternatives.
2. Read [ReminderUseCases.kt](../../../app/src/main/java/com/akib/mycompanion/domain/usecase/ReminderUseCases.kt). Start with ScheduleResult and ScheduleReminderUseCase.invoke. Translate the sealed result cases into plain English.
3. Follow [ReminderRepository](../../../app/src/main/java/com/akib/mycompanion/domain/repository/ReminderRepository.kt). Which functions return a value, which suspend, and which expose changing data?
4. Follow [ReminderTimeCalculator](../../../app/src/main/java/com/akib/mycompanion/domain/time/ReminderTimeCalculator.kt). Why inject a clock instead of reading system time in every calculation?
5. Read [ReminderReceiver](../../../app/src/main/java/com/akib/mycompanion/system/ReminderReceiver.kt). It is an Android boundary; domain scheduling decisions do not belong inside a composable.
6. Return to FireReminderUseCase: find notification posting, next recurrence, and one-off delivery marking.

Write the trace as user input → validation → save → schedule → Android delivery → next state. Note failure boundaries. A successful database save does not by itself prove that an alarm or notification was allowed.

Exercise: locate the tests for an empty title/message and a past timestamp. Explain how their expected results differ from a notification permission failure. Use search instead of guessing:

```bash
rg -n 'EmptyContent|InvalidTime|NotificationsBlocked' app/src/test app/src/main
rg -n 'class .*Reminder|AlarmScheduler' app/src/main/java
```

## Trace a setting from storage to screen

Read [Settings.kt](../../../core/src/main/java/com/akib/mycompanion/core/settings/Settings.kt), then [HomeViewModel](../../../app/src/main/java/com/akib/mycompanion/presentation/home/HomeViewModel.kt).

Settings is a value model. SettingsStore is a contract. DataStoreSettingsStore persists preferences and exposes a Flow. The ViewModel combines settings with service/heartbeat/permission information into HomeUiState, then shares it through stateIn. A feature-toggle event launches a store write. The next stored value travels back through the flow to update UI.

Explain why both a feature's enabled flag and the global paused flag affect Settings.isActive. Find the enum parser that ignores removed CAPTURE settings rather than crashing when reading an old backup. This is compatibility handling, not a reason to restore the deleted feature.

## Trace a context rule

Read [Rules.kt](../../../feature-context/src/main/java/com/akib/mycompanion/context/domain/Rules.kt). Triggers include charging, time, Wi-Fi, Bluetooth, place, and foreground app. Actions carry desired device changes. Then inspect [RulesEngine.kt](../../../feature-context/src/main/java/com/akib/mycompanion/context/device/RulesEngine.kt), [DeviceSettings.kt](../../../feature-context/src/main/java/com/akib/mycompanion/context/device/DeviceSettings.kt), and [ContextSensors.kt](../../../feature-context/src/main/java/com/akib/mycompanion/context/device/ContextSensors.kt).

Separate observation, pure decisions, and effects. Rules can conflict, phone values can be quantized, and users can manually override a setting. Restoring a previous value therefore needs an ownership policy, not simply “set everything back.” Read the existing tests before changing that policy. Earlier uncommitted phone fixes were discarded at the owner's request; do not assume those fixes are present in main.

## Persistence and injection

Room maps entities/DAOs to a SQLite database. A repository can convert stored shapes into domain shapes. A Flow from a DAO lets subscribers observe relevant query updates. Schema changes require migration decisions; do not “fix” a failed migration by deleting user data during a lesson.

DataStore suits small settings and transactional updates; it does not replace a relational database. Export/restore also needs a schema and compatibility policy. Files and preferences belong to the app sandbox; access to another app's internal files is not implied by knowing its package name.

Hilt constructs an object graph. Constructor @Inject declares dependencies; modules bind interfaces/provide construction; scopes control reuse within components. Read [AppModule.kt](../../../app/src/main/java/com/akib/mycompanion/di/AppModule.kt) and [ContextModule.kt](../../../feature-context/src/main/java/com/akib/mycompanion/context/di/ContextModule.kt). A Singleton is graph-scoped reuse, not an automatic synchronization guarantee.

## Android integration map

| Concept | Read or search | Learning question |
|---|---|---|
| Runtime/special access | core permissions, app PermissionsScreen | Was access requested, granted, and still effective? |
| Foreground service | ServiceHost and player service | Who starts it, what type/notification does it need, when does it stop? |
| Notifications | ReminderReceiver, notifier implementations | Which channel and action PendingIntents are used? |
| Media3 | Player sources under app | How do player state, MediaSession, audio focus, and queue relate? |
| WorkManager | Heartbeat/worker classes | What work is deferrable rather than an exact timer? |
| Notification listener | adapters-messaging | What can be observed, and which reply action is actually available? |
| Widget/tile | QuickActionsWidget, PauseTileService | How does an external action reach shared settings? |
| Overlay/sensors | feature-context/screenon | Who owns registration and window cleanup? |

Modern Android restricts background execution and sensitive access. An implementation compiling successfully does not guarantee a particular device permits every operation. Check the exact official API page before changing permissions/services. Practice with synthetic notifications and previews; real messages/calls require the owner's deliberate test setup.

## Checkpoint

Without reading the code, explain one full event round trip, which interface makes it testable, where a failure can occur, and who owns the coroutine. If you cannot, repeat one trace rather than adding another abstraction.

Reference: [Android architecture guidance](https://developer.android.com/topic/architecture).
