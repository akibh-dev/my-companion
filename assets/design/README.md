# My Companion — Minimal Design System

A distinct, monochrome, dot-grid visual language inspired by the restraint of Nothing OS, not an official Nothing product.

## Contents
- `DESIGN_GUIDE.md`: principles, tokens, accessibility, usage.
- `logo.svg`: original dotted M emblem, scalable and editable.
- `logo-dark.svg`: inverted emblem for dark backgrounds.
- `palette.svg`: reference swatches.
- `../../app/src/main/java/com/akib/mycompanion/ui/design/CompanionDesign.kt`: reusable Jetpack Compose components and palette.

## Adoption
The components are **opt-in**. Existing screens and theme remain unchanged until migrated. Use `CompanionMinimalTheme` around a screen to preview; use `CompanionTile`, `CompanionSectionTitle`, `CompanionStatusPill`, and `CompanionDotMark` inside it. Keep actual feature navigation and status wired to existing ViewModels. Never replace real functionality with the illustrative chat/tasks tiles from the visual concept.

The mark is original and should not be confused with Nothing's trademarks or typeface. Use system/Roboto fonts unless independently licensed fonts are added.
