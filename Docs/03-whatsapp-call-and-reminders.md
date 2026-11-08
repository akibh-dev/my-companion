# WhatsApp calls and custom reminders

What the app does today, how each piece works, and the Android concepts behind it.

## What you can do

| Screen | Action | What happens |
|--------|--------|--------------|
| **People** | Tap the green call button | Hands off to a supported WhatsApp call action, or explains why chat is used |
| | ⋮ → Add to home screen | A home-screen icon using the saved person’s latest number and the same call recovery flow |
| | ⋮ → Edit / Remove | Remove shows a snackbar with **Undo** |
| | Add person → Choose from phone contacts | Name and number filled from the phone book |
| **Reminders** | Title/message + **Now** | Notification appears immediately |
| | 5 min … 1 h, or **Pick time** | Exact alarm at that time |
| | Pick time + **Repeat every day** | Same local time every day (survives DST) |
| | Add a call button | Notification gets a "Call Mum" button |
| | Notification buttons | **Snooze 10 min**, **Done**, optional **Call** |
| | Upcoming list ✕ | Cancels the alarm and deletes the reminder |

Reminders survive reboots and app updates (`BootReceiver` re-arms them; overdue one-offs are delivered and missed repeat occurrences are skipped).

## How the one-tap WhatsApp call works

Direct calling uses private contact actions exposed by the installed WhatsApp client. Those rows are not a public calling API; their availability and behavior depend on the client, contact sync and contacts access. Candidate MIME/account combinations must be confirmed on the target phone rather than inferred solely from an installed package name.

The resolver scopes lookup to the requested phone-book number, checks each call row's number and raw-contact account, and restricts launch to the selected package. Full-number comparison replaces the old unconditional ten-digit suffix fallback. Ambiguous candidates are refused. When both supported variants have distinct valid targets, the user chooses the app; the app does not silently switch accounts after a rejected launch.

People, shortcuts and reminder actions share permission recovery. Shortcut intents retain the app contact ID and existing token rather than storing a provider row ID; lookup happens again at each attempt so contact edits and provider sync are respected. Missing or permanently denied contacts permission uses a visible recovery host, while a normal supported shortcut handoff stays invisible.

A successful Android intent launch establishes **handoff**, not a ringing or connected call. Missing rows, denied lookup, ambiguous numbers, unavailable handlers and rejected launches have distinct fallback explanations. Chat launch failure is separate from WhatsApp not being installed. A saved My Companion contact does not create a phone-book contact or force WhatsApp synchronization.

See [Plan 14](14-whatsapp-direct-call-plan.md) for the compatibility and acceptance requirements. Real client behavior requires explicit testing on the phone; synthetic provider/fake-client tests only establish routing and recovery.

### Phone numbers

`PhoneNumbers.normalize` stores everything as E.164 (`+8801711123456`). Local numbers starting with `0` get `+880`; `00…` is treated as `+…`; spaces, dashes and brackets are ignored.

## How reminders work

```
ScheduleReminderUseCase ──save──▶ Room ──▶ AlarmScheduler (AlarmManager, exact, Doze-safe)
                                                   │ at trigger time
                                                   ▼
                                         ReminderReceiver ─▶ FireReminderUseCase
                                                   │            ├─ notify (Snooze / Done / Call)
                                                   │            └─ daily? re-arm tomorrow : mark delivered
BootReceiver (boot / app update) ─▶ RestoreRemindersUseCase (re-arm, fire missed, clean up)
```

- **Exact alarms:** `USE_EXACT_ALARM` (Android 13+, granted at install — fine for a personal app that isn't on Play) and `SCHEDULE_EXACT_ALARM` for Android 12. Without permission we fall back to an inexact alarm.
- **Snooze** creates a one-off copy 10 minutes out, so a daily schedule is never shifted.
- **Notification permission** (Android 13+) is asked when you first send a reminder; a banner offers to fix it if notifications are off.

## Architecture

```
presentation/   Compose screens (stateless) + ViewModels + Route composables (Android glue)
system/         BroadcastReceivers (alarm, boot)
domain/         Pure Kotlin: models, rules, use cases, ports (interfaces)
data/           Room, AlarmManager, NotificationManager, Contacts provider, WhatsApp intents
di/             Hilt modules binding ports to Android implementations
```

Decisions:
- **Hilt with kapt** (ADR 002 picks Hilt; kapt instead of KSP because Room + Hilt on kapt is the most battle-tested combo for Kotlin 1.9). Switch to KSP when moving to Kotlin 2.x.
- **Room** for contacts and reminders (queryable, observable with Flow, easy to test in memory).
- Screens take state and lambdas only; permission prompts and pickers live in `*Route` composables. That is what makes the UI testable without a device.

## Concepts used

ContentResolver queries · runtime permissions (READ_CONTACTS, POST_NOTIFICATIONS) · package visibility (`<queries>`) · implicit intents · AlarmManager + Doze · PendingIntent flags and request codes · BroadcastReceiver + `goAsync` · notification channels and actions · pinned shortcuts (ShortcutManagerCompat) · Room + Flow · Hilt (`@HiltViewModel`, `@AndroidEntryPoint`).
