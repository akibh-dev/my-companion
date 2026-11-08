# 06 — Coroutines and Flow

[Previous](05-interop-jvm.md) · [Index](../README.md) · [Next](07-android-compose.md)

Goal: understand ownership and cancellation before adding asynchronous work to a screen. Run [Async.kt](examples/kotlin/Async.kt), then trace the flow from parent to children.

## Suspension is not a background thread

A coroutine is a suspendable computation that executes on threads. Suspend allows a function to call other suspending operations; it does not automatically move work off the UI thread. Delay suspends without blocking its thread; Thread.sleep blocks. A CPU loop inside a suspend function still occupies its thread until it finishes or cooperatively suspends/checks cancellation. See [coroutine basics](https://kotlinlang.org/docs/coroutines-basics.html).

On Android, blocking disk/network work needs an appropriate dispatcher, commonly IO. CPU-heavy transformations commonly use Default. Main is for UI work; the console example has no Android Main dispatcher. Libraries such as Room may already provide main-safe suspending APIs; do not wrap every suspend call in IO by habit.

## Ownership: scopes and jobs

Launch starts work and returns a Job. Async starts work producing a Deferred value; await gets its result. Calling two async builders before awaiting allows their work to overlap. This is concurrency; parallel execution depends on dispatcher and workload.

CoroutineScope establishes a lifetime. Structured concurrency makes child work belong to a parent. A parent scope normally waits for its children, and cancellation propagates. A failing child normally cancels its siblings under a regular parent; supervisorScope changes that relationship but does not automatically handle every child's exception.

Use viewModelScope for work belonging to a ViewModel. Use lifecycle-aware collection for visible UI. Long-lived application work needs explicit app ownership. Avoid GlobalScope: it makes cancellation, errors, and tests harder to reason about. RunBlocking is useful as a console/test bridge but blocks its caller and belongs outside Android UI event handlers.

## Cancellation is cooperative

Cancellation usually appears as CancellationException at suspending points. Do not swallow it in a broad catch. Rethrow it before translating expected IO/domain failures. A tight CPU loop should periodically check ensureActive or otherwise cooperate. Finally is useful for cleanup, but cleanup requiring suspension needs additional deliberate handling; do not start unrelated work while shutting down.

A timeout bounds how long a coroutine waits. It does not prove the underlying external operation has no side effect. Retrying a send or save requires an idempotency policy, not just another launch.

## Flow: values over time

A cold flow normally starts its producer for each collection. Map transforms emissions; filter selects them; collect consumes them. Ordinary flow operators are sequential unless an operator introduces concurrency. Flow is not a list waiting to be returned.

StateFlow is hot state with a current value; equal values are conflated. Slow collectors can skip intermediate state updates. SharedFlow can broadcast emissions with configurable replay/buffering, but it is not durable event storage. Emitting an important event while nobody listens requires a deliberate delivery model.

Combine uses the latest values after each source has emitted at least once. FlatMapLatest switches to the newest inner flow and cancels the previous one. CollectLatest cancels previous collector work on a new value. Use cancellation only when abandoned work is actually safe to abandon.

StateIn converts a flow to a StateFlow tied to a scope and sharing policy. SharingStarted.WhileSubscribed can stop upstream collection after subscribers disappear, using its configured timeout. It does not persist state through process death.

MutableStateFlow.update applies atomic transformations. `value = value.copy(...)` can lose concurrent changes if several writers race. A val holding MutableStateFlow does not make its state immutable; expose a read-only StateFlow to consumers.

## Run the example

Use the same coroutines version as this repo (currently 1.10.2). In a separate Kotlin/JVM project, add implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") to its dependencies and run Async.kt. The Android project already declares coroutines; do not edit its dependencies for this lesson.

For the standalone compiler, download the matching core-jvm jar from its official Maven Central coordinates, or use your learning project's resolved artifact. Set its absolute path:

```bash
export COROUTINES_JAR=/absolute/path/to/kotlinx-coroutines-core-jvm-1.10.2.jar
mkdir -p /tmp/my-companion-learning/async
kotlinc Docs/learning/java-kotlin/examples/kotlin/Async.kt -classpath "$COROUTINES_JAR" -include-runtime -d /tmp/my-companion-learning/async/async.jar
java -cp "/tmp/my-companion-learning/async/async.jar:$COROUTINES_JAR" AsyncKt
```

The path is an instruction to replace with your actual artifact path, not a file shipped by this repo. The example checks combined results, three cold-flow values, an atomic counter, and child cancellation without timing-performance assertions.

## Practice

1. Remove one cold-flow collection. Predict which producer runs stop happening.
2. Replace delay with Thread.sleep in a scratch example. Observe the blocked thread, then restore delay.
3. Cancel the child and explain why cancelAndJoin waits for its finally block.
4. Explain why StateFlow is suitable for the latest settings but not a guaranteed record of every button press.

Answers: a cold producer runs per collector; blocking occupies the thread; cancellation completion includes child cleanup; state conflation and subscriber lifetime differ from durable event delivery.

References: [coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html), [Flow API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/).
