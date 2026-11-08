# 04 — Kotlin modeling and collections

[Previous](03-kotlin-basics.md) · [Index](../README.md) · [Next](05-interop-jvm.md)

Run [Modeling.kt](examples/kotlin/Modeling.kt). It combines the concepts below in a complete program.

## Classes and properties

A primary constructor declares parameters after the class name. Adding val/var makes a constructor parameter a property. An init block validates construction. A computed property uses a getter rather than storing another value that could become inconsistent.

Classes are final by default. Use open only when subclassing is part of the contract. Interfaces describe behavior and may provide default method bodies. Constructor injection works exactly as in Java; annotations are not needed to understand it.

A data class generates equals, hashCode, toString, component functions, and copy using primary-constructor properties. Properties declared only in its body are excluded from these generated value operations. Copy is shallow: nested mutable objects remain shared. Destructuring uses component functions, not an arbitrary object's field names.

## Model alternatives explicitly

An enum represents a fixed set of named values, such as SoundMode.NORMAL or SILENT. A sealed interface represents alternatives that may carry different data. Modeling.kt uses Valid(reminder) and Invalid(reason). An exhaustive when forces callers to handle both.

A regular class instance stores per-instance state. An object declaration defines a singleton. A companion object groups class-associated members, but it is not simply identical to Java static declarations for interoperability purposes. A data object is useful for a state with no payload and generated value-style behavior.

Avoid flags that permit impossible combinations. A type with loading=true, error=true, and data=null leaves readers guessing. A sealed Loading/Ready/Failed model can prohibit ambiguous combinations when the UI needs mutually exclusive states. Independent concurrent conditions may still deserve separate fields.

## Functions are values

A parameter `(Reminder) -> Boolean` accepts a predicate. In `items.filter { it.enabled }`, the braces define a lambda and it names the single parameter. An explicit name helps when nested lambdas would make it ambiguous. A function reference such as String::trim passes an existing operation.

Higher-order functions receive or return functions. Extensions add convenient syntax. Keep helpers named for domain meaning rather than inventing abbreviations for every expression.

## Collection transformations

| Operation | Result |
|---|---|
| filter | Elements matching a predicate |
| map | One transformed value per input |
| mapNotNull | Transformed non-null results |
| flatMap | Flattened collections returned by a transform |
| any / all / none | Boolean condition across elements |
| firstOrNull | First match or null |
| associateBy | Map keyed by a selector; later duplicate keys replace earlier values |
| groupBy | Map from keys to lists |
| sortedBy | New ordered result |
| fold | Accumulated result starting from a seed |

List preserves order and permits duplicates; Set models uniqueness; Map associates unique keys to values. Read-only List is an interface, not proof of deep immutability. A caller holding a MutableList alias can still change the backing list. Use defensive snapshots at ownership boundaries where appropriate. This distinction is explained in [Kotlin collections](https://kotlinlang.org/docs/collections-overview.html).

Most collection transformations are eager. Sequences defer intermediate work until a terminal operation, which can reduce temporary collections for some pipelines. They are not automatically faster for small inputs. Neither sequences nor ordinary lists are coroutine Flow.

## Scope functions without confusion

| Function | Receiver in block | Returns | Typical use |
|---|---|---|---|
| let | it | Block result | Nullable transformation |
| run | this | Block result | Compute using a receiver |
| with | this | Block result | Group operations on an existing value |
| apply | this | Original object | Configure an object |
| also | it | Original object | Observe/log an intermediate value |

Choose one for a clear purpose. Do not nest several just to reduce lines. A normal local variable is often easier to debug.

## Generics and delegation

`Box<T>` can hold values of a caller-selected type. Kotlin declaration-site out means a type produces T; in means it consumes T. Invariant mutable containers cannot safely substitute subtype element containers. Star projection permits use when the actual argument is unknown, with restricted operations. Inline reified parameters allow certain runtime type operations otherwise unavailable through erased generic parameters; do not introduce them before there is a need.

Interface delegation with by forwards interface behavior to another instance. Property delegation uses a delegate to implement access, such as a lazy property. These are different mechanisms sharing a keyword. Lazy initialization does not by itself make the initialized object safe for concurrent mutation.

## Practice

1. Add InvalidTitle and InvalidDelay as separate sealed alternatives. The compiler should identify when expressions needing another case.
2. Add two reminders with the same ID, call associateBy, and explain which survives.
3. Put a MutableList inside a data class and copy it. Mutate the list to demonstrate shallow copying.
4. Group reminders by enabled and count each group. Expect two groups when both states exist.

[Answers](10-projects-answers.md#modeling-answers). Explain why a nullable return and a sealed error result express different amounts of information.

References: [data classes](https://kotlinlang.org/docs/data-classes.html), [sealed classes](https://kotlinlang.org/docs/sealed-classes.html), [generics](https://kotlinlang.org/docs/generics.html).
