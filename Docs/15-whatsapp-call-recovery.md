# WhatsApp direct-call recovery — 2026-11-08

Branch `feat/whatsapp-direct-call-recovery`, isolated worktree `/tmp/my-companion-whatsapp-calls`, based on main `ad2b30b` after Plan 13 merged.

## Implementation

The resolver now scopes Contacts provider lookup to the requested phone-book number, verifies full number identity and owning raw-contact account, refuses ambiguous rows, and explicitly targets the chosen WhatsApp package. Recognizable Bangladesh local-number normalization remains; arbitrary ten-digit suffix matching is removed. Lookup and target revalidation run on IO, with launch returning to the foreground caller context. Every explicit attempt resolves the latest app contact and current provider rows rather than storing provider IDs in shortcuts.

Typed results distinguish missing permission, denied/error provider access, missing phone-book matches, absent call rows, ambiguity, missing handlers, stale targets and denied launches. Both variants can be offered for a deliberate choice. A selected account is not silently switched after rejection, and failed chat launch is distinct from app absence. Successful intent submission means handoff only, not ringing or connection.

A token-protected invisible shortcut trampoline keeps successful paths brief. A separate nonexported visible host owns permission requests, permanent-denial settings recovery and variant selection. Saved state retains the app contact ID and consumed-operation state across recreation; it does not retain provider row IDs. People, shortcuts and reminder buttons use the same recovery mechanisms.

The debug-only nonexported diagnostic activity inspects a deliberately chosen app contact without calling any launch method. Reports contain version/permission/result counts, recognized account/MIME/package metadata and handler availability, but exclude names, numbers, JIDs, account names and provider IDs. Release builds exclude this facility.

## Compatibility and validation

Private WhatsApp account/MIME mappings are conservative compatibility candidates, not verified current-client guarantees. The CMF phone was not connected during this run. No real recipient was dialled and no ringing/connection claim is made. If the installed client exposes no supported call action, the correct result remains an explained chat fallback.

The existing Pixel 8 emulator was tried with default, software and lower-memory graphics settings; each process exited 139 during cold boot before ADB became available. Native fixtures can be compiled but device execution remains pending. Completed validation: **652 automated tests passed**, zero failures/errors/skips. `ktlintCheck`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `:app:assembleDebugAndroidTest` and `assembleRelease` passed, including release vital lint. Tests cover scoped provider selections and account ownership, international collisions/conflicting labels, ambiguity, package-specific launches, stale rows, lookup/launch failures, variant choice, saved-state recovery and diagnostic redaction. Device fixtures compiled but were not executed. Release APK inspection confirms the debug diagnostics are excluded.

## Phone acceptance procedure

1. Connect the CMF phone and verify the installed WhatsApp client versions and My Companion contacts permission.
2. Choose a known phone-book recipient whose system Contacts app shows a WhatsApp call action. Use the non-calling debug diagnostic first and record the sanitized result.
3. The owner deliberately taps Call through People, a pinned shortcut and a reminder, with permission both granted and revoked. Observe whether WhatsApp rings, requests confirmation, opens chat or rejects the action; a successful startActivity alone is insufficient.
4. Check both installed variants, a saved-number edit, a removed contact, sync changes and permission-host recreation. Do not remove the safe fallback to force an unsupported call.

Original requirements and official references: [Plan 14](14-whatsapp-direct-call-plan.md).
