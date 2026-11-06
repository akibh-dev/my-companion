# IndicXlit evaluation prototype

This milestone evaluates real pretrained offline conversion. It does **not** enable the model
for automatic notification reading. Home → AI engines → **Banglish preview (experimental)**
opens a separate preview. It deliberately treats every Latin word as Banglish so errors in
English preservation are visible; the existing model switches still control their existing
dummy implementations. This is not yet the mixed-language detector or speech planner.

## Reproduce

```bash
python3 tools/banglish/fetch_models.py
python3 tools/banglish/fetch_models.py --check
JAVA_HOME=/home/akib/.jdks/corretto-17.0.20.1 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=3
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class com.akib.mycompanion.BanglishModelDeviceTest com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as com.akib.mycompanion cat files/banglish-evaluation.tsv > /tmp/banglish-evaluation.tsv
python3 tools/banglish/summarize.py /tmp/banglish-evaluation.tsv
```

The graphs are ignored by Git to avoid adding 48.5 MB of binary weights to source control.
The fetcher pins the source revision and checks SHA-256 before replacing each file. Fetch
before building an evaluation APK. Normal builds without weights remain valid; the preview
reports that the offline model is unavailable. Native inference tests explicitly require
weights; no model download happens in the app or automatically during Gradle builds.

## Model provenance and runtime

- Original model: [AI4Bharat IndicXlit](https://github.com/AI4Bharat/IndicXlit), Roman-to-Indic v1.0.
- ONNX conversion: [cloudrumbles/indic-transliterate](https://github.com/cloudrumbles/indic-transliterate),
  revision `235fe2976d3e5c3c7d883af8cdfac4a83463d4d9`.
- Both projects use MIT; the port's license and AI4Bharat attribution are included in the APK's assets.
- Encoder: 20,225,148 bytes; decoder: 28,241,504 bytes; vocabulary: 9,657 bytes.
- Runtime: ONNX Runtime Android 1.23.2, CPU, two intra-op threads, one inter-op thread.
- Decoder: Bengali tag `__bn__`, beam width 4, length penalty `(5 + generatedLength) / 6`,
  at most 48 generated steps; only EOS-completed Bengali-script hypotheses are returned.
  This uses a bounded beam search rather than blindly copying the port's early-stop heuristic.
  Upstream Python/Fairseq parity has not yet been established.
- Limits: 600 input characters, 48 letters per word. Punctuation, digits, spaces and existing
  Bangla are preserved. Latin text inside links is **not protected in this raw evaluation**;
  do not use it as the production message processor.
- Cancellation is checked during decoding. Tensors and results close after use; native
  model sessions are reused and close after 30 seconds of inactivity. Inference is serialized
  so idle cleanup cannot close a session in use. Repeated words share a per-request cache.
  No message text is logged.

## Interpreting results

The TSV report includes source text, reference target, actual output, session-load time and
inference time. The 30 hand-written starter examples are plumbing and diagnostic cases,
not the planned 300-message development/held-out benchmark. English and mixed cases expose
the absence of a classifier; they are not expected to pass English-preservation targets yet.
Reference spellings are examples, not an exhaustive list of pronunciation-acceptable variants.

The original reports opened/closed sessions on every request. The current prototype retains
them for 30 seconds: `load_ms=0` identifies a reused session. Cold/warm comparisons must
therefore be reported separately. Emulator numbers do not establish phone performance,
audible quality or memory acceptance.

Remaining quality work: independently validate ONNX outputs against official inference, expand
and split the labeled corpus, and benchmark detection and pronunciation. Production integration
now exists with conservative token routing; see [production voice notes](../../Docs/12-production-voice.md). See [the saved plan](../../Docs/09-banglish-model-research-plan.md).

The first measured run and its limitations are recorded in
[Docs/10-banglish-prototype-results.md](../../Docs/10-banglish-prototype-results.md).

## Real notification-to-speech fixture

The debug build additionally supports explicitly invoked, fixed-message spoken tests:

```bash
adb -s PHONE_SERIAL shell am instrument -w -e class com.akib.mycompanion.BanglishNotificationSpeechDeviceTest com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
```

Install both current APKs first. Notification access for My Companion must be enabled,
and the phone must have an installed offline Bengali TTS voice. Keep the phone unlocked:
the test opens the app, waits for the notification listener, posts `ami bangla boli`,
then checks real model conversion and the TTS completion callback. It grants the app's
notification permission and leaves the clearly labeled test notification visible.

The fixture lives in `app/src/debug`; release builds have no fixture observer binding.
It handles only the app's own fixed notification ID/channel and allowlisted invented messages.
This fixture is separate from automatic chat reading, which now uses the real model through
the production router and conservative token routing. The successful CMF Phone 1 run and human audibility confirmation are in the report
linked above. This test plays audio aloud, so run it deliberately.

For the explicitly annotated mixed-language fixture, run only its test method:

```bash
adb -s PHONE_SERIAL shell am instrument -w -e class 'com.akib.mycompanion.BanglishNotificationSpeechDeviceTest#mixedNotificationIsReadWithOneVoice' com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
```

It posts `আমি এখন বাসায়। kal dekha hobe. Please call me later.`. Only the known Banglish
span is converted. Following the owner's listening feedback, one fixed Bengali voice reads
the whole message, including English, in a single utterance. Both fixtures are explicitly
allowlisted in the debug observer; this is not an automatic mixed-language detector.

The method `mixedNotificationKeepsOneVoiceAndReusesTheLoadedModel` posts the mixed fixture
twice in one process. It verifies one completed utterance per message, the same voice ID on
both reads, and zero graph-load time for the second request. Its reports include model-load,
conversion and TTS queue-to-start callback timings.

## Robotic character auditions (pre-rendered)

`python3 tools/banglish/render_robotic_samples.py` uses installed eSpeak NG and its data at
`/usr/share/espeak-ng-data` to render C/D into ignored `out/robotic-voice/`. It copies data
before adding custom variants, leaving the installed synthesizer unchanged. These files
compare voice style; they do not install or integrate an Android TTS backend.

After building/installing the current test APK, copy each WAV into the debug app's files:

```bash
adb -s PHONE_SERIAL shell "run-as com.akib.mycompanion sh -c 'cat > files/robotic-c.wav'" < out/robotic-voice/robotic-c.wav
adb -s PHONE_SERIAL shell "run-as com.akib.mycompanion sh -c 'cat > files/robotic-d.wav'" < out/robotic-voice/robotic-d.wav
adb -s PHONE_SERIAL shell am instrument -w -e class com.akib.mycompanion.RoboticVoiceAuditionDeviceTest com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
```

The test plays C first, D second, and records MediaPlayer completion for both. It does not
prove live synthesis performance or that the owner likes either candidate. See
[the voice-style notes](../../Docs/11-voice-style-audition.md) for the target and settings.

## Production controller device test

`ProductionVoiceDeviceTest` exercises the actual routed transliterator and shared playback
controller, configured speed, completion and master-pause gate. It disables automatic voice
reading before allowing the invented foreground sample on the speaker, and restores settings
in `finally`. Run it deliberately on an unlocked phone with an offline Bengali voice:

```bash
adb -s PHONE_SERIAL shell am instrument -w -e class com.akib.mycompanion.ProductionVoiceDeviceTest com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
```
