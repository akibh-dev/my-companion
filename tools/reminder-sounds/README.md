# Reminder sound presets

Bell, Chime and Beep are original mathematically synthesized PCM WAV tones. There are no
external recordings, voice samples, dependencies or third-party sound licenses. Reproduce
them with `python3 tools/reminder-sounds/generate_presets.py`.

The generated audio and this generator are dedicated to the public domain under CC0 1.0:
https://creativecommons.org/publicdomain/zero/1.0/ .

The app references presets by stable resource names and channel identities, not compiled
numeric resource IDs. Changing the actual tone later requires a new preset/version identity
so existing Android channel settings remain meaningful.

Device import fixtures are original Beep encoded with local ffmpeg (not downloaded songs):

```bash
cp app/src/main/res/raw/reminder_beep.wav app/src/androidTest/assets/reminder-sounds/sample.wav
ffmpeg -i app/src/main/res/raw/reminder_beep.wav app/src/androidTest/assets/reminder-sounds/sample.mp3
ffmpeg -i app/src/main/res/raw/reminder_beep.wav -c:a libvorbis app/src/androidTest/assets/reminder-sounds/sample.ogg
ffmpeg -i app/src/main/res/raw/reminder_beep.wav -c:a aac app/src/androidTest/assets/reminder-sounds/sample.aac
ffmpeg -i app/src/main/res/raw/reminder_beep.wav -c:a aac app/src/androidTest/assets/reminder-sounds/sample.m4a
```

These use WAV PCM, MP3, OGG Vorbis, ADTS AAC and MP4 AAC containers. The device fixture
imports each, checks codec discovery and stable copies, tests deduplication, removes an
original/copy, and rejects corrupt data. It does not prove audibility under every DND or
OEM power state. Original presets and all transcoded test fixtures carry the same CC0 dedication.
