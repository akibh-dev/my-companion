# Gradle Tips (Kotlin DSL)

## Project Structure
```
my-companion/
├── settings.gradle.kts
├── build.gradle.kts          # Root build script
├── gradle/
│   └── libs.versions.toml    # Version catalog
├── app/
│   ├── build.gradle.kts      # App module
│   └── src/...
└── gradle.properties         # Gradle/JVM settings
```

## Version Catalog (libs.versions.toml)
```toml
[versions]
agp = "8.4.0"
kotlin = "1.9.22"
coreKtx = "1.13.1"
lifecycle = "2.8.0"
activity = "1.9.0"
composeBom = "2024.04.00"
hilt = "2.48"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
moshi = "1.15.1"
coroutines = "1.8.0"
flow = "1.8.0"
work = "2.9.0"
media3 = "1.4.0"
ktlint = "1.2.1"
detekt = "1.23.4"

[libraries]
# AndroidX
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity" }

# Compose BOM (manages all compose versions)
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
compose-compiler = { group = "androidx.compose.compiler", name = "compiler" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Networking
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-moshi = { group = "com.squareup.retrofit2", name = "converter-moshi", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
moshi = { group = "com.squareup.moshi", name = "moshi", version.ref = "moshi" }
moshi-kotlin = { group = "com.squareup.moshi", name = "moshi-kotlin", version.ref = "moshi" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# Media3
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }

# Testing
junit = { group = "org.junit", name = "junit", version = "4.13.2" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version = "5.10.2" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.13" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.0.0" }
androidx-test-core = { group = "androidx.test", name = "core", version = "1.5.0" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version = "1.1.5" }
androidx-test-espresso = { group = "androidx.test.espresso", name = "espresso-core", version = "3.5.1" }
compose-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# Plugins
ktlint = { group = "org.jlleitschuh.gradle", name = "ktlint-gradle", version.ref = "ktlint" }
detekt = { group = "io.gitlab.arturbosch.detekt", name = "detekt-gradle-plugin", version.ref = "detekt" }

[bundles]
compose = ["compose-ui", "compose-material3", "compose-runtime", "compose-navigation"]
room = ["room-runtime", "room-ktx", "room-compiler"]
hilt = ["hilt-android", "hilt-compiler", "hilt-navigation-compose"]
network = ["retrofit", "retrofit-moshi", "okhttp", "okhttp-logging", "moshi", "moshi-kotlin"]
test = ["junit", "junit-jupiter", "mockk", "turbine", "androidx-test-core", "androidx-test-ext-junit", "androidx-test-espresso", "compose-test", "compose-test-manifest"]
```

## Using in Module build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)  // Faster than kapt
    // alias(libs.plugins.ktlint)
    // alias(libs.plugins.detekt)
}

android {
    namespace = "com.akib.mycompanion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.akib.mycompanion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"  // Match compose compiler version
    }

    buildFeatures {
        compose = true
        viewBinding = false  // Not needed with Compose
    }

    packagingOptions {
        resources.excludes += "META-INF/*.kotlin_module"
    }
}

dependencies {
    // Version catalog bundles
    implementation(libs.bundles.compose)
    implementation(libs.bundles.room)
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.network)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)

    // Individual
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.bundles.test)
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.compose.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("hilt.useNewApi", "true")
}

// Ktlint
tasks.named("ktlintFormat") {
    setDescription("Format Kotlin code with ktlint")
    setGroup("verification")
}

// Detekt
tasks.named("detekt") {
    setDescription("Run detekt static analysis")
    setGroup("verification")
}
```

## Root build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
```

## settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:8.4.0")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "my-companion"
include(":app")
```

## gradle.properties
```properties
# JVM
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true

# Android
android.nonTransitiveRClass=true
android.enableArtifactTransforms=true

# Kotlin
kotlin.code.style=official

# KSP
ksp.incremental=true
ksp.useWorkers=true

# Compose
android.experimental.buildTypes=true
```

## Common Commands
```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Test
./gradlew test
./gradlew connectedAndroidTest

# Lint & Format
./gradlew lint
./gradlew ktlintCheck
./gradlew ktlintFormat
./gradlew detekt

# Dependencies
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
./gradlew :app:dependencyInsight --dependency retrofit --configuration releaseRuntimeClasspath

# Clean
./gradlew clean

# Generate Room schema (for migrations)
./gradlew :app:kspDebugKotlin
```

## Tips
1. **Always use version catalog** - single source of truth
2. **Use KSP over KAPT** - 2x faster annotation processing
3. **Enable Gradle configuration cache** - `org.gradle.unsafe.configuration-cache=true` (experimental)
4. **Separate test dependencies** - `testImplementation` (JVM) vs `androidTestImplementation` (device)
5. **Use bundles** - group related libs, easier to manage
6. **Pin AGP version in settings.gradle.kts** - avoids surprise upgrades
7. **Generate Room schemas** - enables auto-migration testing