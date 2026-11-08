# 09 — Testing, debugging, and safe changes

[Previous](08-repository-tour.md) · [Index](../README.md) · [Next](10-projects-answers.md)

Tests express observable requirements. Good tests can survive an internal rewrite. A test that merely repeats the implementation's formula can repeat the same mistake.

## Choose the smallest useful scope

| Test | Proves | Does not prove |
|---|---|---|
| Plain JVM unit | Calculation/model behavior for supplied inputs | Android permission/device behavior |
| Robolectric | Android-dependent code under a simulated framework | Actual OEM background survival |
| Room/data integration | Queries, persistence/mapping behavior in its environment | Every production migration unless tested |
| Compose UI | Screen semantics and user interactions | All physical keyboard/accessibility/device conditions |
| Instrumented phone test | Behavior on that device/configuration | Several days of battery/soak behavior |

Use fakes when a small working substitute makes tests readable. MockK can verify interactions when those interactions are the contract. Mocking every private call couples tests to implementation. Inject clocks and repositories rather than sleeping until the time changes.

## Boundary-driven examples

For parseMinutes accepting 0..10080, check null, empty, spaces, malformed text, overflow text, -1, 0, 1, 10080, and 10081. A single happy-path check does not establish validation behavior.

For a “next reminder” algorithm, test before/exactly at/after the boundary, midnight, recurrence rules, and relevant date/zone transitions. Name each test after the scenario and expected outcome.

The console examples use check or explicit AssertionError to avoid a test-framework setup before you know the language. In production, place Kotlin tests under src/test with JUnit. For suspending code use kotlinx-coroutines-test/runTest when available in that module. Advance virtual time rather than relying on real sleeps. An infinite StateFlow collector needs cancellation or a test-owned background scope, otherwise completion can hang.

## Practical commands

From the repo root:

```bash
./gradlew :app:testDebugUnitTest --max-workers=2
./gradlew :feature-context:testDebugUnitTest --tests '*RulesEdgeCasesTest' --max-workers=2
./gradlew ktlintCheck
./gradlew lintDebug
./gradlew :app:assembleDebug
```

Use the narrow relevant test while iterating. Before a production PR, use the repository's full check:

```bash
./gradlew ktlintCheck testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --continue --max-workers=2
```

The test APK build compiles instrumented tests; it does not execute them. Executing connected tests requires a selected authorized device and an installation compatible with its app ID/signing key. Reports live under each module's build/reports; test XML lives under build/test-results. A green old CI run does not validate later edits.

## Debug in a repeatable order

1. Write exact reproduction inputs, expected outcome, actual outcome, and app version.
2. Reproduce with synthetic data. Reduce to the smallest failing case.
3. Read the first useful error and its cause chain; later failures may be consequences.
4. Set a breakpoint before the wrong state appears. Inspect arguments, branches, and actual return values.
5. Change one cause. Add a meaningful regression test when the behavior warrants it.
6. Run the relevant check, then the required wider checks. Record remaining device limitations honestly.

Compilation error? Read types/imports and declarations. Dependency resolution error? Check coordinates, repository rules, network, and Gradle JDK. Runtime error? Read the stack trace and input. Wrong result? Compare intermediate values with a hand-calculated example. UI not updating? Check state ownership, mutation, collection lifetime, and equality/conflation before adding forced refreshes.

## Git workflow

```bash
git status --short --branch
git switch -c feat/learning-reminder-validation
git diff --check
git diff
```

Review only intended files before staging. Do not mix generated files, credentials, or unrelated edits into a lesson. Follow the repo's owner identity and conventional commit rules; do not override authorship. Open a PR for main and inspect its actual checks.

Do not use reset/clean as a routine error-recovery tool: those commands can discard work. Restore from a known state only when the owner deliberately wants that result. The current docs branch does not need an app reinstall.

## Exercises and answers

Introduce int multiplication overflow into a scratch copy of Java Basics. A long-delay check should fail; using a long operand before multiplication fixes the cause. Remove trim from Kotlin parseMinutes: the surrounding-whitespace case should fail. Add a StateFlow update without a collector and explain why value changes but no UI exists to display it.

Finish when you can distinguish a skipped test, compilation-only check, passing test, and manual observation. Each is different evidence.

References: [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals), [coroutine testing](https://developer.android.com/kotlin/coroutines/test), [project testing guide](../../04-testing.md).
