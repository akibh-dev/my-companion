# 02 — Java objects and APIs

[Previous](01-java-basics.md) · [Index](../README.md) · [Next](03-kotlin-basics.md)

Run [Catalogue.java](examples/java/Catalogue.java). It validates reminders, stores them in memory, filters results, and checks replacement by ID.

## Classes and constructors

A class describes state and operations; an object is an instance. A constructor establishes valid starting state. Private fields keep callers from bypassing every rule; public methods expose a deliberate contract. This refers to the current object.

The example's Reminder contains ID, title, and enabled flag. Its constructor validates and trims title. Final fields require replacing an instance to change these values. Final references alone do not make their referenced objects immutable. Java records reduce value-model boilerplate but are not deeply immutable when components reference mutable objects.

Instance methods operate on a particular instance; static members belong to the class. Static mutable collections can retain screens or leak state between tests. Avoid using them as convenient global app storage.

## Interfaces and composition

ReminderStore defines operations; MemoryReminderStore implements them. A caller can receive an interface through a constructor, then later use a disk implementation without rewriting business logic. That is dependency injection even without a framework.

Inheritance models an “is a” relationship. Composition gives an object collaborators. A reminder service has a store; it need not inherit from a store to reuse storage behavior. Overriding implements/replaces a superclass or interface method. Overloading uses the same name with different parameter lists.

## Equality and hashing

Java == compares primitive values or object identity. Equals can implement logical equality. Equal objects must have equal hash codes. Hash sets/maps depend on that contract. Do not mutate equality/hash fields while an object is a map key. The example uses stable numeric IDs as keys instead.

## Collections and generics

| Type | Contract | Example |
|---|---|---|
| List<Reminder> | Ordered, duplicates allowed | Display order |
| Set<Long> | Unique values | Selected IDs |
| Map<Long, Reminder> | One value per key | Lookup by ID |
| ArrayList | Resizable array | Fast indexed access |
| LinkedHashMap | Insertion-ordered map | Predictable lesson output |

Generics catch element-type mistakes during compilation. Use Integer rather than primitive int as a Java generic argument. Raw collections discard those checks. List<Integer> is not a subtype of List<Number>: otherwise a caller could insert a Double into an integer list.

Wildcards express limited operations. A producer typed ? extends Number allows reading as Number; a consumer typed ? super Integer accepts Integer values. Most generic arguments are erased at runtime: instanceof cannot establish that an arbitrary List contains only Strings.

ArrayList indexed access is constant time. Scanning titles is linear. Hash lookup is expected constant time with collision/implementation qualifications. Recognize repeated scans without optimizing before measuring.

## Lambdas and streams

A lambda such as `r -> r.enabled` can implement a functional interface. A stream pipeline filters/maps values; a terminal operation drives processing. A consumed Java Stream generally cannot be reused. It is not Kotlin's observable Flow. Begin with loops and use pipelines when clearer. Parallel streams are not an automatic Android performance fix.

## Exceptions, resources, and time

An exception interrupts normal flow and carries a stack trace. Catch when you can recover, translate, or add context while preserving the cause. Empty catches can make failed saves appear successful.

Checked Java exceptions must be handled or declared; unchecked ones do not have that compiler requirement. IOException is checked; IllegalArgumentException is unchecked. Catching Exception everywhere is not a recovery policy.

Specify file encoding, such as UTF-8. Files.readString reads a small file and manages its internal resource. Explicitly opened streams belong in try-with-resources to close on failures too. Use scratch files for exercises, never the real app database.

Instant represents a point on a timeline. LocalTime represents a wall-clock time without date or zone. “Tomorrow at 09:00” needs date and zone rules. Use java.time rather than arithmetic over display strings.

## Practice

Before moving on, review packages and visibility. A package groups names; an import lets source use a shorter name rather than copying code. Public exposes an API; private restricts access to the declaring class; package-private (no modifier) permits access inside a package. Protected permits same-package access and qualified subclass access with additional rules. Prefer the narrowest useful visibility. Kotlin's internal means module visibility and is not Java package-private.

Annotations attach metadata understood by compilers, tools, or runtime reflection according to their retention policy. Override lets the compiler verify an overriding declaration; it does not perform dependency injection. Hilt's annotations are processed by its tooling, which generates supporting code. Learn the ordinary constructors/interfaces before reading the generated graph.

For a complete standard-library example of UTF-8 files, resource cleanup, and Java concurrency, run [FilesAndThreads.java](examples/java/FilesAndThreads.java) after chapter 00's javac command:

```bash
java -cp /tmp/my-companion-learning/java FilesAndThreads
```

It uses a temporary file, closes its reader, submits a calculation to an ExecutorService, waits through a Future, shuts down the executor, and removes the temporary file. Future.get blocks: do not copy that wait into an Android main-thread event handler. Shared mutable state can race across threads; synchronization/atomic types or single ownership are deliberate solutions. Volatile visibility alone does not make read-modify-write operations atomic. Chapter 06 teaches the coroutine approach used by this app; coroutines still require care with shared state.

1. Change duplicate-ID behavior from replacement to rejection; check the exception.
2. Take all()'s snapshot, save another reminder, and verify the old snapshot size remains unchanged.
3. Search titles case-insensitively. Locale.ROOT is suitable for this locale-independent exercise; user-language matching may need a different policy.
4. Explain how an interface lets tests replace disk storage with memory.

[Answers](10-projects-answers.md#java-objects-answers).

References: [classes](https://dev.java/learn/classes-objects/), [collections](https://dev.java/learn/api/collections-framework/), [exceptions](https://dev.java/learn/exceptions/).
