# 11 — Quick reference and study plan

[Previous](10-projects-answers.md) · [Index](../README.md)

## Java → Kotlin translation sheet

| Java | Kotlin | Remember |
|---|---|---|
| final String name | val name: String | Binding immutability is not deep immutability |
| int count | var count: Int | Prefer val unless reassignment is needed |
| null-capable reference | String? | Java annotations/platform types affect inference |
| String.equals | == | Kotlin === means identity |
| new Reminder(...) | Reminder(...) | Kotlin omits new |
| static utility method | Top-level function | Java sees generated file-class members |
| getter/setter | Property | Backing storage is not required for every property |
| ternary condition | if expression | Both branches can return a value |
| instanceof | is | Smart casts need stable values |
| switch over types | when | Sealed models can enable exhaustive handling |
| List<T> | List<T> / MutableList<T> | Read-only is not necessarily immutable |
| void | Unit | Interoperability has representation details |
| constructor overloads | Default/named arguments | Java callers do not automatically get defaults |
| try-with-resources | use | Only where the resource type/API supports it |
| stream pipeline | Collection/Sequence pipeline | Neither is automatically Flow |

## Glossary

| Term | Plain meaning |
|---|---|
| API | Contract callers use to interact with code |
| Argument | Actual value passed to a call |
| Parameter | Named input in a declaration |
| Expression | Code producing a value |
| Side effect | Observable change outside a function's returned result |
| Encapsulation | Keeping internal state behind deliberate operations |
| Interface | Contract that multiple implementations can fulfill |
| Generic | Type/function parameterized by another type |
| Nullable | Permits an absent value |
| Immutable | Cannot change; clarify whether this includes nested values |
| Coroutine | Suspendable computation with an ownership context |
| Dispatcher | Policy for where coroutine work executes |
| Flow | Asynchronous values over time |
| State | Current condition needed to render/decide behavior |
| Event | Something that happened; delivery requirements vary |
| Repository | Access boundary for application data |
| Dependency injection | Passing collaborators rather than constructing them internally |
| Recomposition | Re-executing relevant Compose functions as state changes |
| Lifecycle | Framework-managed component transitions/lifetime |
| Migration | Deliberate transformation between persisted schema versions |
| Regression | Previously correct behavior broken by a change |
| Idempotent | Repeating an operation has the same intended effect as doing it once |

## Flexible 12-week route

Assume four or five sessions a week, about an hour each. Repeat weeks as needed; this is a suggested pace, not a deadline.

| Week | Material | Completion evidence |
|---|---|---|
| 1 | Setup, Java variables/decisions | Run programs and explain overflow |
| 2 | Java methods/loops/input | Solve boundary exercises |
| 3 | Objects, interfaces, collections | Explain the Java catalogue without notes |
| 4 | Kotlin basics/nullability | Translate parser and calculations |
| 5 | Data/sealed classes and collections | Model validation and explain shallow copy |
| 6 | Interop and JVM | Run mixed-language example; explain null boundary |
| 7 | Coroutines and Flow | Explain cancellation and hot/cold flows |
| 8 | Compose and ViewModel | Form validates correctly and survives rotation |
| 9 | Persistence and architecture | Trace reminder and settings flows |
| 10 | Testing/debugging | Introduce and catch a meaningful regression |
| 11 | Console capstone | Rebuild behavior independently and pass checks |
| 12 | Android capstone increments | Demonstrate persistence/state ownership and explain limits |

If Android is your primary goal, do not wait to master every Java library before learning Kotlin. Keep Java fundamentals and interoperability, then spend most implementation time in Kotlin. Deep reflection, compiler plugins, advanced lock-free algorithms, and backend frameworks are follow-on topics, not prerequisites for understanding this app.

## Official reference library

- [Dev.java tutorials](https://dev.java/learn/): language, objects, collections, tools, and JVM topics.
- [Java 17 API reference](https://docs.oracle.com/en/java/javase/17/docs/api/): lookup matching this course's baseline.
- [Kotlin language documentation](https://kotlinlang.org/docs/home.html): language and standard library guidance.
- [Kotlin null safety](https://kotlinlang.org/docs/null-safety.html).
- [Kotlin collections](https://kotlinlang.org/docs/collections-overview.html).
- [Java interoperability](https://kotlinlang.org/docs/java-interop.html).
- [Coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html).
- [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course).
- [Android architecture](https://developer.android.com/topic/architecture/recommendations).
- [Android testing](https://developer.android.com/training/testing/fundamentals).

Language syntax and standard-library reference are different from Android API availability. Before using a newer Java API in Android, check platform support/desugaring; a desktop sample working on JDK 17 does not establish device availability. Use this repository's version catalog as the dependency baseline rather than copying versions from arbitrary tutorials.

## Personal learning log

Copy this template into your own notes for each session:

```text
Date:
Chapter and exercise:
What I predicted:
What happened:
What I changed:
Why the result changed:
One explanation in my own words:
A question I still have:
Next small task:
```

Revisit yesterday's concept before starting new material. Once a week, implement a small calculation or parser from a blank file. Being able to explain and reproduce a solution is stronger evidence than recognizing it while reading.
