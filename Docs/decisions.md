# Decision log

Every spike result, model result and design change, with its date (plan §8).
IDs D1–D7 come from the implementation plan; later entries are numbered on.

| ID | Decision | Settled by | Status | Date / notes |
|----|----------|-----------|--------|--------------|
| D1 | Place detection method | Spike S7 | **Open** — built on `LocationManager` proximity alerts (no Google location) behind `PlaceDetector`, so S7 can swap it | 2026-10-25 |
| D2 | Wi-Fi control method | Spike S1 | **Open** — apps can't switch Wi-Fi since Android 10; the rule action shows the system Wi-Fi panel as a notification to tap (the plan's fallback) | 2026-10-25 |
| D3 | Vault database | Start of Phase 3 | **Decided: encrypted fields + in-memory search index** after unlock (no SQLCipher dependency; 500 items fit in memory) | 2026-10-25 |
| D4 | Offline map picker | You | Decided: MapLibre + local PMTiles file, plus current location and typed coordinates | plan |
| D5 | Speech-to-text and summarizer models | B5, B6 | Open — **dummy models** in `:ai-models` until the local models pass their tests | 2026-10-25 |
| D6 | Banglish approach | B2, B3 | Proposed: rules first, small model second. Dummy word list for now | plan |
| D7 | Bangla voice | B4 | Open — the phone's text-to-speech is used | plan |
| D8 | SDK levels | Plan §3 | compile/target/min SDK 36; AGP 8.13, Kotlin 2.2. Unit tests run on JDK 21 (Robolectric SDK 36) | 2026-10-25 |
| D9 | Modules | Plan §2 | `core`, `ai-api`, `ai-models`, `adapters-messaging`, `feature-*`; the older People / Reminders / Player code stays in `app` for now | 2026-10-25 |
| D10 | Model seam | Plan §2 | Features see only the four interfaces. `AiRouter` sends a call to the model when switched on in settings and it answers, otherwise to the fallback. Models load and release only through `ModelLoader` | 2026-10-25 |
| D12 | Vault file encryption | Phase 3 | Own chunked AES-GCM (64 KB chunks, each bound to its index and a "last" flag) with the vault's data key instead of adding Tink; the data key is wrapped by a Keystore key that needs fingerprint/PIN (30 s window) | 2026-10-25 |
| D13 | Parallel build-out | You | Phases 4–5 are built by three parallel workstreams from `Docs/implementation-guide.md`, integrated and tested centrally | 2026-10-25 |
| D14 | Typing bug #5 | Phase 5 | Text fields own their text in Compose `TextFieldState` (material3 1.3.2 has no state overload, so `BasicTextField` + outlined decoration); the `Main.immediate` workaround is gone | 2026-10-25 |
| D11 | Background work | Plan §3, S9 | One foreground service (`HostService`, type `specialUse`) hosts every always-on part; it runs only while a feature that needs it is on. Started at boot and from settings changes | 2026-10-25 |
| D15 | "App in use" trigger (FR-3.7) | Phase 1 gap, 2026-10-25 | **Decided: usage log** (`UsageStatsManager.queryEvents`, special access "Usage access") polled every 3 s, only while an enabled rule uses the trigger and the screen is on. An accessibility service would get window events instantly but means a second restricted-settings service just for this; the usage log needs one plain settings switch. Emulator: rule follows Chrome in 2 s | 2026-10-25 |
| D16 | Place detection shared | Handover leftover | `PlaceDetector.start(scope)` is reference-counted: one set of proximity alerts for context rules and keep-screen-on, handed over when one stops, removed with the last | 2026-10-25 |
| D17 | Camera URI grant | Android 17 warning | Photo capture grants the camera app our file explicitly (ClipData + read/write flags), because the implicit grant for `IMAGE_CAPTURE` ends with Android 18 | 2026-10-25 |
| D18 | Speed checks (NFR-6) | Phase 5 | Measured by `SpeedChecksTest` on a device, logged with tag `SpeedCheck`. Emulator: panel 440–634 ms, vault unlock 500 items ~70 ms, search ≤ 23 ms. Repeat on the phone | 2026-10-25 |

## Capture photo previews (2026-10-25)

Camera apps can store orientation in metadata instead of rotating image pixels. The capture
viewer now reads it with AndroidX ExifInterface and applies the horizontal flip followed by
clockwise rotation. This covers mirrored photos as well as portrait photos. Decoding stays on
the IO dispatcher and samples to at most 2048 pixels on the longest side before transforming,
so large camera files don't require a full-resolution bitmap. The original file stays untouched
for moving to the vault. Screenshots without metadata display as before.

Regression tests generate lossless images with all eight EXIF orientations and verify decoded
pixels, dimensions, and unchanged source bytes. Additional cases cover screenshots, large
images, and missing/corrupt files. Portrait and front-camera device checks remain pending.

Validation on the home laptop: `ktlintFormat ktlintCheck testDebugUnitTest lintDebug assembleDebug
--continue --max-workers=3` passed; 675 tests, no failures or skips, and no lint errors.
ADB reported no connected phone or emulator, so the phone-only spikes remain unverified.

Reference: [AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface).

## Spikes (need the real phone)

| Spike | Result |
|-------|--------|
| S1 Wi-Fi control | not run |
| S2 Volume-key combination | not run on the phone. Emulator can't inject volume keys (not even single presses change the volume), so this needs the physical buttons |
| S3 Keep screen on over other apps | not run |
| S4 Reply through notifications | not run |
| S5 Missed call and SMS | not run |
| S6 Background survival | not run (heartbeat log is ready: Home → Log) |
| S7 Places and Wi-Fi range | not run |
| S8 Sideload permissions | phone not run. **Emulator, Android 17, `adb install`:** the quick capture accessibility service and Usage access switched on without "Allow restricted settings" (2026-10-25). Check an APK installed from a file on the phone |
| S9 Starting services from the background | not run |
| S10 Offline map | not run (map screen is ready; needs the PMTiles file) |
