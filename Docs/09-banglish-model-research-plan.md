# Banglish voice: pretrained models and recommended approach

Production integration update: [current reader behavior and setup](12-production-voice.md).
The research/prototype descriptions below record earlier milestones.

Research date: 2026-11-05. This is a research-backed plan; model accuracy, Android
compatibility, latency and memory use have not yet been measured locally.

**Owner update, 2026-11-06:** use one consistent Bengali voice for the complete message,
including English words; a Bengali accent on English is acceptable. Preserve English text,
but do not switch to an English voice. Audible tests and measurements now appear in
[the results report](10-banglish-prototype-results.md), superseding the research-time
statement above that no measurements were available.

## 1. Recommendation

**We can reuse pretrained models without training a transliterator from scratch.** Start
with IndicXlit, retain the phone's English/Bangla TTS, and evaluate lightweight detection
before committing to a classifier model.

```text
Incoming message
  → identify English / Bangla / Banglish spans
  → convert only Banglish spans to Bangla script
  → join the processed spans and read once with a fixed Bengali TTS voice
```

The task is transliteration: `ami bhalo achi → আমি ভালো আছি`. It does not require
translating or rewriting the message.

Completely independent word conversion has limits: `am` could mean English “am” or
Banglish “আম”; `to` could be English or “তো”. Neighboring-word context helps identify
which language a word belongs to. This does not require a general-purpose LLM.

The owner's selected preferences are to preserve English words in mixed messages and
try pretrained models first, training a compact detector only if necessary.

## 2. Relevant pretrained options

Sizes describe model files or authors' reported sizes, not total Android RAM use.
These models have not been benchmarked on the target phone.

| Candidate | What it provides | Size / availability | Assessment |
|---|---|---|---|
| AI4Bharat IndicXlit | Word-level Roman-to-Bangla transliteration, among 21 supported languages | About 11M parameters; pretrained downloads; MIT code and models | First candidate. Its word-level design matches the proposed conversion. [Official repository](https://github.com/AI4Bharat/IndicXlit) |
| nahidstaq/bangla-transliteration | Bangla-specific character-level encoder–decoder | Author reports 6M parameters, 24 MB, MIT; weights require accepting Hugging Face access conditions | Second candidate. Author reports 9.18% character error rate; needs independent testing on chat messages. [Model card](https://huggingface.co/nahidstaq/bangla-transliteration) |
| bntranslit | Pretrained Bangla word transliteration using an attention-based LSTM | CPU inference, PyTorch checkpoint, MIT repository | Useful older comparison baseline; less attractive as the main integration target. [Repository](https://github.com/sagorbrur/bntranslit) |
| AI4Bharat IndicLID | Identifies native and romanized languages, including ben_Latn and English | Published Roman fastText model: 357 MB; full pipeline: approximately 1.4 GB; MIT | Relevant pretrained detector, but too large to assume it belongs in this app. Sentence classification does not solve mixed-message token detection. [Official results](https://github.com/AI4Bharat/IndicLID#evaluation-results) |
| fastText lid.176 | General language identification | Compressed version 917 kB; CC BY-SA 3.0 | Small, but documented training sources and labels do not establish reliable Banglish recognition. Benchmark only; Bengali support does not prove romanized-Bengali support. [Official documentation](https://fasttext.cc/docs/en/language-identification.html) |
| fms-byte/banglish_to_bangla | Task-specific mBART sequence generation | Weight file approximately 2.44 GB | Available pretrained, but unnecessarily heavy for the first Android version. [Files](https://huggingface.co/fms-byte/banglish_to_bangla/tree/main) |

Two misleading matches to avoid:

- BanglishBERT is a pretrained Bengali/English understanding model, not a ready
  Banglish-to-Bangla converter or language classifier. Task-specific fine-tuning would
  be necessary. [BUET model card](https://huggingface.co/csebuetnlp/banglishbert)
- banglish-ontor has tiny classifiers, but their outputs concern emotion, warmth and
  reply intent, not English/Banglish identification.
  [Author's description](https://huggingface.co/sifat-febo/banglish-ontor)

## 3. Reusing existing weights on Android

Reuse the learned weights: yes. Drop the original Python package into Kotlin: no.
Android needs an inference runtime, matching character vocabulary, preprocessing and decoding.

An unofficial IndicXlit ONNX port provides downloadable encoder, decoder and vocabulary
files. Repository metadata checked during research lists 20,225,148 bytes for the encoder
and 28,241,504 bytes for the decoder, approximately 48.5 MB combined. This is a concrete
starting point, but compare outputs with upstream inference before trusting it. Its optional
frequency dictionaries add substantial storage, so exclude them initially.
[Port and inference implementation](https://github.com/cloudrumbles/indic-transliterate)

The 6M Bangla-specific model also has a community ONNX export project supporting FP32,
INT8 and FP16 export. It expects access to the original checkpoint; the npm package does
not include the weights.
[Export project](https://github.com/inj-src/transliteration-bangla)

Use ONNX Runtime Android on CPU for the first prototype. Quantization is a later
optimization, after verifying output parity. Android support exists, but compatibility
of each graph still needs testing.
[ONNX Runtime Android documentation](https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html)

## 4. Implementation and evaluation plan

First milestone: prove pretrained conversion quality before changing the production voice pipeline.

1. Build a fixed evaluation set of 300 messages: 100 English, 100 Banglish and 100 mixed,
   with separate development and untouched test portions. Include spelling variants, short
   replies, names, numbers and ambiguous words. Label English/Banglish spans and acceptable
   Bangla outputs.
2. Benchmark IndicXlit first. Compare the ONNX port against official inference. Evaluate
   the 6M model separately if its access conditions are accepted. Published scores use
   different datasets and must not be used to rank these models directly.
3. Start detection with script checks and conservative lexical rules. Bangla script needs
   no classifier. For Latin text, preserve known English phrases and uncertain words;
   convert confidently identified Banglish. Protect links, numbers and sender metadata.
4. Test IndicLID as a desktop reference rather than shipping its full pipeline. If the
   lightweight approach misses the acceptance targets, train a small token classifier
   using character features and neighboring words. A relevant public resource is BanglaMix,
   which provides English/Bangla/romanized-Bangla token labels; audit its released data and
   license before use. This refers to the text dataset, not the similarly named speech
   corpus. [Dataset paper](https://pubmed.ncbi.nlm.nih.gov/42910137/)
5. Integrate the successful pipeline: retain the existing Transliterator interface; add a
   detector returning labeled spans and a speech planner returning ordered text/language
   segments. Keep inference in ai-models and planning rules in pure Kotlin.
6. Preserve the order of the detected spans, join their processed text, and speak the whole
   message in one Bengali utterance. Example intermediate spans:

   ```text
   ami meeting e achi, call later
   → [bn: আমি] [en: meeting] [bn: এ আছি] [en: call later]
   ```

   These labels identify which text to convert, not separate TTS voices. This is a target
   behavior for automatic detection, not a measured model prediction.

Proposed acceptance targets:

- At least 98% English preservation.
- At least 90% Banglish-token detection recall.
- At least 90% pronunciation-acceptable converted words on the held-out set.
- Measure warm preprocessing latency, cold loading time and peak RAM on the CMF Phone 1.
  Target warm p95 below one second for a 200-character message.

If quality fails, keep automatic conversion experimental and add the compact detector
before release.

## 5. Defaults and completion checks

- Preserve English words and try pretrained models first, as selected by the owner.
- Keep processing offline; do not send notification content to hosted inference.
- Preserve uncertain text rather than producing an unrelated Bangla word.
- Verify installed offline English and Bengali voices. Android exposes whether a voice
  requires a network connection; Bengali availability and pronunciation depend on the
  installed engine. [Android Voice API](https://developer.android.com/reference/android/speech/tts/Voice)
- Test mixed-language transitions, missing models/voices, cancellation, calls and headset
  disconnects alongside the reliability work in [the voice completion plan](08-voice-completion-plan.md).
- Record model revisions, licenses, vocabulary, decoding settings and measured results
  so the selected configuration is reproducible.

The evaluation prototype and explicit notification fixtures now exist; see the results report.
Automatic detection, corpus expansion and quality evaluation remain open before production
Banglish conversion is enabled.

## Deferred pronunciation fallback

The owner requested this for a later milestone, not the current voice/timing polish:
spell unsupported words letter by letter (`mooo` → “em, o, o, o”), and verbalize digits and
punctuation individually (`1234;` → “one, two, three, four, semicolon”). Preserve every
character. The criteria for an unsupported word and the punctuation mapping still need
to be specified before implementing that fallback.
