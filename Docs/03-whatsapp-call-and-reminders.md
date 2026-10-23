# WhatsApp calls and custom reminders

What the app does today, how each piece works, and the Android concepts behind it.

## What you can do

| Screen | Action | What happens |
|--------|--------|--------------|
| **People** | Tap the green call button | WhatsApp voice call starts (or the chat opens, see below) |
| | ⋮ → Add to home screen | A home-screen icon that calls that person in one tap |
| | ⋮ → Edit / Remove | Remove shows a snackbar with **Undo** |
| | Add person → Choose from phone contacts | Name and number filled from the phone book |
| **Reminders** | Title/message + **Now** | Notification appears immediately |
| | 5 min … 1 h, or **Pick time** | Exact alarm at that time |
| | Pick time + **Repeat every day** | Same local time every day (survives DST) |
| | Add a call button | Notification gets a "Call Mum" button |
| | Notification buttons | **Snooze 10 min**, **Done**, optional **Call** |
| | Upcoming list ✕ | Cancels the alarm and deletes the reminder |

Reminders survive reboots and app updates (`BootReceiver` re-arms them; anything missed while the phone was off fires right away).

## How the one-tap WhatsApp call works

WhatsApp has **no public "call this number" API**. What it does do: for every phone contact that uses WhatsApp, it adds a row to the Contacts provider with MIME type
`vnd.android.cursor.item/vnd.com.whatsapp.voip.call` (Business: `…vnd.com.whatsapp.w4b.voip.call`). Launching that row with `ACTION_VIEW` starts the call.

```
CallContactUseCase
  ├─ WhatsApp installed?            no  → "WhatsApp isn't installed"
  ├─ find voip row for the number   (needs READ_CONTACTS + number saved in phone contacts)
  │     found → ACTION_VIEW row     → call starts
  └─ otherwise → https://wa.me/<digits> → chat opens (call button is one tap away)
```

Matching (`WhatsAppNumberMatcher`) compares the number against the row's WhatsApp id (`8801711123456@s.whatsapp.net`) and its label, falling back to the last 10 digits so numbers saved without a country code still match.

**Caveat:** this relies on WhatsApp's undocumented contact rows. If a WhatsApp update changes them, calls degrade to opening the chat; nothing crashes.

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
