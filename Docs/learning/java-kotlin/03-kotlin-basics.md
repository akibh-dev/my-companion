# 03 — Kotlin basics

[Previous](02-java-objects.md) · [Index](../README.md) · [Next](04-kotlin-modeling.md)

Run [Basics.kt](examples/kotlin/Basics.kt). It needs only Kotlin/JVM.

## Declarations

Java final int minutes = 5 becomes `val minutes: Int = 5`, usually inferred as `val minutes = 5`. Use var for reassignment; prefer val otherwise. A val referring to a mutable list does not freeze it.

`fun delayMillis(minutes: Int): Long` defines a function. Types follow colons; semicolons are usually unnecessary. Unit means no meaningful result. An expression body uses =; a block returning a result uses explicit return. Top-level functions need no artificial utility class. Default/named arguments clarify calls, but Java interoperability needs extra attention.

## Expressions and control flow

If and when produce values. When over a sealed hierarchy can require every case. Is checks a type; in checks membership. The range 0..5 includes endpoints; 0 until 5 excludes 5. For iterates ranges/collections; Kotlin has no Java-style three-part for syntax.

Arithmetic still needs care: 5 / 2 is 2, and 60_000L * minutes avoids Int intermediate overflow. Kotlin does not freely widen an Int variable for a Long parameter; convert deliberately with toLong(). Converting back may lose information.

## Nullability belongs to the type

String expects non-null text; String? allows absence. Ordinary unsafe calls on nullable references fail compilation.

| Syntax | Meaning |
|---|---|
| title?.trim() | Call when non-null; otherwise null |
| title ?: "Reminder" | Fallback for null, not for blanks |
| title?.length ?: 0 | Length or zero |
| text.toIntOrNull() | Parsed integer or null for invalid/out-of-range input |
| value as? String | Cast or null for incompatible type |
| require(condition) | Reject an invalid argument with an exception |

Blank text is non-null. Basics.kt handles null/empty/whitespace deliberately. The !! operator asserts non-null and throws if wrong; it is not validation.

After a null check, the compiler can smart-cast a stable local reference. A mutable property may change between reads, preventing that proof. Capture a local snapshot when it matches the desired behavior. Java platform types, assertions, unsafe casts, and initialization can still cause runtime problems. See [the null safety reference](https://kotlinlang.org/docs/null-safety.html).

## Strings, equality, and extensions

A template such as `"Hello $name"` inserts a value; `${expression}` inserts an expression. Strings are immutable. JVM string indices access UTF-16 code units, not necessarily complete visible characters.

Kotlin == uses null-safe structural equality; === checks reference identity. Arrays need contentEquals for element equality. Floating-point approximation still exists.

Extensions add receiver-call syntax without modifying a class or gaining private access. Dispatch uses the declared receiver type; matching members take precedence. Prefer named helpers over several nested scope functions when learning.

## Practice

1. Implement parseMinutes(text: String?): Int?: trim and accept 0..10080. Null, abc, -1, and 10081 return null; " 15 " returns 15.
2. Translate chapter 01's positive-sum loop, then use filter/sum. Both return 15.
3. Predict `null ?: "x"`, `"" ?: "x"`, and `"  ".ifBlank { "x" }`.
4. Explain why `val names = mutableListOf("Akib")` allows names.add("Friend").

[Answers](10-projects-answers.md#kotlin-basics-answers). Explain every question mark in Basics.kt before moving on.

References: [syntax](https://kotlinlang.org/docs/basic-syntax.html), [control flow](https://kotlinlang.org/docs/control-flow.html), [functions](https://kotlinlang.org/docs/functions.html).
