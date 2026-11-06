# Female announcer voice selection — 2026-11-06

## Owner's preference

The single-voice mixed-message replay was confirmed consistent and clear, with an acceptable
pause. Bengali/Banglish improved relative to the split-voice version, while English suffered.
The desired replacement is a bright, slightly robotic female announcer voice, similar to
stream subscription/donation alerts. One consistent voice for the full message remains the
priority. The owner accepted this general style without requiring a particular streamer's
voice. Offline processing remains the project default.

## Phone inventory and listening comparison

The CMF Phone 1 inventory test passed. It recorded installed, offline English/Bengali voices;
it did not play audio, change the system default or select a new app voice. Android's Voice
metadata does not provide a gender field, so perceived gender/style needs listening feedback.

Relevant installed Bengali presets:

| Preset | Locale | Role |
|---|---|---|
| `bn-BD-language` | bn-BD | Current deterministic app selection |
| `bn-bd-x-ban-local` | bn-BD | Bangladesh preset |
| `bn-IN-language` | bn-IN | India language default |
| `bn-in-x-bnc-local` | bn-IN | Audition A |
| `bn-in-x-bnd-local` | bn-IN | Audition B |

The audition diagnostic plays A and then B, each with the same text, normal pitch and normal
rate. The Bengali labels “প্রথম কণ্ঠ” and “দ্বিতীয় কণ্ঠ” identify the first and second voices.
The sample combines the English alert phrase “Mr X just subscribed to your channel. Ha ha ha.”
with native Bangla and an English sentence. It uses each preset for one entire utterance.
It does not claim automatic gender detection or an exact streamer-voice match.

The app's selected voice and system settings remain unchanged during the comparison.
Choose a preset only after the owner hears it. Neither sample sends text to an online service.

Inventory evidence:
[cmf-phone1-offline-voice-inventory.tsv](../tools/banglish/results/cmf-phone1-offline-voice-inventory.tsv).

The real-phone audition test passed in **21.091 seconds**, with both preset utterances
reporting completion. The owner was asked to choose A, B or neither; no new default has
been applied. Test APK compilation and formatting checks passed.
[Audition evidence](../tools/banglish/results/cmf-phone1-announcer-audition.tsv).

## Brighter robotic auditions C / D

The owner rejected A and B and requested a more robotic, brighter sound, described as
“70% female + 30% male.” This is treated as a perceptual target: female-forward with some
lower resonance. It is not an objective gender measurement or a mix of two narrators.

The next auditions are rendered locally using the installed **eSpeak NG 1.52.0** formant
synthesizer, which exposes pitch and resonance controls. They are pre-rendered listening
samples played through MediaPlayer on the phone, **not an installed Android speech backend**.
The app's current voice is unchanged. No text is sent to a service.

| Sample | Pitch endpoints | Intended difference | File duration |
|---|---|---|---:|
| C | 155–175 Hz | Brighter, higher synthetic character | 11.50 s |
| D | 140–160 Hz | Fuller lower tone, same bright formant profile | 11.09 s |

Both use narrow pitch variation and adjusted formants. SSML selects English and Bengali
pronunciation while reusing the same character variant settings in one continuous WAV.
The owner must judge whether that preserves a coherent perceived speaker across languages.
Both files are 22,050 Hz PCM and were checked for clipped samples (none found).

The reproducible renderer is `tools/banglish/render_robotic_samples.py`; generated audio and
the local engine-data copy stay in ignored `out/robotic-voice/`. It never changes system
engine data. The audition includes the subscription/donation-style example and a Bengali/
English sample. It is a sound/style comparison, not a new notification-to-synthesis test.

Reference: [eSpeak NG voice attributes](https://github.com/espeak-ng/espeak-ng/blob/master/docs/voices.md),
[SSML support](https://github.com/espeak-ng/espeak-ng/blob/master/docs/markup.md).

The C/D phone playback test passed in **26.02 seconds**, with both files completing.
The test APK build and formatting checks passed. Listening preference was requested; no
new default has been applied.
[Playback evidence](../tools/banglish/results/cmf-phone1-robotic-audition.tsv).

## Research constraints

Streamlabs supports selecting from many alert TTS voices, so the wording of an alert does
not identify its speaker. [Streamlabs documentation](https://support.streamlabs.com/hc/en-us/articles/4413183793563-How-to-Add-Text-to-Speech-for-Tips-and-Bits-to-Your-Stream).

Amazon Polly provides female English presets such as Joanna and Salli, but its published
language list does not include Bengali. Those are cloud-service voices, not downloadable
Android system presets. They are not a direct replacement for the current offline,
single-voice Bengali/English pipeline.
[Available voices](https://docs.aws.amazon.com/polly/latest/dg/available-voices.html),
[supported languages](https://docs.aws.amazon.com/polly/latest/dg/supported-languages.html),
[API architecture](https://aws.amazon.com/polly/features/).

Kokoro-82M offers open-weight female English voices, but Bengali is absent from its published
voice/language inventory. It therefore does not establish a ready solution for reading the
entire mixed message with one voice.
[Official voice inventory](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md).

No cloud speech service has been enabled and no new speech-model weights have been installed.
If the installed presets cannot meet clarity/style requirements, evaluating another offline
multilingual engine is a separate step, with actual Bengali/English listening and phone
latency/memory measurements before choosing it.

## Selection deferred

The owner rejected A/B and C/D. Keep the current deterministic Bengali voice while the
reader is hardened; choose a replacement later. Browser audition options include
[Lazypy TTS demos](https://lazypy.ro/tts/), [ElevenLabs voice library](https://elevenlabs.io/voice-library)
and the [Amazon Polly console](https://console.aws.amazon.com/polly/). These can audition
styles independently of this app. A demo voice is not necessarily an installable offline
Android voice; choosing a cloud voice would require a separate backend decision.
