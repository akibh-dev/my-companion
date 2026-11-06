# Voice completion plan — 2026-11-05

Production integration update: [current reader behavior and setup](12-production-voice.md).
The research/prototype descriptions below record earlier milestones.

## Scope and application status

This plan treats “voice” as the existing **read incoming messages aloud** feature.
Microphone commands, voice replies and transcription are separate features; the recognizer
currently has only a dummy implementation and its previous capture UI was removed in D19.

Reviewed starting from `main` at `a7985b0` (version 0.2.0). Eight modules remain: app, core,
ai-api, ai-models, adapters-messaging, feature-context, feature-autoreply and feature-voice.
People, reminders, YouTube playback, context rules, auto-reply, screen-on, export and widgets
have implementations and test suites. This is code-level status, not fresh phone acceptance.
The app actually targets/minimally supports API 36, despite the older SDK values in AGENTS.md.
Vault and quick capture were deliberately removed. Older handover/test documents describe
historical versions and should not be used as the current feature checklist.

## What voice already has

- Notification listener and adapters for WhatsApp/Business, Messenger, Signal, Telegram and Messages.
- Enable switch, Quick Settings tile, widget action and shared foreground service.
- App/sender filters, headphones/car privacy default, message length and emoji/link cleanup.
- A bounded queue of 20 messages, oldest-drop overflow and five-minute expiry.
- Call-state polling before reading, audio-focus request and phone TTS fallback.
- Bangla-script detection and interfaces for future local transliteration/synthesis models.
- Unit/Robolectric coverage of settings, queue, filtering, formatting and component behavior.

## Priority 1 — Make playback private and reliable

Primary files: `feature-voice/.../system/VoiceMode.kt`, `ai-api/.../Fallbacks.kt`,
`ai-api/.../AiInterfaces.kt`, `adapters-messaging/.../MessageListener.kt`.

1. Keep structured message data in the queue instead of only a rendered sentence. Recheck
   current feature state, sender/app filters, privacy and expiry immediately before speaking.
   Observe route/call changes while speaking and stop on headset disconnect, ringing, calls,
   loss of audio focus, master pause or voice disable. Clear pending messages on pause/disable
   so a later enable does not replay an earlier session. Use monotonic time for queue age.
2. Distinguish connected audio devices from the route speech actually uses. A connected
   Bluetooth device alone is not proof of private output, and BLE speakers are currently
   classified as private. Fail closed if the private route cannot be established. Validate
   wired, USB and Bluetooth routes on the phone, including a disconnect during an utterance.
3. Check the audio-focus request result and observe focus loss; do not speak after denial.
   Set matching speech audio attributes on TTS. Verify the existing foreground host supports
   background speech/focus on the target phone before changing service architecture.
4. Harden PhoneSpeechSynth: handle immediate `speak()` failure, callback errors, initialization
   failure, cancellation and missing callbacks with bounded waits. Report failures rather than
   returning Ready after `onError`; log “read” only on actual completion. Define interruption
   separately from successful completion. Release TTS resources at the appropriate lifecycle
   boundary and support initialization again after release/failure.
5. Add the TTS service visibility query to the merged manifest. Select an installed voice
   with `isNetworkConnectionRequired == false`, respecting the app's on-device-only promise;
   merely choosing a language does not enforce offline operation. If unavailable, show how
   to install voice data instead of silently choosing a network voice.
6. Verify notification update deduplication so the same message is not repeatedly read;
   preserve genuinely repeated messages. Test the actual notification lifecycle, not only
   direct calls to VoiceConsumer. Isolate failures per queue item so one exception cannot
   silently leave the voice component inactive while the host still reports it running.

Acceptance: no speaker disclosure under private mode; immediate stop on interruption; a failed
message cannot block later messages; no replay across disabled sessions; errors are visible.

## Priority 2 — Finish the usable English/Bangla version

Primary files: `feature-voice/.../ui/VoiceScreen.kt`, `feature-voice/.../domain/Voice.kt`,
speech interface and implementations in `ai-api` and `ai-models`.

1. Apply the persisted speechRate through a speech request/options contract and expose its UI.
2. Show readiness: notification access, host/listener connection, private route, installed
   offline languages, speaking/waiting state and the last actionable error. Add “Stop reading”.
3. Route sample playback through the same controller as real messages so tests and queued
   reads cannot interfere. Clearly label its output/privacy behavior and offer English,
   Bangla and mixed-language samples. Current sample bypasses privacy and call gating.
4. Detect language from the body, before adding the English sender/app announcement. Short
   Bangla messages currently risk being classified using that longer English wrapper.
   Render announcement and body as separate language-aware segments where necessary.
5. Preserve Bangla combining marks during cleanup. Replace the sender field's per-keystroke
   persistence/reset pattern with local editing and deliberate save/debounce; test fast typing.

Acceptance: downloaded English/Bangla voices work in airplane mode, rate changes are audible,
short and mixed messages choose sensible voices, and setup failures offer a clear next step.

## Priority 3 — Complete Banglish separately

The default transliterator passes text through. The optional dummy translates only a small
word list; the optional speech model always returns NotReady and uses phone TTS. Neither is
a production Banglish solution.

1. Create an anonymized evaluation set covering everyday Banglish, spelling variants, mixed
   English/Bangla, names, numbers and ambiguous words. Decide pronunciation acceptance by
   listening on the target phone. Obtain agreement on acceptable quality before model selection.
2. Transliterate only the message body, preserving names and the announcement. Replace the
   “one hint word” heuristic with explicit language selection and/or confidence-based routing;
   weak detections should preserve the original text.
3. Evaluate a rules/dictionary baseline, then candidate local models against that set. Record
   license, downloadable size, peak RAM, cold/warm latency and offline operation. Choose a
   model only from measured results; no model/package is selected by this review.
4. Bind the selected implementation behind Transliterator and existing model routing. Prefer
   the installed offline Bangla TTS if its quality is acceptable. Evaluate a bundled synthesis
   model only if that voice fails the listening test. Expose real engine readiness in settings.

## Validation and completion criteria

- Add focused regression tests for route disconnect during speech, call interruption, focus
  denial/loss, queue revalidation and clearing, TTS errors/timeouts/cancellation, sample
  contention, duplicate notification updates, speech rate and short/mixed Bangla text.
- Run `ktlintCheck testDebugUnitTest lintDebug assembleDebug --continue --max-workers=3`.
- On the CMF Phone 1: real chat notifications (direct/group/update), screen locked, music
  playing, incoming cellular/VoIP call, Bluetooth connect/disconnect, speaker opt-in, tile,
  master pause, reboot, permission revocation and recovery, and airplane-mode samples.
- Confirm background behavior during a normal day, including battery restrictions. Record
  setup, audible results and any failures in Docs; emulator text assertions cannot establish
  pronunciation, actual routing or OEM background survival.

Review validation: the full command above succeeded on 2026-11-05. Existing reports contain
572 tests with zero failures, errors or skips, including 24 voice tests. Most Gradle tasks
were up-to-date, so this was an incremental validation rather than a forced fresh test run.
ADB found no connected device; no audible or on-device checks were performed.

Suggested PR order: (1) privacy/interruption and queue lifecycle; (2) TTS/focus/offline handling;
(3) setup/status/rate and language-aware samples; (4) evaluated Banglish implementation.
English/Bangla completion does not depend on shipping a custom speech model.

## Official references

- [TextToSpeech API: initialization, voices, speak status, rate, audio attributes and shutdown](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Voice API: network connection requirement](https://developer.android.com/reference/android/speech/tts/Voice)
- [Audio focus: grant/failure, loss handling and foreground requirements](https://developer.android.com/media/optimize/audio-focus)
- [TextToSpeech.Engine: service visibility and voice-data intents](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine)
