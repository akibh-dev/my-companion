# Hello World Android App - Complete Guide

This document explains every file in your minimal Android project, what it does, and how they work together.

---

## Project Structure

```
my-companion/
├── app/                          # Your app module (main code lives here)
│   ├── build.gradle.kts          # App-level build config
│   ├── proguard-rules.pro        # Code obfuscation rules (empty for now)
│   └── src/main/
│       ├── AndroidManifest.xml   # App permissions, activities, metadata
│       ├── java/com/akib/mycompanion/
│       │   └── MainActivity.kt   # Your main screen code
│       └── res/
│           └── values/
│               └── strings.xml   # Text resources
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar    # Gradle wrapper executable
│       └── gradle-wrapper.properties  # Which Gradle version to use
├── build.gradle.kts              # Root project build config
├── settings.gradle.kts           # Which modules are in this project
├── gradle.properties             # Gradle/JVM settings
├── local.properties              # Local SDK path (auto-generated)
└── gradlew / gradlew.bat         # Gradle wrapper scripts
```

---

## File-by-File Breakdown

### 1. `settings.gradle.kts` - Project Root

**Purpose**: Tells Gradle which modules are in your project.

```kotlin
pluginManagement {
    repositories {
        google()        // Google's Maven repo (for Android libs)
        mavenCentral()  // Central repo (for Java/Kotlin libs)
        gradlePluginPortal()  // Gradle plugins
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "my-companion"
include(":app")  // Your app module is included
```

**Key Concepts**:
- `google()` - Where AndroidX, Compose, etc. live
- `mavenCentral()` - Where most Java/Kotlin libraries live
- `include(":app")` - Registers your app as a buildable module

---

### 2. `build.gradle.kts` - Root Project Config

**Purpose**: Defines plugins available to all modules.

```kotlin
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

**Key Concepts**:
- `apply false` - Plugins are declared here but not applied (applied in app module)
- `version "8.4.0"` - Android Gradle Plugin (AGP) version
- `version "1.9.22"` - Kotlin compiler version

---

### 3. `app/build.gradle.kts` - Your App's Build Config

**Purpose**: The most important file - defines how your app builds.

```kotlin
plugins {
    id("com.android.application")      // Android app plugin
    id("org.jetbrains.kotlin.android") // Kotlin support
}

android {
    namespace = "com.akib.mycompanion"  // Unique package name
    compileSdk = 34                     // Build against Android 34 APIs

    defaultConfig {
        applicationId = "com.akib.mycompanion"  // Play Store ID
        minSdk = 24                     // Minimum Android 7.0
        targetSdk = 34                  // Target Android 14
        versionCode = 1                 // Internal version number
        versionName = "1.0"             // User-visible version
    }

    buildTypes {
        release {
            isMinifyEnabled = false     // No code obfuscation (for now)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // Java 17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"                // Kotlin targets Java 17 bytecode
    }

    buildFeatures {
        compose = true                  // Enable Jetpack Compose
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"  // Compose compiler version
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")           // Core Android utilities
    implementation("androidx.activity:activity-compose:1.8.2") // Compose Activity integration
    implementation("androidx.compose.ui:ui:1.5.4")            // Compose UI toolkit
    implementation("androidx.compose.material3:material3:1.1.2") // Material Design 3
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4") // Preview annotations
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4") // Preview in IDE (debug only)
}
```

**Key Concepts**:

| Setting | Meaning |
|---------|---------|
| `namespace` | Package name for R class (resources) |
| `compileSdk` | Build against this Android version's APIs |
| `minSdk` | Minimum Android version your app supports |
| `targetSdk` | Version you optimize for (affects runtime behavior) |
| `buildFeatures.compose` | Enable Jetpack Compose UI |
| `kotlinCompilerExtensionVersion` | Must match Kotlin version (1.5.8 ↔ 1.9.22) |

**Dependency Types**:
- `implementation` - Needed at runtime and compile time
- `debugImplementation` - Only in debug builds (not released)

---

### 4. `gradle.properties` - JVM & Android Settings

```properties
# JVM Settings
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8  # 4GB heap for Gradle
org.gradle.parallel=true      # Build modules in parallel
org.gradle.caching=true       # Cache build outputs
org.gradle.configureondemand=true  # Only configure needed modules

# Android
android.useAndroidX=true      # Use AndroidX (not old support libs)
android.nonTransitiveRClass=true  # Smaller R class (faster builds)
```

**Key Concepts**:
- `android.useAndroidX=true` - **Required** for all modern Android apps
- `org.gradle.jvmargs=-Xmx4g` - Gives Gradle 4GB memory (large projects)

---

### 5. `local.properties` - Your Machine's Config (NOT in git)

```properties
sdk.dir=/home/akib/Android/Sdk  # Path to Android SDK
```

**Purpose**: Points to your Android SDK installation. Auto-generated by Android Studio/IntelliJ.

---

### 6. `AndroidManifest.xml` - App Blueprint

**Purpose**: Tells Android system what your app contains and needs.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_menu_gallery"  <!-- Built-in icon -->
        android:label="@string/app_name"                   <!-- From strings.xml -->
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"    <!-- Your main screen -->
            android:exported="true">        <!-- Required for launcher -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

**Key Concepts**:

| Attribute | Meaning |
|-----------|---------|
| `android:name=".MainActivity"` | Class that runs when app launches |
| `android:exported="true"` | Required for main activity (Android 12+) |
| `MAIN` + `LAUNCHER` | Makes app appear in app drawer |

---

### 7. `strings.xml` - Text Resources

```xml
<resources>
    <string name="app_name">My Companion</string>
</resources>
```

**Purpose**: Centralize all user-visible text. Used in manifest (`@string/app_name`).

**Why?** Easy to translate, reuse, and maintain text.

---

### 8. `MainActivity.kt` - Your App's Code

```kotlin
package com.akib.mycompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {  // Entry point, extends Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {                        // Start Compose UI
            MyApp()
        }
    }
}

@Composable                                 // Marks function as Compose UI
fun MyApp() {
    MaterialTheme {                         // Apply Material Design theme
        Surface(                            // Container with background
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Greeting("Hello World!")
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = name)                       // Display text
}

@Preview(showBackground = true)             // Show in IDE preview
@Composable
fun GreetingPreview() {
    MyApp()
}
```

**Key Concepts**:

| Annotation | Purpose |
|------------|---------|
| `@Composable` | Function describes UI (not a widget itself) |
| `@Preview` | Shows this composable in IDE preview |
| `setContent` | Bridge between Activity and Compose |

**How It Works**:
1. App launches → `MainActivity.onCreate()` runs
2. `setContent { MyApp() }` starts Compose
3. `MyApp()` sets up Material theme
4. `Surface` creates a container
5. `Greeting` displays "Hello World!"

---

## How Files Connect

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Android OS     │────▶│ AndroidManifest  │────▶│  MainActivity   │
│  (Launcher)     │     │  (declares app)  │     │  (onCreate)     │
└─────────────────┘     └──────────────────┘     └────────┬────────┘
                                                          │
                                                          ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  User sees      │◄────│   Compose UI     │◄────│   setContent    │
│  "Hello World!" │     │   (MaterialTheme)│     │   (MyApp)       │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

**Build Flow**:
```
Kotlin Code ──▶ Compiler ──▶ Dex (Android bytecode) ──▶ APK
     ↑                                              ↑
Resources ──▶ AAPT ──▶ R.java ──▶ Merge ──▶ Package
```

---

## Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Gradle | 8.14.5 | Build system |
| AGP | 8.4.0 | Android Gradle Plugin |
| Kotlin | 1.9.22 | Programming language |
| Compose Compiler | 1.5.8 | **Must match Kotlin** |
| Compose UI | 1.5.4 | UI toolkit |
| JDK | 17 | Required by AGP 8.x |

**Critical**: Compose Compiler 1.5.8 works with Kotlin 1.9.22. Mismatch = build error.

---

## Commands Cheat Sheet

```bash
# Set JDK 17 (required)
export JAVA_HOME=/home/akib/.jdks/corretto-17.0.20.1

# Build APK
./gradlew :app:assembleDebug

# Install on device
~/Android/Sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk

# Launch app
~/Android/Sdk/platform-tools/adb shell am start -n com.akib.mycompanion/.MainActivity

# Clean build
./gradlew clean :app:assembleDebug

# See all tasks
./gradlew tasks
```

---

## Common Issues & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `Kotlin version not compatible` | Compose Compiler ≠ Kotlin | Match versions (see matrix above) |
| `useAndroidX not enabled` | Missing `gradle.properties` | Add `android.useAndroidX=true` |
| `SDK not found` | Missing `local.properties` | Create with `sdk.dir=...` |
| `JDK 25 not supported` | Kotlin 1.9.22 max JDK 21 | Use JDK 17 |
| `compose compiler not found` | Version mismatch | Use 1.5.8 for Kotlin 1.9.22 |

---

## Next Steps (When Ready)

1. **Add app icon**: Create `res/mipmap-*/ic_launcher.png`
2. **Change theme**: Modify `MaterialTheme` in `MyApp()`
3. **Add navigation**: Create second screen + Navigation library
4. **Add ViewModel**: For state management (learning step)

---

## Resources

- [Android Basics](https://developer.android.com/courses/android-basics-compose/course)
- [Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)
- [Compose-Kotlin Compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)