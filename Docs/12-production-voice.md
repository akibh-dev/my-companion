# Production voice reader

The automatic reader now uses the pretrained offline IndicXlit ONNX model through the existing AI router, rather than the old dummy word dictionary. It transliterates the message body and sends the complete announcement to one installed offline Bengali TTS voice. Sender names and app labels remain as written. The exact voice style will be chosen later; no paid voice service or network speech backend has been added.

## Setup

1. Run `python3 tools/banglish/fetch_models.py` before building. Graphs are pinned by upstream revision and SHA-256, packaged in the APK, and excluded from Git. `preBuild` verifies them; CI downloads and verifies them before building.
2. Install an offline Bengali voice using the phone's Text-to-speech settings. Grant My Companion notification access.
3. Enable Read messages aloud from the Voice screen. This also enables the transliteration engine. Choose apps, optionally save a sender allowlist, and select the output privacy policy.
4. Use Read a test message. It follows the same privacy, pause, call, speed, serializer and audio-focus checks as automatic reading. Stop reading clears pending messages and cancels current preparation/playback.

## Text routing and limits

- Auto converts a small explicit set of common Banglish tokens; existing Bangla, punctuation and other English words stay intact. This is a conservative heuristic, not a trained language classifier. Ambiguous English words are deliberately excluded.
- Banglish text converts all Latin words, including English. Use it only when that tradeoff is wanted.
- As written bypasses transliteration.
- Failed or timed-out conversion preserves the original text. Conversion has a 30-second budget per message. Length is bounded to 600 input characters and each model word to 48 letters. Speed is persisted and applied between 0.5 and 2.
- English pronunciation has the selected Bengali voice's accent. The small evaluation fixture measures text conversion only and is not a broad accuracy or intelligibility guarantee; see the model research and prototype reports.
- Character-by-character spelling of unknown words, numbers and punctuation is deferred by request.

## Lifecycle and privacy decisions

Messages enter a bounded queue as structured data. The reader rechecks current settings and actual anticipated speech routes before playback and while work is active. Connected Bluetooth devices alone do not prove that speech is routed privately. Unknown routes and Bluetooth speakers are not accepted for private playback. Calls, master pause, disabling voice mode, audio-focus loss, output disconnect and Stop interrupt speech. Android route updates can be asynchronous; unit checks cannot establish timing guarantees for every headset/car.

The queue drops the oldest item after 20 pending messages, expires items after five minutes, suppresses duplicate notification updates using message timestamps, and invalidates old sessions on Stop/disable. TTS initialization and utterance waits are bounded. Native inference is serialized and model sessions are released after 30 seconds idle. Logs record outcomes without message text.

Notification adapters prefer the newest MessagingStyle message timestamp over a changing notification post time. Plain notifications without message timestamps can still be treated as new if the app republishes them with a new timestamp.

## Verification

Domain and Robolectric tests cover token routing, fallback, queue order/deduplication/session invalidation, privacy rechecks, pause during preparation, audio routing and audio-focus denial. Device fixtures cover real ONNX inference, the preview UI, actual notification-listener delivery and TTS completion. `ProductionVoiceDeviceTest` additionally exercises the routed production controller and configured speed, restoring settings in `finally`.

Local validation on 2026-11-06:

- 591 tests across the eight current modules: zero failures, errors or skips. Removed-module build directories are excluded from that count.
- `ktlintCheck`, `lintDebug`, `assembleDebug`, `:app:assembleDebugAndroidTest` and `assembleRelease` passed, including release vital lint.
- All three pinned model-file SHA-256 checks passed.
- Earlier prototype/notification tests ran on the CMF Phone 1 as recorded in the results report. The final production-controller phone test is compiled but has not been executed: the phone is currently disconnected from ADB and wireless discovery. That final replay and physical headphone-disconnect test remain device verification work.

Audition fixtures and research previews are diagnostic tools; they do not select a new default voice.

## References

- Android TextToSpeech: https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Anticipated audio routing: https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)
- Audio focus: https://developer.android.com/media/optimize/audio-focus
- Upstream ONNX port and licensing: https://github.com/cloudrumbles/indic-transliterate
- IndicXlit: https://github.com/AI4Bharat/IndicXlit
