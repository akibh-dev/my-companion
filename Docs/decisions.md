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

## Spikes (need the real phone)

| Spike | Result |
|-------|--------|
| S1 Wi-Fi control | not run |
| S2 Volume-key combination | not run |
| S3 Keep screen on over other apps | not run |
| S4 Reply through notifications | not run |
| S5 Missed call and SMS | not run |
| S6 Background survival | not run (heartbeat log is ready: Home → Log) |
| S7 Places and Wi-Fi range | not run |
| S8 Sideload permissions | not run |
| S9 Starting services from the background | not run |
| S10 Offline map | not run (map screen is ready; needs the PMTiles file) |
