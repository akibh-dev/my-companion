# WhatsApp call button and custom notifications

## One-tap WhatsApp call
WhatsApp has no public "call this number" API. Instead, for each phone contact that uses
WhatsApp it adds a row to the Contacts provider with MIME type
`vnd.android.cursor.item/vnd.com.whatsapp.voip.call`. Launching that row with `ACTION_VIEW`
starts a voice call directly.

Flow in `data/WhatsAppCaller.kt`:
1. Needs `READ_CONTACTS` and the number saved in the phone's address book.
2. Query `ContactsContract.Data` for the voip row whose `DATA1` is `<digits>@s.whatsapp.net`.
3. Found: launch it (call starts). Not found / no permission: open `https://wa.me/<digits>`
   (chat opens, call button one tap away).

Caveats: this relies on WhatsApp's undocumented contact rows and could change. Numbers must
include the country code.

Concepts: ContentResolver, runtime permissions, `<queries>` (package visibility, API 30+), implicit intents.

## Custom notifications
- `NotificationHelper`: channel (API 26+), `POST_NOTIFICATIONS` check (API 33+), builder with a "Snooze 10 min" action.
- `ReminderScheduler`: `AlarmManager.setAndAllowWhileIdle` (inexact, no special permission).
- `ReminderReceiver`: fires the notification, or reschedules on snooze.

Known gap: pending reminders are lost on reboot. Next step: persist them and re-arm on
`BOOT_COMPLETED`, or switch to WorkManager.

Concepts: NotificationChannel, PendingIntent flags, BroadcastReceiver, Doze.
