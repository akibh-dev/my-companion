# Learn Java, Kotlin, and this Android app

Begin with [00 — Setup](java-kotlin/00-setup.md). No programming experience is assumed. Learn Java fundamentals, translate them into Kotlin, then use Kotlin to understand Android and this app. Already know Java? Answer chapters 01–02's checkpoints and begin chapter 03.

This is a practical foundation through an Android capstone, not a reference for every JVM API. Lessons include explanations, practice, checkpoints, and official references. Console examples use synthetic reminder data and never send messages, calls, or notifications.

## Read in order

| Chapter | Learn | Practice |
|---|---|---|
| [00 — Setup](java-kotlin/00-setup.md) | JDK, compiler, JVM, Gradle, Android runtime | Run Java and Kotlin |
| [01 — Java basics](java-kotlin/01-java-basics.md) | Types, variables, decisions, loops, functions | Validate reminder delays |
| [02 — Java objects](java-kotlin/02-java-objects.md) | Classes, interfaces, collections, generics, errors | Build a catalogue |
| [03 — Kotlin basics](java-kotlin/03-kotlin-basics.md) | Functions, null safety, ranges, equality | Translate Java logic |
| [04 — Kotlin modeling](java-kotlin/04-kotlin-modeling.md) | Data/sealed classes, lambdas, collections, generics | Model results and transform data |
| [05 — Interoperability and JVM](java-kotlin/05-interop-jvm.md) | Platform types, Java APIs, memory | Call Java from Kotlin |
| [06 — Coroutines and Flow](java-kotlin/06-coroutines-flow.md) | Suspension, cancellation, dispatchers, observable state | Collect updates and run concurrent work |
| [07 — Android and Compose](java-kotlin/07-android-compose.md) | Lifecycle, state, ViewModel, persistence | Build a reminder form |
| [08 — Repository tour](java-kotlin/08-repository-tour.md) | Clean layers, Hilt, Room, DataStore, services | Trace existing features |
| [09 — Testing and debugging](java-kotlin/09-testing-debugging.md) | Boundaries, fakes, coroutine tests, Git | Diagnose bugs |
| [10 — Projects and answers](java-kotlin/10-projects-answers.md) | Requirements and incremental implementation | Complete console and Android projects |
| [11 — Reference and study plan](java-kotlin/11-reference-study-plan.md) | Syntax comparison, glossary, resources | Follow a flexible 12-week plan |

Runnable sources are in [examples/](java-kotlin/examples/). Chapters distinguish console programs from Android integration snippets. Exercises include answers or acceptance criteria; the console capstone has complete source.

## How to study

Use 45–75 minute sessions: read a section, predict output, run it, change one input, and explain the result. Solve exercises before reading answers. Repeat a chapter when you cannot explain its checkpoint. Finished exercises matter more than dates.

Keep experiments in a separate learning project. Read this repo alongside the course; production changes belong on a feature branch with focused checks. App feature completeness and your learning progress are separate things.

## Repository baseline

This course follows main after PR #4 removed Private Vault and Quick Capture. People, Reminders, Player, context rules, messaging automation, voice mode, keep-screen-on, widgets, and export remain. AI implementations are placeholders.

The build uses compile/minimum/target SDK 36, Java/Kotlin bytecode target 17, and JDK 21 for Robolectric tests. Verify [app/build.gradle.kts](../../app/build.gradle.kts) and [the version catalog](../../gradle/libs.versions.toml) before changing tools. Historical documents may describe older SDKs and features.

Supporting material: [Compose state](01-compose-state.md), [testing](../04-testing.md), [reminders](../03-whatsapp-call-and-reminders.md), [player](../05-youtube-player.md), and [implementation guide](../implementation-guide.md). The [previous learning path](legacy-learning-path.md) remains explicitly historical.

Core references: [Dev.java](https://dev.java/learn/), [Kotlin documentation](https://kotlinlang.org/docs/home.html), and [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course).
