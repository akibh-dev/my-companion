# Runnable course sources

Run commands from the repository root. Outputs belong in a scratch directory, not beside source. These programs do not depend on Android or modify app data.

| Source | Prerequisites | Entry point / purpose |
|---|---|---|
| java/Basics.java | JDK 17+ | Basics: arithmetic, loops, validation |
| java/Catalogue.java | JDK 17+ | Catalogue: interface, store, search, snapshots |
| java/FilesAndThreads.java | JDK 17+ | FilesAndThreads: temporary UTF-8 file and executor cleanup |
| java/LegacyTitles.java | JDK 17+ | Java library used by Interop.kt; no main |
| kotlin/Basics.kt | Kotlin/JVM compiler | BasicsKt: null safety and parsing |
| kotlin/Modeling.kt | Kotlin/JVM compiler | ModelingKt: data/sealed classes and transformations |
| kotlin/Interop.kt | Compiler plus compiled LegacyTitles | InteropKt: Java null boundary |
| kotlin/Async.kt | Compiler plus coroutines-core-jvm 1.10.2 | AsyncKt: ownership, flows, cancellation |
| kotlin/ReminderCli.kt | Kotlin 1.9+; course baseline 2.2.21 | ReminderCliKt: interactive catalogue; --self-test runs checks |

See [setup](../00-setup.md) for Java and basic Kotlin commands, [interop](../05-interop-jvm.md) for mixed compilation, [coroutines](../06-coroutines-flow.md) for the library classpath, and [capstone](../10-projects-answers.md#console-capstone-reminder-catalogue) for CLI commands.

Each independent Kotlin file has its own main; compile it separately. CLI data is in memory only. Self-check failures throw and cause a nonzero process exit. They are executable learning checks rather than a replacement for the app's JUnit/instrumentation suites.
