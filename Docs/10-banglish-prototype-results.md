# Banglish prototype results — 2026-11-06

Production integration update: [current reader behavior and setup](12-production-voice.md).
The research/prototype descriptions below record earlier milestones.

The first implementation milestone is an offline IndicXlit evaluation prototype. Open
**Home → AI engines → Banglish preview (experimental)**. Enter Banglish, select **Preview
Bangla**, and inspect the converted text, candidate words and timing. The updated debug
APK is installed on `emulator-5554`.

The preview deliberately treats every Latin word as Banglish. It is separate from the
production model bindings and automatic notification reader. The existing engine switches
continue to use their existing dummy implementations. No real chat text was used for testing.

## Implemented

- Real pretrained IndicXlit encoder/decoder inference through ONNX Runtime Android 1.23.2.
- Bengali character vocabulary and bounded beam decoding; only complete Bengali-script
  candidates can be returned. Cancellation, a 30-second timeout and input limits prevent
  unbounded requests. Sessions and native tensors close after each request.
- Preview UI with loading, result, alternatives and failure states; no inference runs on
  the main thread. Punctuation, existing Bangla and numbers are preserved. All Latin words,
  including words in URLs, are converted in this raw evaluation mode.
- A pinned model fetcher with SHA-256 verification. The 48.5 MB graphs are ignored by Git;
  vocabulary and license attribution are included. Fetch before building an evaluation APK;
  a build without graphs shows an unavailable message instead of downloading at runtime.
- Four decoder regression tests, four targeted device tests and a 30-message starter corpus.
- A TSV report exporter and a standard-library Python diagnostic summarizer.

## Verification

Gradle formatting/checks, `testDebugUnitTest`, `lintDebug`, `assembleDebug` and
`:app:assembleDebugAndroidTest` passed after correcting build/test harness issues. Current
module reports contain **576 JVM tests**, zero failures/errors/skips; four are new beam tests.
Some Gradle tasks reused cached results. Old reports belonging to removed modules are excluded.

On Android 17 / API 37, x86_64 with 16 KB pages, **4 targeted instrumented tests passed**
in 30.06 seconds. These check real native conversion and candidates, punctuation, rejected
input, unchanged non-Latin input, starter-corpus report generation and the preview UI.
The UI test entered `ami` and verified `আমি`. The existing broader device suite was not rerun.

## Starter evaluation

| Category | Exact message matches | Character error rate |
|---|---:|---:|
| Banglish | 5 / 10 | 7.4% |
| English | 0 / 10 | 90.2% |
| Mixed English/Banglish | 0 / 10 | 49.3% |

English/mixed failures are expected for this raw converter; it has no language detector.
Banglish mismatches show that even after detection, spelling/candidate selection needs
evaluation. Examples:

| Input | Reference | Actual |
|---|---|---|
| `ami bangla boli` | আমি বাংলা বলি | আমি বাংলা বলি |
| `kal dekha hobe` | কাল দেখা হবে | কাল দেখা হবে |
| `ami bhalo achi` | আমি ভালো আছি | আমি ভালো আচি |
| `ami bari jabo` | আমি বাড়ি যাবো | আমি বারি যাবো |

Across these 30 short cases, median session-load time was **342.5 ms**, maximum 385 ms;
median inference time was **485.5 ms**, maximum 679 ms. Each request opens a fresh session.
These are emulator timings, not phone or persistent-session warm-p95 measurements.

The references are hand-written examples, not exhaustive acceptable pronunciations. Exact
matches and character errors do not establish audible quality. This corpus is a diagnostic
starter set, not the planned 300-message development/held-out benchmark. No target-phone,
native TTS listening, peak RAM or official Python/Fairseq parity result is claimed for
the emulator run. The subsequent physical-phone check is recorded below.

Raw results: [emulator-api37-starter.tsv](../tools/banglish/results/emulator-api37-starter.tsv).
Reproduction commands and model provenance: [tools/banglish/README.md](../tools/banglish/README.md).

## CMF Phone 1 validation — 2026-11-06

Wireless ADB successfully reached the owner's Nothing A015 (CMF Phone 1): Android 16,
API 36, `arm64-v8a`, 4 KB pages. The prototype and test APKs installed successfully.
The same four targeted instrumented tests passed in **64.545 seconds**, including real
native inference, invalid/non-Latin input, all 30 starter cases and the Compose preview.
The UI test entered `ami` and verified `আমি` on the physical phone.

All **30 conversion outputs matched the emulator exactly**. Quality therefore remains
5/10 exact Banglish message matches with 7.4% character error rate; the converter still
does not preserve English in the English/mixed categories because detection is absent.
Cross-device consistency is not independent parity with official Python/Fairseq inference.

| Timing over 30 short cases | Median | Maximum |
|---|---:|---:|
| Fresh model-session load | 722.5 ms | 2,280 ms |
| Conversion after session load | 907 ms | 3,019 ms |

The phone was slower and more variable than this emulator run. These are observed timings,
not a controlled device-performance comparison; thermal state and other device workload
were not controlled. Each preview still creates and closes both model sessions, so these
numbers do not establish persistent-session warm p95 or the 200-character latency target.

Raw phone results:
[cmf-phone1-api36-starter.tsv](../tools/banglish/results/cmf-phone1-api36-starter.tsv).
The app remains installed; open **Home → AI engines → Banglish preview (experimental)**
to try it. This validation did not enable notification access, test real chats, assess audible
English/Bangla TTS, measure peak RAM, or establish background survival. Automatic Banglish
message reading remains outside this prototype.

The next performance experiment should retain a loaded model session across previews and
measure the same corpus again, alongside the planned detection and candidate-quality work.

## Actual notification and spoken playback — 2026-11-06

At the owner's request, the CMF Phone 1 also completed an actual notification-to-speech
test. Android posted a visible MessagingStyle notification from My Companion with the
invented sender “My Companion test” and body `ami bangla boli`. The real notification
listener received it, IndicXlit produced `আমি বাংলা বলি`, and the phone's offline Bengali
TTS reported utterance completion. **The owner confirmed hearing the spoken message.**

The targeted instrumented test passed in **14.482 seconds**. Its only delivery mechanism
is NotificationManager: it does not call the observer, model or speech engine directly.
An initial attempt exposed a listener startup race after APK replacement; the test now
waits for the listener-connected state before posting the notification.

This is a debug-only fixture integration on the existing Android listener. It accepts only
My Companion's own fixed notification ID/channel and exact invented body. It does not enable
model conversion for ordinary chat notifications or prove mixed-language detection. Existing
voice-mode settings were not changed. The test notification remains available on the phone.

Supporting changes add TTS package visibility, select an installed offline voice, bound
initialization/playback waits, and report speech errors/interruption instead of returning
success. The fixture requests audio focus and stops speech on focus loss. Gradle unit tests,
lint, app/test APK builds and the final formatting check passed for this change.

Machine evidence:
[cmf-phone1-notification-speech.txt](../tools/banglish/results/cmf-phone1-notification-speech.txt).
The owner confirmed audibility; systematic pronunciation quality and background chat reading
remain separate acceptance work.

## Mixed Bangla / Banglish / English notification — 2026-11-06

The owner requested a notification containing all three forms. Android posted:

> আমি এখন বাসায়। kal dekha hobe. Please call me later.

The debug fixture uses explicitly annotated spans: the original Bangla is preserved,
`kal dekha hobe` goes through the real IndicXlit model, and English is preserved. It speaks
the combined Bangla text with the offline Bengali voice, then switches to an offline English
voice for the English sentence. It does not automatically infer those spans.

The resulting text was `আমি এখন বাসায়। কাল দেখা হবে. Please call me later.`.
The targeted phone test passed in **17.363 seconds**, with both actual TTS completion
callbacks recorded (`completed_segments=2`). The owner confirmed hearing all components,
but reported different perceived voices and noticeable pauses, especially before Banglish.
App/test APK builds and the formatting check passed. The notification remains visible.

Evidence:
[cmf-phone1-mixed-notification-speech.txt](../tools/banglish/results/cmf-phone1-mixed-notification-speech.txt).

## Consistent voice and shorter repeated startup — 2026-11-06

The owner prioritizes one consistent, understandable voice over native English pronunciation.
The current implementation therefore uses one installed offline Bengali voice for the entire
message, retaining English text and accepting its Bengali accent. The notification fixture
now joins Bangla, converted Banglish and English into a single TTS utterance. Voice mode's
ordinary reader and sample button request the same Bengali locale. Generic AI speech APIs
still support other requested languages.

Voice selection has a deterministic tie-breaker and caches its selected voice per locale.
The prototype shares a model session across callers, serializes inference and releases the
graphs after 30 seconds of inactivity. TTS preparation runs concurrently with conversion.
These changes remove the app's language-boundary TTS restart; natural punctuation pauses
or engine-internal prosody can remain.

The CMF Phone 1 test posted the same mixed notification twice and passed in **22.137 seconds**.
Both reads completed exactly one utterance using voice ID **`bn-BD-language`**. Measured
components were:

| Component | First read | Second read |
|---|---:|---:|
| Model session load | 820 ms | 0 ms |
| Banglish conversion | 1,116 ms | 838 ms |
| TTS queued → start callback | 98 ms | 9 ms |

These are component measurements, not a controlled end-to-end latency benchmark. The first
read still loads the model; the second demonstrates reuse within the idle window. No result
here establishes 200-character p95 latency. Human confirmation of the new consistent sound
and perceived pause was requested separately. The owner subsequently confirmed consistent,
clear playback and an acceptable pause, but found the English pronunciation tradeoff poor.
The next preference is a bright, slightly robotic female announcer voice while retaining
one consistent voice for the whole message.

Validation: **579 JVM tests**, no failures/errors/skips in current module reports, including
three new idle-session tests covering reuse, release after failure and protection during
active inference. The existing reader test now checks that English and converted Banglish
both request Bengali. Lint, formatting, app/test APK builds and the targeted phone test passed.

Evidence: [first read](../tools/banglish/results/cmf-phone1-single-voice-first.txt),
[warm read](../tools/banglish/results/cmf-phone1-single-voice-warm.txt).

The owner also requested later spelling fallbacks for unsupported words, digits and
punctuation. This is recorded in the research plan and is not implemented in this change.

## Next milestone

Expand/split the labeled evaluation corpus, compare ONNX outputs with official inference,
inspect candidate ranking for common spelling variants, and evaluate conservative
English/Banglish span detection. Then benchmark an alternative pretrained converter if
IndicXlit remains below the pronunciation target. Only integrate automatic span-aware
reading after those gates; this prototype alone does not complete Banglish voice.

See [the research plan](09-banglish-model-research-plan.md) for the agreed English-preservation
behavior and pretrained-first strategy.
