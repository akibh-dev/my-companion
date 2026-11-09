# Approved monochrome UI

The user approved the temporary React prototype on 8 November 2026 after supplying a reference with dotted display lettering, monochrome cards and sparse red accents. This implements that direction in the native Compose app.

## Decisions

- Use fixed light/dark color schemes instead of wallpaper-derived dynamic colors. Dark background is `#0B0B0B`, cards `#1A1A1A`; light background is `#F7F7F7`, cards `#EDEDED`. Foreground and primary actions invert with the theme. Red `#FF3B3B` is reserved for brand details and active indicators. Errors retain readable error colors.
- Bundle Inter (regular, medium, semibold) for body copy, inputs and actions. Its license is included in `app/src/main/assets/licenses/inter.txt`.
- Draw the prototype's original dot alphabet using Compose Canvas for display headings. This is an approximation of the reference, not Nothing's proprietary font. Canvas headings expose normal text and heading semantics to TalkBack. Unsupported characters, including Bangla, fall back to ordinary text.
- Home uses a personal greeting and four feature cards. Quick switches remain independent of opening the feature; missed-call SMS, service health, permissions and the existing tools remain available below. Home's settings button opens the existing tools in a settings screen.
- Preserve the four destinations and back stack restoration. The navigation surface is a neutral translucent pill with an inverse active item. It respects system and keyboard insets. Translucency is used without adding a backdrop blur dependency.
- Apply the same theme to the existing feature modules and native form/dialog components. Keep reminder scheduling, sound import, voice, WhatsApp recovery and playback logic intact.
- People includes local name/number filtering. No contacts are sent anywhere.
- Replace the old speech-bubble launcher artwork with the approved dotted M, including the adaptive monochrome layer and a single-color notification icon. Widgets use fixed neutral day/night colors; Android still owns notification and Quick Settings templates.

The prototype's sample contacts, reminders and tracks are not inserted into the app. The app continues to use real stored data. Further layout and motion polish can follow without changing this foundation.

## Android concepts

MaterialTheme provides color, type and shape to all nested feature screens, keeping module boundaries intact. Canvas visuals need explicit semantics because drawn circles are not text nodes. Navigation keeps selectable tab semantics and a minimum 48 dp touch target; feature quick toggles expose a separate switch role. Bundled fonts work offline and Android supplies fallback glyphs for scripts outside Inter.

References: [Material 3 theming](https://developer.android.com/develop/ui/compose/designsystems/material3), [Compose drawing](https://developer.android.com/develop/ui/compose/graphics/draw/overview).

## Validation

Build, regression and emulator results are recorded with the PR. The temporary browser prototype is a review aid; production source is entirely Kotlin and Android resources.

### Local verification — 8 November 2026

- `:app:assembleDebug`: passed with the existing JDK 17 and cached dependencies.
- `:app:testDebugUnitTest`: 346 tests, zero failures/errors. New interaction checks cover accessible dot text with Bangla fallback, independent feature-card navigation/toggling, settings access, and contact filtering/calling callbacks. The navigation test now scrolls Home back to its header after restoring its prior list position.
- `:app:lintDebug`: passed, zero lint errors.
- `:app:ktlintCheck :feature-context:ktlintCheck`: passed.
- Installed an update on the connected CMF Phone 1 without clearing data and visually inspected the dark Home screen. The subsequent automated multi-screen check was incomplete when the phone screen became unavailable; do not count that check as passed. Scheduled system theme mode was restored.
- The user requested no PR while further updates are pending. Keep this work on the local `feat/nothing-inspired-ui` branch.

## Enhanced navigation, reminders and calling

The follow-up reference was the user's Nothing Photos screen recording. The bottom navigation now overlays the scrolling content instead of reserving a full-width strip. A Compose graphics layer records the destination content; a separate clipped layer draws and blurs the corresponding region behind the bar, with a neutral translucent tint and highlight. Only this background is blurred, so controls remain sharp. The bar contracts from 328 × 76 dp to 272 × 64 dp once its list leaves the top, and restores its size at the top. Compact tabs keep names and selected state in accessibility semantics. Each remains at least 48 dp high. Main lists include bottom scroll clearance, and the People add button stays above the bar. The bar is hidden while the keyboard is open.

NavHost enter/exit/pop transitions are explicitly disabled for immediate destination changes. The bar's 180 ms sizing motion is independent of navigation. Source: [Compose graphics layers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers).

Reminders starts with the upcoming list and a create action. Creation/editing uses a native expanded bottom sheet with a scrollable editor. Date, repeat and unit choices wrap onto lines instead of being clipped in horizontal rows. Sound selection is a dropdown. Dismissing a new draft keeps it; a successful save closes the editor, while an unsuccessful submission leaves it open. Source: [Compose bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets).

People provides distinct visible voice and video buttons. A call kind is passed through the use case, current Contacts-provider lookup, permission/account recovery, and saved state. Voice requests only accept voice MIME rows; video requests only accept video MIME rows. Matching still requires the full phone identity and an owned WhatsApp account. Provider row IDs are resolved and revalidated for each attempt, never saved in app contacts or shortcuts. Lookup/launch failures retain the chat fallback in the selected account. Existing pinned shortcuts and reminder call buttons remain voice actions.

On the CMF Phone 1 with WhatsApp 2.26.42.74, the suggested `call_action="voice"` extra on a `wa.me` link opened chat only. The actual WhatsApp-owned Contacts data actions opened direct voice and video calls to the explicitly authorized test recipient, confirmed by the user. The production app uses these contact actions, not the failed extra. No private WhatsApp application code, phone numbers, or device-specific provider IDs are embedded in the implementation.

A read-only device diagnostic accepts an explicitly supplied test number and reports only call-type readiness, counts and handler availability. The installed app reported one matching voice row and one matching video row, both ready. Hardware UI and in-app handoff results are recorded below.


### Verified call-permission repair and final checks — 9 November 2026

The installed WhatsApp manifest protects `com.whatsapp.accountsync.CallContactLandingActivity` with `android.permission.CALL_PHONE`. This explains the difference between the successful ADB test and the app's `SecurityException`/chat fallback. My Companion now declares that permission, checks whether the resolved installed handler requires it, and uses the visible permission recovery host before retrying the pending call. Contacts access and Phone access have separate persisted request state. Granting either resumes the chosen voice/video action once; settings and chat alternatives remain available. Telephony hardware is optional because the app's direct calls are WhatsApp handoffs.

The light-mode bar now uses a neutral glass tint with a white selected pill and dark icons. A separate 240 ms position animation moves the highlight while the destination page changes immediately. The controlled-clock regression test waits for layout frames before checking the intermediate highlight position.

- 356 app JVM/Robolectric/Compose tests passed; debug lint and ktlint passed.
- The opt-in `WhatsAppLiveCallDeviceTest` passed on the CMF Phone 1. It clicked the actual app Voice call and Video call buttons for the explicitly authorized recipient, handled the Phone permission request, observed WhatsApp's outgoing-call controls for both, and ended both calls.
- The same device test captured the light-mode People screen, reminder list/editor, and expanded/compact navigation. It sent no reminders or messages. The live test is skipped unless explicit recipient and real-call opt-in arguments are supplied.
- The earlier shell-only call checks are distinct from this final in-app verification.

The user requested a release APK/ZIP and a local branch commit, with no push or PR. Version 0.3.0 increments Android's version code to 3. The release variant preserves the repository's existing personal-app signing configuration for update compatibility; its certificate is the configured Android debug certificate, while the APK itself is a non-debuggable release build.


### Navigation follow-up — 0.3.1

Both expanded and compact navigation are icon-only. Accessibility names and selected-tab semantics remain. The selected pill retains its positional animation, while tab press ripple indication is disabled to remove the fade/pulse during switching. The glass tint, sizes, scroll contraction, and immediate page changes are unchanged.

Home refreshes the local hour when visible, on lifecycle restart/resume, and once per minute while open. Greetings remain morning from 05:00, afternoon from 12:00, and evening from 17:00. The timer stops while Home is not started, and picks up time/time-zone changes on the next refresh.
