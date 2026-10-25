# ADR 004: Modules and the model seam

**Status:** accepted (2026-10-25)

## Context
The implementation plan adds context rules, messaging (auto-reply, voice mode), an encrypted vault
and quick capture. Some parts are fragile (chat-app notifications change, Android limits
background work) and the AI models are built separately (Track B).

## Decision
```
app ──► feature-context ─┐
    ├─► feature-autoreply ─► adapters-messaging ─┐
    ├─► feature-voice ─────► adapters-messaging   ├─► core
    │                  └───► ai-api ─────────────┘
    ├─► feature-vault ──────────────────────────────► core
    ├─► feature-capture ───► ai-api
    └─► ai-models (dummies today) ──► ai-api
```
- **core**: settings (master pause, a switch per feature, AI engine switches), the on-device log,
  heartbeat, the rule reconciler, the service host and the permission registry.
- **ai-api**: the four interfaces (Transliterator, SpeechSynth, SpeechRecognizer, Summarizer), their
  built-in fallbacks, `AiRouter` and `ModelLoader`. No network.
- **ai-models**: model implementations. Today dummies that answer through the real interfaces and
  load/release like real models; each is replaced when its Track B model passes.
- Features contribute their screens, permissions (`Requirement`) and background parts
  (`HostedComponent`) to the app through Hilt multibindings, so the app shell lists them without
  knowing their insides.
- Shared Gradle settings for library modules live once in the root `build.gradle.kts`.

## Consequences
- A broken adapter or model can't take the rest down; a model can be switched off in settings.
- Each feature module owns its Room database file.
