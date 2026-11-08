# Diagnose and repair WhatsApp direct voice calls

Planned: 2026-11-07. Status: proposal; production code has not changed.

## Finding

The app already implements the contact-row technique in the supplied proposal. Opening chat means the direct-call lookup or launch failed, or WhatsApp accepted the intent without starting a call. The precise cause on the phone is still unknown. Adding another shortcut activity would duplicate existing functionality.

The actual build is Kotlin/Compose/Hilt with min/target SDK 36 (Android 16), per `app/build.gradle.kts` and decision D8. Use the current CMF Phone 1 target when implementing and validating this work.

| Existing piece | Current behavior | Gap |
| --- | --- | --- |
| `data/whatsapp/AndroidWhatsAppGateway.kt` | Queries `ContactsContract.Data` for voice MIME types, matches DATA1/DATA3, launches ACTION_VIEW with the row URI and MIME | Call intent has no package; lookup returns the first match without checking account ownership; null/false hide failure reasons |
| `domain/usecase/CallContactUseCase.kt` | Tries direct calling, then opens `https://wa.me/<digits>` | Every failed lookup/launch produces the same fallback; failed chat launch is reported as WhatsApp missing even when installed |
| `domain/whatsapp/WhatsAppNumberMatcher.kt` | Matches a JID or label, including the last ten digits | Different international numbers can share that suffix; label parsing is fragile |
| `presentation/contacts/ContactsRoute.kt` | Requests READ_CONTACTS when Call is tapped | Pending contact uses `remember`, so recreation can lose the pending action |
| `presentation/call/CallActivity.kt` | Token-protected invisible activity used by pinned shortcuts and reminder actions | Does not request permission; missing/revoked permission goes directly to chat |
| `presentation/shortcuts/HomeShortcuts.kt` | Stores the app's contact ID, loads the latest number through the repository, and resolves the WhatsApp row at tap time | Already avoids persisting WhatsApp data-row IDs; retain this design |
| `AndroidManifest.xml` | Declares READ_CONTACTS and visibility for `com.whatsapp` and `com.whatsapp.w4b` | Required declarations already exist |

Saving a person inside My Companion only saves the app's contact. It does not create a phone-book entry or make WhatsApp sync one. Likewise, choosing a phone number with the system picker does not give blanket access to sibling WhatsApp call rows.

## What the research establishes

Android documents custom MIME rows in the Contacts provider and the READ_CONTACTS requirement. That supports the mechanism, but does not guarantee WhatsApp's private row schema or current calling behavior. Treat the supplied voice/video MIME strings and this repository's Business MIME assumption as compatibility candidates to verify on the installed client. Do not assume the package name determines the MIME string. [Android Contacts provider](https://developer.android.com/identity/providers/contacts-provider).

`Intent.setPackage()` restricts intent resolution to the chosen app; it does not guarantee that the recipient starts a call or bypass its confirmation UI. Its absence is a concrete implementation gap, not a proven explanation for this phone's fallback. [Android Intent reference](https://developer.android.com/reference/android/content/Intent#setPackage(java.lang.String)).

The existing package declarations and pinned-shortcut APIs follow Android's documented patterns. Keep them. [Package visibility](https://developer.android.com/training/package-visibility/declaring), [creating pinned shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts).

Earlier reports (`06-device-test-report.md`, `07-feature-test-report.md`) used synthetic contact rows and `tools/device-test/fake-whatsapp`. They verify intent construction/routing, not calls through real WhatsApp. During this review, ADB exposed only `emulator-5554`, with neither WhatsApp package installed. No real call was attempted, and existing tests were reviewed rather than rerun for this documentation-only change.

## Implementation sequence

### 1. Identify the failure before changing compatibility assumptions

- On the phone, record Android/app/WhatsApp versions, installed variants, and My Companion's contacts-permission state.
- For one chosen phone-book contact, check whether the system Contacts app exposes a WhatsApp voice-call action and whether a user tap starts the intended call.
- Add a debug-only lookup diagnostic for that chosen contact: permission, matched-row count, account type, MIME type, selected package, and launch outcome. Keep phone numbers, names, JIDs, account names and full provider dumps out of logs.
- Distinguish permission missing, provider access denied/error, no phone-book match, no WhatsApp voice row, ambiguous match, no activity handler, and launch denied. Successful `startActivity` only establishes handoff; WhatsApp can still open a different screen.
- Use this evidence to confirm the actual account/MIME combinations. A missing row requires contact/sync setup or a fallback; changing the intent cannot create a usable WhatsApp-owned row.

Deliverable: a phone-specific failure reason and sanitized account/MIME observations, recorded alongside the tested client version.

### 2. Repair resolution and launch

- Extend the pure Kotlin target model with the selected app variant/package identity. Keep Android provider/URI types in the data layer.
- Resolve phone-book candidates for the requested normalized number, then inspect their call data rows and owning raw-contact accounts. `PhoneLookup` can narrow candidates, but aggregated contacts may contain several numbers: still verify the call row's number identity.
- Read account metadata through `RawContacts`, linked by `Data.RAW_CONTACT_ID`; use parameterized provider queries. Build an explicit allowlist of verified account/MIME/package combinations. Avoid a whole-address-book scan where scoped lookup works.
- Prefer exact normalized full-number matches. Apply the existing Bangladesh normalization policy only to recognizable local phone-number forms. Do not treat arbitrary JIDs or label text as phone numbers; use a label only if its observed format can be parsed and normalized unambiguously. Remove the unconditional ten-digit suffix match. Refuse conflicting candidates rather than choosing the first cursor row.
- When both WhatsApp apps have valid targets, let the user choose the app for that attempt. When only one has a valid target, use it. Do not silently switch accounts after a failed call attempt.
- Launch ACTION_VIEW with `ContentUris.withAppendedId(Data.CONTENT_URI, rowId)`, the verified MIME type, `.setPackage(selectedPackage)`, and the required new-task flag. Query on the IO dispatcher and hand off the launch through the foreground presentation flow.
- Re-resolve on every explicit call attempt; retain no provider row ID in Room or a pinned intent. Handle a row disappearing between lookup and launch without crashing or repeatedly starting activities.
- Preserve safe chat fallback. Prefer the selected app when known, and report chat-launch failure separately from package absence. Do not claim a connected/ringing call solely because Android accepted the intent.

Primary files: gateway, domain ports/results, number matcher, use case, and their fakes/tests. This scope needs no Room migration or new dependency unless device evidence reveals a requirement.

### 3. Make every entry point explain and recover

- Share the permission/result flow across People, pinned shortcuts and reminder call actions. A shortcut or reminder tapped without READ_CONTACTS should route to a visible explanation/permission host, then resume the chosen action once if granted.
- Keep the normal successful shortcut path invisible. The current `noHistory` trampoline should not own a permission dialog without lifecycle changes; use a visible host for permission requests instead.
- Preserve the pending app contact ID across recreation with saved state and consume it once. Denial must remain usable: open chat with a truthful explanation and offer settings recovery after permanent denial, without repeated prompts.
- Carry a sealed fallback reason into `CallMessages`: contacts access needed, number missing from the phone book, WhatsApp call entry unavailable, ambiguous number, or direct-call launch rejected. Keep messages readable and provide the relevant next action.
- Preserve the existing shortcut token check, removed-contact handling, notification behavior and deferred snackbar delivery. Editing a saved number must affect the next shortcut tap.
- Adjust UI/docs that currently promise a call for every saved contact. Explain that direct calling depends on an available WhatsApp contact action; chat remains the fallback.

Primary files: `ContactsRoute`, `ContactsViewModel`/`CallMessages`, `CallActivity`, shared permission host/navigation, and existing shortcut/activity tests.

### 4. Validate behavior, then update the feature documentation

| Check | Expected result |
| --- | --- |
| Exact JID/full-number match; recognized local Bangladesh number | Correct target; no broad suffix-based matches |
| Two international numbers with the same last ten digits; multi-number aggregate | Never call the wrong person; unresolved ambiguity falls back |
| Regular only, Business only, both installed | Verified matching package receives the intent; both valid targets offer a choice |
| Permission denied, permanently denied, revoked after shortcut pinning | Useful explanation/fallback from all three entry points; no crash or prompt loop |
| Missing row, null/provider-error cursor, read denied | Distinct lookup result; usable fallback |
| Activity missing, launch SecurityException, removed/stale row | Distinct launch result; no unhandled exception or repeated automatic attempt |
| Rotation/recreation while permission UI is open | Pending contact survives; at most one launch |
| Number edited, contact deleted, provider IDs changed after sync | Shortcut uses current number/current row, or explains removal |
| WhatsApp accepts intent but opens chat/confirmation | Report handoff accurately; document observed client behavior |

Extend the existing domain, Robolectric gateway, activity, ViewModel and shortcut tests. Make the fake provider enforce the intended selection/projection and account mapping so a test cannot pass by ignoring query semantics. Add failure injection and assert the exact package/URI/MIME combination. The fake APK remains useful only for routing regression tests.

Run `ktlintCheck`, `testDebugUnitTest`, `lintDebug` and `assembleDebug` with the project's JDK setup after implementation. Then validate People, a pinned shortcut, and a reminder action on the CMF phone with real WhatsApp and a deliberately selected test recipient. Start with the non-calling diagnostics; the user should initiate the live call checks knowing they can ring the recipient. Record client version and whether each handoff produces ringing, confirmation, or chat.

Acceptance: direct voice calling is demonstrated on the target phone where WhatsApp exposes a working call action; every unavailable/denied path explains the fallback; shortcuts survive contact edits and sync; no ambiguous match can initiate a call. If the installed WhatsApp exposes no working action, record the incompatibility and retain chat fallback rather than marking direct calling fixed.

After validation, update `03-whatsapp-call-and-reminders.md` and append current real-client evidence to the device report. Video calling is a separate follow-up after voice compatibility is established; the pasted video MIME alone is insufficient validation.
