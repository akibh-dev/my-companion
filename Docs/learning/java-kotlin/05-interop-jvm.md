# 05 — Java/Kotlin interoperability and the JVM

[Previous](04-kotlin-modeling.md) · [Index](../README.md) · [Next](06-coroutines-flow.md)

Java and Kotlin can coexist in one JVM project. Android framework APIs and many libraries expose Java-style contracts, so reading Java remains useful even when writing Kotlin.

## Run the mixed-language example

[LegacyTitles.java](examples/java/LegacyTitles.java) deliberately exposes an unannotated Java method that can return null. [Interop.kt](examples/kotlin/Interop.kt) handles its result explicitly. From the repository root, with javac and kotlinc installed:

```bash
mkdir -p /tmp/my-companion-learning/interop
javac --release 17 -d /tmp/my-companion-learning/interop Docs/learning/java-kotlin/examples/java/LegacyTitles.java
kotlinc Docs/learning/java-kotlin/examples/kotlin/Interop.kt -classpath /tmp/my-companion-learning/interop -include-runtime -d /tmp/my-companion-learning/interop/interop.jar
java -cp /tmp/my-companion-learning/interop/interop.jar:/tmp/my-companion-learning/interop InteropKt
```

On Windows use a semicolon instead of a colon between classpath entries. Compiling Kotlin against a class does not automatically package that Java class into the jar; runtime classpath still matters. Expected output: Reminder, Water, then the success line.

## Platform types are uncertain boundaries

Without useful Java nullability annotations, Kotlin may expose a platform type, shown in IDEs as String!. You cannot normally write that syntax as a Kotlin source type. It means the compiler lacks a reliable nullability contract. Assigning an unexpectedly null result to String or dereferencing it can fail.

At an uncertain boundary, declare a nullable local, validate it, and convert into a stronger internal model. Avoid pretending Java's absence of annotations proves non-null. Kotlin also does not force Java checked exceptions to be declared/caught: IOException may still happen even when the compiler does not demand a catch.

## Common translations

| Java API | Kotlin view |
|---|---|
| getTitle / setTitle | Often property syntax title |
| static method | Usually called on the Java class |
| Single abstract method interface | Often accepts a lambda |
| Primitive int | Usually Int, with representation depending on context |
| Integer/null | Int? when nullability is known |
| Java array | Kotlin Array<T> or primitive array such as IntArray |

Kotlin generic and nullable numeric values may use boxing on the JVM. Do not assume every Int has an identical memory representation in every context.

Kotlin top-level functions compile into generated file classes for Java calls. Default arguments do not automatically create Java overloads; @JvmOverloads can generate selected overloads. @JvmStatic exposes certain object/companion members as static-callable methods. @Throws can advertise exceptions to Java. Use these deliberately when designing an API consumed by Java rather than sprinkling them throughout Kotlin-only code. Suspend functions have a continuation-based JVM signature; ordinary Java callers do not see a simple blocking method.

## Memory and object lifetime

Variables may reference objects. Reassigning one reference does not copy the object. Garbage collection reclaims unreachable objects, not objects that merely stopped being useful. A singleton holding an Activity can keep its view tree reachable after the screen should be gone.

For Android dependencies that genuinely need application lifetime, use an application Context. UI operations may require an Activity Context instead. A WeakReference is not a substitute for deciding who owns a resource and when listeners/coroutines should stop.

Stack frames represent calls and their local execution state; heap objects may outlive a call. Treat this as a conceptual model rather than a promise about every compiler/runtime optimization. Files, sockets, and listeners still need explicit cleanup; garbage collection is not a timely close operation.

## Read failures by category

- NullPointerException: find the boundary where an absent value was treated as present.
- ClassNotFoundException/NoClassDefFoundError: inspect runtime classpath, packaging, and initialization causes.
- NoSuchMethodError: inspect incompatible runtime dependency versions; a source import change alone may not solve it.
- IndexOutOfBoundsException: inspect index and size at the failing line.

Read the first relevant application frame and the Caused by chain. Log a synthetic identifier or state where useful, not full private message contents.

## Practice and answers

Make LegacyTitles.title(2) return whitespace. Interop.kt should normalize it to Reminder after trimming. Add a nullability annotation only in a scratch project with its annotation dependency; inspect how IDE types change. Explain why specifying String? at the boundary is safer than inserting !!.

Reference: [calling Java from Kotlin](https://kotlinlang.org/docs/java-interop.html) and [calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html).
