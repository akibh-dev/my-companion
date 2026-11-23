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

### First-run guide and permissions follow-up

The first-run guide now uses the approved display language: each page has a dotted hero card (the dotted wordmark on page one, a dot icon on the others), a title with the red brand period, and muted body copy. Page one lists the five features with dot icons instead of one long sentence. Pages swipe with a Compose `HorizontalPager`; the buttons animate the same pager. Progress is shown once, by the page dots (the active dot stretches into a pill), so there is no numeric counter. Skip jumps to the last page instead of closing the guide, so Start / Open permissions stays one tap away. On the last page Back and Start share a row and Open permissions takes the full width below, so its label never wraps. The decorative red period is excluded from accessibility text; screen readers hear the plain title as a heading.

When every requirement is granted, Permissions & health shows a "Ready to roll" card whose Let's go button returns to Home. It pops the back stack to Home rather than using the tab-switching helper, because that helper's saved state would restore the permissions screen. Source: [Compose pager](https://developer.android.com/develop/ui/compose/layouts/pager), [Navigation back stack](https://developer.android.com/guide/navigation/backstack).

Verified on the CMF Phone 1 (light mode): all four guide pages, swipe and Skip, and Let's go from 12/12 granted landing on Home. 358 app unit tests, debug lint and ktlint passed.

### Screen kit — 22 November 2026

Every screen is now built from the same few pieces in `design/.../ui/components/Layout.kt`, so the app reads as one thing:

- A screen is a column of **Sections**: a `SectionLabel` (small spaced capitals, a heading for TalkBack, read as written) over a `SectionCard` (the grey rounded surface), with an optional `Note` under it. No more mixed bold, caps and plain headings.
- On/off settings are `SwitchRow`s: the whole row toggles, one 48 dp target.
- Adding to a list is one `AddButton`, an ink pill floating at the bottom end (People, Reminders, Rules, Places, Library). The main action of a card or form is a full-width `PrimaryButton`; lesser actions are `SecondaryButton`s.
- Empty lists show an `EmptyState` (dot icon, what's missing, how to fill it). Lists that load from the database wait for the first answer, so the empty state doesn't flash on open.
- Fixes in the same pass: forms show errors only after Save is tapped (not on open); AI engines moved from Home to Settings → Developer; the Log is grouped by hour with readable rows; Keep screen on uses choice chips; the Quran reader's view and script choices are two separate pills, and its zoom pill floats over the page only while it is touched, fading out 1.5 s after (always shown with TalkBack); tappable rows inside cards are inset so the ripple's rounded corners don't clip text.

Verified on the CMF Phone 1 in light and dark: all 27 screens, the reminder sheet, the floating zoom pill (hidden → shown on scroll → hidden). 756 unit tests, lint and ktlint passed.

### Book reader controls — 22 November 2026

The reader's controls had no Surface behind them, so their text and icons fell back to black and almost vanished on a dark page; both bars also sat straight over running text. Now:

- Content colour is the page's ink (`LocalContentColor` = `onBackground`), in every page theme (Auto, Light, Sepia, Dark).
- Top: a round back button, the chapter (one line of small caps), and one pill of actions (contents, search, bookmark, look). Both pills are 56 dp with 4 dp around each 48 dp button, so a pressed button's circle is concentric with the pill's end.
- Bottom: one compact floating pill, like the Quran zoom pill: position · dotted strip · red-dot percentage. Tap the position to type a page ("Go to page"; "position" for EPUB).
- The strip: tap to jump, swipe to move through the whole book. After a seek it holds the new place until the book reports it, so it never flashes back first.
- The page dial, after the camera zoom wheel: push up from the strip, or press and hold, and a half dial rises above the pill. One tick per page, a numbered tick every ten, fading towards the ends. The page under the fixed red marker at the top (large, with its chapter) is where you land, so the finger turning the dial below never covers it. Ticks follow the finger like a wheel (right goes back); slow is page by page, a flick accelerates up to 8 pages per tick. A light haptic tick per page; release lands exactly on the marked page. TalkBack sets the position directly.
- Behind the bars, a fade from the page's own colour (black, white or sepia), so the bars never sit on text. The fades are plain backgrounds and take no touches.
- Bars are at most 640 dp wide and centred, for landscape.

Tried and dropped on the way: a slider that slowed as the finger rose, and a bell curve of dots rising under the finger. Both put the value under the fingertip; the dial keeps it above.

Checked on the CMF Phone 1: dark and light, paged and scroll layouts, portrait and landscape; the dial turned 43 → 46 and released exactly there.

## App icon: Buddy — 22 November 2026

The dotted "M" and its red full stop are replaced by **Buddy**, a dot face on the near-black
launcher background (`launcher_background` #0B0B0B): two round eyes, the right one red (#FF3B3B),
over a five-dot smile on an arc. Everything sits inside the 66 dp safe circle of the adaptive icon.

- Launcher: `ic_launcher_foreground` (white dots + red eye) and `ic_launcher_monochrome` (all seven
  dots in one colour, for Android's themed icons).
- Status bar: every notification the app posts uses the same face, drawn on a 24 dp grid with 2 dp
  of margin and slightly larger dots so the smile still reads at that size (`core` `ic_buddy`). This
  replaces the old M (reminders), gear (background features) and map pin (context rules), so the
  status bar always shows one recognisable mark for this app. The player notification gets it too by
  overriding Media3's `media3_notification_small_icon` resource by name.
- Status-bar icons are single colour (Android uses only the alpha), so the red eye is launcher-only.
- Launch animation: the splash screen's icon is `splash_buddy`, an animated vector of the same face on
  the black icon circle. The dots pop on, both eyes blink, then the right eye winks and opens red (under
  1 s, the splash screen's limit). `MainActivity` takes over the splash's exit so it waits for the wink
  to finish, then fades out, never holding the app back more than a second.
