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
