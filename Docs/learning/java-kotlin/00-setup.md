# 00 — Setup and how programs run

[Index](../README.md) · [Next](01-java-basics.md)

Goal: distinguish language, compiler, runtime, and framework, then run examples without modifying the app.

## The tools

| Term | Meaning |
|---|---|
| Java | Language and platform with standard libraries |
| Kotlin | Language that can use Java APIs; this course uses Kotlin/JVM |
| JDK | Development kit containing java, javac, and tools |
| JVM | Runtime executing class-file bytecode on your computer |
| Compiler | Translates source into another representation |
| Gradle | Builds, resolves dependencies, runs tasks |
| Wrapper | Repo's gradlew and its pinned Gradle distribution |
| Android SDK | Android APIs and build/device tools |
| ART | Android runtime; builds convert JVM class files into DEX |
| Compose | Kotlin UI toolkit and compiler support |

Console Java: source → javac → class files → java → output. Android: source → class files → Android build tools → DEX/resources inside an APK → installation → Android runtime. The desktop JDK is a build tool, not something installed onto the phone.

## Environment

From the repository root:

```bash
java -version
javac -version
./gradlew --version
git status --short --branch
```

This project targets Java 17 bytecode. Robolectric tests use a JDK 21 toolchain. Gradle's runtime and test runtime can differ. Android SDK 36 is another version dimension. Do not force all these numbers to match.

This machine has a JDK at `/home/akib/.jdks/corretto-17.0.21`. If necessary:

```bash
JAVA_HOME=/home/akib/.jdks/corretto-17.0.21 ./gradlew --version
```

Use your own installed path elsewhere. Android Studio also has a Gradle JDK setting. Prefer the wrapper over a system Gradle installation.

## Run Java examples

From the repo root:

```bash
mkdir -p /tmp/my-companion-learning/java
javac --release 17 -d /tmp/my-companion-learning/java Docs/learning/java-kotlin/examples/java/*.java
java -cp /tmp/my-companion-learning/java Basics
java -cp /tmp/my-companion-learning/java Catalogue
```

The `-d` option chooses output; `-cp` locates classes. Basics is a class name without a suffix. Public class Basics belongs in Basics.java. Examples omit packages to simplify commands; production classes use packages. Both programs print a final success line and use checks that do not depend on Java's optional `-ea` flag.

## Run Kotlin examples

Use an IntelliJ IDEA Kotlin/JVM project, or install the standalone compiler with the [official instructions](https://kotlinlang.org/docs/command-line.html). Gradle supplies the Android project's compiler; building the app does not require a system kotlinc executable.

After `kotlinc -version` works:

```bash
mkdir -p /tmp/my-companion-learning/kotlin
kotlinc Docs/learning/java-kotlin/examples/kotlin/Basics.kt -include-runtime -d /tmp/my-companion-learning/kotlin/basics.jar
java -jar /tmp/my-companion-learning/kotlin/basics.jar
kotlinc Docs/learning/java-kotlin/examples/kotlin/Modeling.kt -include-runtime -d /tmp/my-companion-learning/kotlin/modeling.jar
java -jar /tmp/my-companion-learning/kotlin/modeling.jar
```

Each has an independent main: compile separately. Coroutines need an extra library, explained in chapter 06. Mixed Java/Kotlin needs chapter 05's classpath commands.

## Android comes later

The app currently requires API 36 or newer, despite older API 24/34 references.

```bash
./gradlew :app:assembleDebug --max-workers=2
./gradlew :app:testDebugUnitTest --max-workers=2
```

Installation is separate. Verify device, app ID, and signing key. Do not uninstall an existing app merely to solve a signature mismatch without deciding what happens to its data. Console lessons need no phone.

## Experiment and checkpoint

Copy Basics.java into your learning project. Remove a semicolon and compile: the compiler points to invalid source. Restore it and change a check's expected number: it compiles but fails while running. A third category is logic error: a program runs but produces the wrong answer, sometimes silently without checks.

Explain compiler versus runtime, JDK versus Android SDK, and build versus installation. Can Kotlin call Java? Yes, with interoperability details. Does compilation prove a feature works? No.

Reference: [Java first steps](https://dev.java/learn/getting-started/).
