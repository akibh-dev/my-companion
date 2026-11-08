# 01 — Java basics

[Previous](00-setup.md) · [Index](../README.md) · [Next](02-java-objects.md)

Run [Basics.java](examples/java/Basics.java). Predict output before running it.

## Values, types, and variables

`int minutes = 5;` declares a variable, its type, and starting value. `minutes = 10;` assigns another value. A final binding cannot be reassigned. Names should describe meaning and units: delayMillis is clearer than x.

| Type | Meaning | Trap |
|---|---|---|
| boolean | True or false | An integer is not a condition |
| int | Signed 32-bit integer | Intermediate arithmetic can overflow |
| long | Signed 64-bit integer | Assignment cannot repair earlier int overflow |
| double | Floating-point number | Decimal arithmetic may be approximate |
| char | UTF-16 code unit | Not necessarily an entire visible character |
| String | Immutable text object | Java == compares reference identity |

Other primitives include byte, short, and float. Epoch milliseconds usually need long. String is a reference type; trimming returns a result instead of changing the original string.

`5 / 2` returns 2; `5 / 2.0` returns 2.5. Percent returns a remainder. Inside main, compare:

```java
long correct = 60_000L * 40_000;
long overflowed = 60_000 * 40_000;
System.out.println(correct);
System.out.println(overflowed);
```

Output: 2400000000 then -1894967296. The L makes the first multiplication use long arithmetic. Ordinary integer overflow does not automatically throw. Investigate Math.multiplyExact when overflow must become a failure.

## Decisions and expressions

An expression produces a value, such as minutes >= 0. A statement performs a step. If chooses a branch. Operators &&, ||, and ! mean and, or, and not. And/or short-circuit: `title != null && !title.isBlank()` never calls isBlank on null. Reversing the order loses that protection.

Use switch for discrete choices and if for ranges. The conditional expression `condition ? yes : no` chooses a value. Avoid nesting it until a multi-step decision becomes unreadable.

Validate before effects. The example rejects negative delays before returning a value. Invalid programming contracts can throw IllegalArgumentException; invalid text entered by a user usually deserves a validation result and UI feedback.

## Methods and parameters

`static long delayMillis(int minutes)` accepts an integer and returns a long without requiring an object instance. Void means no result. Return ends the invocation.

Java is pass-by-value, including when the copied value is an object reference. A method can mutate the object reached through that reference. Reassigning its local parameter does not reassign the caller's variable.

Separate calculations from clocks, logs, files, and Android APIs. A pure function depends only on its arguments and has no observable side effects, making checks straightforward.

## Loops and arrays

Arrays have fixed lengths and zero-based indices; the last valid index is length - 1. A standard for loop controls initialization, condition, and update. Enhanced for visits values. While repeats while its condition is true; ensure the state can advance.

Rewrite the example's enhanced loop with an index. Deliberately use i <= delays.length: the final iteration throws an index error. Restore < and explain why every valid element was already processed.

Break exits a loop; continue skips the remainder of an iteration. Prefer small named functions over deeply nested loops hiding several separate decisions.

## Text input and parsing

Integer.parseInt("15") succeeds; parsing "fifteen" throws NumberFormatException. Read user input as a line and validate it. Scanner reads terminal input; System.console() may be null in IDEs. Closing a scanner around System.in also closes that stream, which matters if another reader needs it.

Null means an absent reference. It differs from zero, an empty string, and the text "null". Function contracts should state what values are accepted.

## Practice

1. Implement hoursToMinutes(int hours), accepting 0..168. Check 0, 2, -1, and 169.
2. Sum only positive values from {0, 5, 10, -1}. Expect 15.
3. Explain `"tea".equals(new String("tea"))` versus reference identity.
4. Predict 7 / 3, 7 % 3, and 7 / 3.0.

[Answers](10-projects-answers.md#java-basics-answers). Move on when you can explain declaration versus assignment, argument versus parameter, and correct loop bounds.

Reference: [Java language basics](https://dev.java/learn/language-basics/).
