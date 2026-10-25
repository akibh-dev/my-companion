# Device-test tools

Small helpers used to test My Companion on an emulator from the command line
(results: [`Docs/06-device-test-report.md`](../../Docs/06-device-test-report.md)).
Nothing here ships in the app.

| File | What it does |
|------|--------------|
| `ui.py` | Drives the UI through the accessibility tree: `dump`, `tap "Save"`, `has "Reminder set"`, `type "hello"` |
| `shot.sh NAME` | Screenshot to `out/shots/NAME.png` (+ a small copy for quick viewing) |
| `db.sh "SQL"` | Copies the app's Room database off the device (`run-as`, debug builds only) and runs queries |
| `seed-contacts.sh` | Adds "Mum Test" (with a WhatsApp voice-call row) and "Dad Test" to the phone book |
| `fake-whatsapp/` | A tiny APK with package `com.whatsapp` that logs the intents it receives |

All scripts find `adb` through `$ADB`, `$ANDROID_HOME` or `~/Android/Sdk`. Output goes to `out/` (git-ignored).

## Why a fake WhatsApp?

The real app can't be installed on an emulator without a Google account and a phone number, but the
call feature depends on it being there. The fake answers the same two intents My Companion sends:

| Intent | Sent when |
|--------|-----------|
| `ACTION_VIEW content://com.android.contacts/data/<id>` + type `vnd.android.cursor.item/vnd.com.whatsapp.voip.call` | The number has a WhatsApp voice-call row in the phone book → direct call |
| `ACTION_VIEW https://wa.me/<digits>` (package `com.whatsapp`) | No such row → open the chat |

`seed-contacts.sh` creates a phone-book contact with a voice-call row shaped like WhatsApp's own, so the
whole path (Contacts query → number matching → intent) runs for real.

## Typical session

```bash
export JAVA_HOME=~/.jdks/corretto-17.0.20.1     # the build needs JDK 17
emulator -avd Pixel_8 &                          # or start it from the IDE's Device Manager
./gradlew installDebug

tools/device-test/fake-whatsapp/build.sh
adb install -r tools/device-test/fake-whatsapp/build/fake-whatsapp.apk
tools/device-test/seed-contacts.sh

adb logcat -s FakeWhatsApp &                     # watch what "WhatsApp" receives
tools/device-test/ui.py tap "People"
tools/device-test/ui.py tap "Call Mum Test on WhatsApp"
```

## Gotchas learned the hard way

- **Wait ~1 s after focusing a text field before `type`.** Text injected while the keyboard is still
  connecting can land in the wrong field or drop characters (see finding #5 in the report).
- **Don't tap the middle of a filled field to append** — the cursor goes where you tap. Tap near the
  right edge, then send `KEYCODE_MOVE_END`.
- **`KEYCODE_BACK` closes the keyboard first; a second BACK closes the dialog or leaves the app.**
- Elements behind the keyboard still appear in `ui.py dump` with their old coordinates — tapping them
  hits the keyboard instead. Close the keyboard first.
- `adb shell monkey -p com.akib.mycompanion -s 42 --pct-syskeys 0 --throttle 40 -v 5000` is a cheap crash hunt.
- Useful system views: `adb shell dumpsys alarm | grep -A3 mycompanion`,
  `adb shell dumpsys notification --noredact`, `adb shell dumpsys media_session`,
  `adb shell dumpsys activity services com.akib.mycompanion`.
