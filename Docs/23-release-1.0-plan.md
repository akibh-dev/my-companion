# Plan 23: Release 1.0.0

**Status:** started 2026-11-23.

1.0.0 is the first production release: the Plan 19 fixes, a real signing key, a shrunk release build,
and the APK published on GitHub Releases. It stays a one-phone app (CMF Phone 1, Android 16), so the
release APK is **arm64-v8a only**, like every build.

## Decisions (2026-11-23)

| Question | Decision |
|---|---|
| Distribution | APK attached to a GitHub Release (`v1.0.0`). No F-Droid for now (see the end). |
| Version | `1.0.0`, versionCode `100` (major·100 + minor·10 + patch, so 1.0.1 = 101, 1.1.0 = 110). |
| Signing | A dedicated release keystore outside the repo, replacing the debug key. |
| ABI | arm64-v8a only (`-Pabis` stays for emulator builds). |

## Steps

Each step on its own branch and PR, each bug fix with a regression test.

1. **Correctness** — Plan 19 section 1 (`fix/plan19-correctness`).
2. **Crash visibility** — Plan 19 section 2 (`fix/crash-visibility`).
3. **Player access control** — Plan 19 section 3 (`fix/player-access-control`).
4. **Performance** — Plan 19 section 4, high-impact rows first: StreamInfo cache, ExoPlayer disk
   cache, voice model loading.
5. **Release hardening** — Plan 19 section 5:
   - Release keystore at `~/keystores/my-companion-release.jks`; its path and passwords live in
     `~/.gradle/gradle.properties` (`MYCOMPANION_RELEASE_STORE_FILE`, `…_STORE_PASSWORD`,
     `…_KEY_ALIAS`, `…_KEY_PASSWORD`), never in the repo. Without them (CI) the release build is
     signed with the debug key, so CI can still build it; only a locally built APK is published.
   - R8 minify + resource shrinking with real keep rules (NewPipeExtractor, Rhino, ONNX Runtime,
     Readium), checked on the phone.
   - Room schema export on for every database, schemas committed.
   - CI also runs `assembleRelease lintRelease`.
   - `AppLog` stops mirroring to logcat in release.
6. **Release** — version bump, `Docs/releases/1.0.0.md`, tag `v1.0.0`, `gh release create` with the
   signed APK and its SHA-256.

## Switching to the release key (once)

Android refuses an update signed with a different key, so moving off the debug key needs one
uninstall, which deletes the app's data. Only the settings backup can be restored (switches, voice
and auto-reply settings, Quran bookmarks, the library list); "Export my data" is a readable ZIP with
no import. Decided 2026-11-23: start fresh on 1.0.0 rather than copy the data over with adb.

1. Settings → Export & backup → Save settings…
2. Uninstall the old build, install the 1.0.0 APK.
3. Restore settings…, re-grant permissions, re-link book files; set up people, reminders,
   playlists, rules, places and reply templates again.

After that, every later release installs over it as an update.

## Updating from GitHub

Download the APK from the release page and install it. Or point
[Obtainium](https://github.com/ImranR98/Obtainium) at the repo to get update notifications from
GitHub Releases.

## Size

About 200 MB for the release build: Quran page fonts 122 MB (already compressed), voice models 45 MB,
ONNX Runtime 19 MB, PDFium 5 MB, code ~8–10 MB after R8 (66 MB unshrunk in debug). GitHub release
assets can be up to 2 GB, so this is fine.

## Later: F-Droid (not now)

Checked on 2026-11-23 and parked. What a submission would need:

- **Google Maps and Places are proprietary**: an `fdroid` flavor with OpenStreetMap (osmdroid) and
  Nominatim search for the place editor.
- **Assets built at build time**: the voice models and the Quran data are git-ignored and fetched by
  `tools/` scripts; F-Droid's build would have to run them (or the sources be vendored).
- **Anti-features** F-Droid would flag: NonFreeAssets (KFGQPC fonts are non-commercial only;
  Tanzil text may not be modified), NonFreeNet (YouTube), NonFreeDep (WhatsApp hand-off).
- **Size**: a personal F-Droid repo can't live on GitHub Pages (100 MB file limit); it would need
  S3/R2-style hosting, or the page fonts downloaded on first use instead of bundled.
- F-Droid signs its own builds, so its APK could not update one signed with our key.
