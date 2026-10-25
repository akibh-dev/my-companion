# Documentation Index

This directory contains learning notes, architectural decisions, and references built during development.

## Structure

```
Docs/
├── 00-index.md              # This file
├── 01-hello-world-explained.md   # Complete guide to minimal Android app
├── 02-quick-reference.md    # Essential facts & commands
├── 03-whatsapp-call-and-reminders.md  # How the two features work
├── 04-testing.md            # Test layers, tools and patterns
├── 05-youtube-player.md     # Background player, queue, playlists
├── architecture/            # Architectural decision records (ADRs)
│   ├── 001-mvvm-clean.md
│   ├── 002-hilt-vs-koin.md
│   └── 003-compose-vs-xml.md
├── learning/                # Step-by-step learning path ← START HERE
│   ├── README.md            # How to start + full 24-step path
│   └── 01-compose-state.md  # Step 01 guide + your learning note
├── references/              # Quick reference sheets
│   ├── coroutines-flow.md
│   ├── compose-recomposition.md
│   ├── lifecycle-cheatsheet.md
│   ├── permission-flows.md
│   └── gradle-tips.md
└── resources/               # Curated external links
    ├── official-docs.md
    ├── videos.md
    ├── articles.md
    └── sample-projects.md
```

## Current Project State

**Status**: ✅ People (one-tap WhatsApp calls), Reminders (custom notifications) and Player (YouTube background audio, queue, playlists), tested in CI

**What you have**:
- Compose app with two tabs, Hilt, Room, AlarmManager, notifications, home-screen shortcuts
- 133 JVM tests (unit, Robolectric, Compose UI) run on every push

**Start here**: [`learning/README.md`](learning/README.md) — setup checklist, the step loop, and the full path.

**Files to read first**:
1. `03-whatsapp-call-and-reminders.md` — What the features do and how
2. `05-youtube-player.md` — The YouTube player
3. `04-testing.md` — How the tests are organised
4. `01-hello-world-explained.md` — Basics of every file
5. `02-quick-reference.md` — Commands and version compatibility
6. `learning/01-compose-state.md` — Your first step

## How to Use This Documentation

1. **After each coding session**: Update relevant learning note
2. **When making a decision**: Add an ADR in `architecture/`
3. **When discovering a pattern**: Add to `references/`
4. **When finding a great resource**: Add to `resources/`

## Template for Learning Notes

```markdown
# Phase X: Feature Name

## What I Built
Brief description.

## Key Classes / APIs Used
- `ClassName` — purpose

## Problems Faced & Solutions
| Problem | Solution | Source |
|---------|----------|--------|
| ...     | ...      | Link   |

## Code Snippets to Remember
```kotlin
// Pattern worth remembering
```

## Questions for Later
- [ ] Question 1
```

## Template for ADR (Architecture Decision Record)

```markdown
# ADR XXX: Decision Title

## Status
Proposed / Accepted / Superseded

## Context
What problem are we solving?

## Decision
What did we decide?

## Consequences
### Positive
- ...

### Negative
- ...

## Alternatives Considered
- Option A — why rejected
```