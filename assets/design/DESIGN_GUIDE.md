# My Companion visual guide — v1

## Principles
1. **Useful first.** Show current state, next action, and failures clearly. Never sacrifice permission, pause, or service health warnings for decoration.
2. **Monochrome with intent.** Black, white, and neutral gray dominate. Red is a scarce emphasis, not a default button color.
3. **Quiet geometry.** 16–24 dp corner radii, clear spacing, flat cards, fine borders, restrained dot motifs.
4. **Accessible by default.** Text is never rendered in dot-matrix; dot art is decorative except the logo. Provide semantic labels, 48 dp touch targets, scalable text, and strong contrast.
5. **Android native.** Preserve system bars, back behavior, TalkBack, dynamic text size, reduced motion, and Compose state patterns.

## Tokens
| Token | Light | Dark | Usage |
|---|---|---|---|
| background | #F7F7F7 | #0B0B0B | app canvas |
| surface | #FFFFFF | #1A1A1A | tiles and sheets |
| onBackground | #101010 | #F5F5F5 | primary text |
| secondary | #616161 | #B5B5B5 | helper text |
| outline | #DEDEDE | #353535 | borders |
| accent | #FF3B3B | #FF3B3B | rare attention markers |
| danger | Material error | Material error | actual errors |

Spacing scale: 4, 8, 12, 16, 24, 32 dp. Screen gutters 16–20 dp; tile interior 20 dp; tiles 16 dp radius. Buttons/touch targets >=48 dp.

## Typography
- Display: bold system sans, 30–36 sp; avoid licensed proprietary dot fonts.
- Section: semibold system sans, 20–24 sp.
- Body: regular 14–16 sp; labels 12–14 sp.
- Dot motif is a *graphic*, never essential copy.

## Component inventory
- **DotMark**: a dotted M brand motif, decorative unless content description is set.
- **Tile**: compact feature card with title, subtitle, and optional click action.
- **SectionTitle**: readable section heading.
- **StatusPill**: short neutral state; do not communicate warnings by color alone.

## Screen migration
1. Start with Home, preserving pause switch, permissions warnings, enabled feature toggles, service heartbeat, and all routes.
2. Apply theme tokens, then introduce cards and headings.
3. Keep dark/light mode in sync with system. Dynamic Material You is currently used in the app; switching to this fixed palette should be an explicit user choice before app-wide adoption.
4. Validate at font scale 1.3+, TalkBack, narrow screens, and contrast in both themes.

## Logo
`logo.svg` is an original dotted M with a red terminal. Maintain 12% clear space. For adaptive icons, recreate within Android's safe zone and provide monochrome variant. Do not ship SVG directly as an Android launcher icon: convert to VectorDrawable or raster assets first.

## Preview concept vs actual app
The original concept board showed generic Chat, Tasks, Notes and Reminders. These are **visual placeholders**, not assertions about the current feature set. Existing features include voice, media, contacts, reminders, and automation utilities. Design should follow actual routes.
