# Documentation Index

This directory contains learning notes, architectural decisions, and references built during development.

## Structure

```
Docs/
├── 00-index.md              # This file
├── 01-hello-world-explained.md   # Complete guide to minimal Android app
├── 02-quick-reference.md    # Essential facts & commands
├── architecture/            # Architectural decision records (ADRs)
│   ├── 001-mvvm-clean.md
│   ├── 002-hilt-vs-koin.md
│   └── 003-compose-vs-xml.md
├── learning/                # Phase-by-phase learning notes
│   └── (add as you progress)
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

**Status**: ✅ Hello World app working on physical device

**What you have**:
- Minimal Android app (1 Activity, 1 Composable)
- Builds successfully with Gradle
- Runs on your phone via wireless debugging

**Files to read first**:
1. `01-hello-world-explained.md` — Understand every file
2. `02-quick-reference.md` — Commands and version compatibility

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