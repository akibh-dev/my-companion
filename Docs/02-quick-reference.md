# Android Development Quick Reference

Essential facts you need to memorize for Android development.

---

## The One Rule

> **Everything must match: JDK 17 + Kotlin 1.9.22 + Compose Compiler 1.5.8 + AGP 8.4.0**

Mismatch = build fails.

---

## File Purposes (One-Liners)

| File | What It Does |
|------|--------------|
| `settings.gradle.kts` | Says "this project has an `:app` module" |
| `build.gradle.kts` (root) | Declares plugins available (not applied) |
| `app/build.gradle.kts` | **THE config** - SDK versions, dependencies, build options |
| `gradle.properties` | JVM memory, AndroidX flag, caching |
| `local.properties` | Where Android SDK is installed (not in git) |
| `AndroidManifest.xml` | Tells Android: "I have an Activity, need no permissions" |
| `strings.xml` | All text resources (translatable) |
| `MainActivity.kt` | Your UI code - extends `ComponentActivity` |

---

## Key Annotations

| Annotation | Meaning | Example |
|------------|---------|---------|
| `@Composable` | This function describes UI | `fun Greeting() { Text("Hi") }` |
| `@Preview` | Show in IDE preview | `@Preview @Composable fun Preview()` |
| `setContent { }` | Bridge Activity → Compose | `setContent { MyApp() }` |

---

## Compose Mental Model

```
Traditional Android (XML)          Jetpack Compose
─────────────────────────          ─────────────────
Activity ──▶ Inflate XML    →      Activity ──▶ setContent { }
    │                                   │
    ▼                                   ▼
findViewById()                    Composable functions
    │                                   │
    ▼                                   ▼
Views (TextView, etc.)            Text(), Button(), etc.
    │                                   │
    ▼                                   ▼
setText(), setOnClickListener()   Direct: Text("Hi"), onClick={ }
```

**Key Insight**: Compose is **declarative** — describe what UI should look like, not how to update it.

---

## Gradle Build Flow

```
You type: ./gradlew :app:assembleDebug

1. Gradle reads settings.gradle.kts → finds :app
2. Reads app/build.gradle.kts → gets dependencies
3. Downloads libs from google() / mavenCentral()
4. Compiles Kotlin → Java bytecode (JDK 17)
5. Converts to DEX (Android bytecode)
6. Packages with resources → APK
7. Signs with debug key → app-debug.apk
```

---

## ADB Commands (Add to PATH first)

```bash
# Add to ~/.zshrc or ~/.bashrc:
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"

# Then use:
adb devices                    # List connected devices
adb install app-debug.apk      # Install APK
adb shell am start -n com.akib.mycompanion/.MainActivity  # Launch app
adb logcat                     # View logs
adb uninstall com.akib.mycompanion  # Remove app
```

---

## IntelliJ Shortcuts (Android Dev)

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+F10` | Run app (build + install + launch) |
| `Ctrl+F10` | Apply code changes (hot reload) |
| `Ctrl+Space` | Auto-complete |
| `Ctrl+Q` | Quick documentation |
| `Ctrl+Alt+Shift+S` | Project Structure (SDK settings) |
| `Ctrl+Alt+S` | Settings |

---

## Version Numbers You Must Know

| Component | Your Version | Rule |
|-----------|--------------|------|
| JDK | 17 | **Must be 17** (not 21, not 25) |
| Kotlin | 1.9.22 | Fixed by build.gradle.kts |
| Compose Compiler | 1.5.8 | **Must match Kotlin** |
| Compose UI | 1.5.4 | Works with compiler 1.5.8 |
| AGP | 8.4.0 | Android Gradle Plugin |
| Gradle | 8.14.5 | Build system |

**Check compatibility**: [Compose-Kotlin Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)

---

## Emergency Commands

```bash
# Build fails? Try:
./gradlew clean :app:assembleDebug

# Still fails? Nuke everything:
rm -rf .gradle app/build build
./gradlew :app:assembleDebug

# IntelliJ confused?
File → Invalidate Caches → Invalidate and Restart

# Wrong JDK?
File → Project Structure → Project → SDK: 17
Settings → Gradle → Gradle JVM: 17
```

---

## Your First App (What It Does)

```
App Icon → Click → MainActivity.onCreate() → setContent { MyApp() }
                                                          │
                                                          ▼
                                                    MaterialTheme
                                                          │
                                                          ▼
                                                    Surface (bg)
                                                          │
                                                          ▼
                                                    Text("Hello World!")
```

**Result**: Full-screen "Hello World!" with Material Design colors.

---

## Next Learning Steps (In Order)

1. ✅ **Hello World** ← You are here
2. **State**: `var count by remember { mutableStateOf(0) }`
3. **Click**: `Button(onClick = { count++ })`
4. **Navigation**: `NavHost` + multiple screens
5. **ViewModel**: Survive rotation, separate logic from UI
6. **Room**: Local database
7. **Media3**: Background playback (your goal!)

Each builds on previous. Master one before moving on.