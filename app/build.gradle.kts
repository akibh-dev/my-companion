import java.util.Properties

/**
 * Google Maps / Places key for the place editor: MAPS_API_KEY in local.properties (never committed) or
 * the environment. Without it the app still builds; the map and search say the key is missing.
 */
val mapsApiKey: String = rootProject.file("local.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } }.getProperty("MAPS_API_KEY") }
    ?: System.getenv("MAPS_API_KEY").orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "dev.akibh.mycompanion"
    // One phone (CMF Phone 1, Android 16), so no code for older versions. Docs/decisions.md
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.akibh.mycompanion"
        minSdk = 36
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["mapsApiKey"] = mapsApiKey

        // Only the CPU the target phone has (CMF Phone 1: 64-bit ARM). Every other ABI's copy of the native
        // libraries (ONNX Runtime and friends) just made the APK ~90 MB bigger. For an emulator, build with
        // -Pabis=x86_64 (or a comma-separated list).
        ndk {
            abiFilters += providers.gradleProperty("abis").orNull?.split(",")?.map(String::trim) ?: listOf("arm64-v8a")
        }
    }

    // The release key lives outside the repo (Docs/23-release-1.0-plan.md): its path and passwords come
    // from ~/.gradle/gradle.properties. Without them (CI) the release build falls back to the debug key,
    // so it still builds, but only a locally built, release-signed APK is ever published.
    // Passing -PMYCOMPANION_RELEASE_STORE_FILE= (empty) signs with the debug key instead, so a release
    // build can be tried on the phone over the current install without losing its data.
    val releaseStore = providers.gradleProperty("MYCOMPANION_RELEASE_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("MYCOMPANION_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("MYCOMPANION_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("MYCOMPANION_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Readium (the library's book engine) is built for older Android and asks for it.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("tools/banglish/fixtures"))

    testOptions {
        unitTests {
            // Robolectric needs merged resources and the manifest.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Robolectric runs Android 16 (SDK 36), which needs JDK 21; Gradle downloads one if missing.
                test.javaLauncher.set(
                    extensions.getByType<JavaToolchainService>().launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(21))
                    },
                )
                // Robolectric keeps a copy of the Android framework per sandbox.
                test.maxHeapSize = "3g"
                // Robolectric downloads its Android jars at test time. On CI, Maven Central rate-limits
                // the shared runners (HTTP 429), so fetch from Google's mirror of Maven Central instead.
                System.getenv("ROBOLECTRIC_REPO_URL")?.let { url ->
                    test.systemProperty("robolectric.dependency.repo.url", url)
                }
                test.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }

    lint {
        abortOnError = true
        // Lint's Kotlin 2 analysis can crash on test files; tests aren't linted anyway.
        ignoreTestSources = true
        warningsAsErrors = false
        checkDependencies = false
        // Version-bump nags are not actionable for CI.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        xmlReport = true
        htmlReport = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*.md")
    }
}

androidComponents {
    // Compose UI tests need ui-test-manifest, which only the debug build has, so `./gradlew test`
    // runs the debug unit tests only (same as CI's testDebugUnitTest).
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // `@ApplicationContext private val context` annotates the parameter and the property (Kotlin's future default).
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":design"))
    implementation(project(":ai-api"))
    implementation(project(":ai-models"))
    implementation(project(":adapters-messaging"))
    implementation(project(":feature-context"))
    implementation(project(":feature-autoreply"))
    implementation(project(":feature-voice"))
    implementation(project(":feature-quran"))
    implementation(project(":feature-library"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // YouTube background player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.coroutines.guava)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // JVM + Robolectric tests (run with ./gradlew testDebugUnitTest)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)

    // On-device tests (./gradlew connectedDebugAndroidTest)
    // Compose otherwise brings Espresso 3.5, which calls a removed InputManager API on Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
