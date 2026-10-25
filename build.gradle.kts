import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Settings every library module shares (see Docs/architecture/004-modules.md), so each module's
// own build file only lists its namespace and dependencies.
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                    all { test ->
                        test.javaLauncher.set(
                            extensions.getByType<JavaToolchainService>().launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(21))
                            },
                        )
                        test.maxHeapSize = "2g"
                        test.testLogging {
                            events("failed")
                            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                        }
                    }
                }
            }
            lint {
                abortOnError = true
                ignoreTestSources = true
                checkDependencies = false
                disable += setOf(
                    "GradleDependency",
                    "NewerVersionAvailable",
                    "AndroidGradlePluginVersion",
                    "OldTargetApi",
                )
            }
        }
        extensions.configure<LibraryAndroidComponentsExtension> {
            beforeVariants(selector().withBuildType("release")) { variant ->
                (variant as HasHostTestsBuilder).hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE).enable = false
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> { android.set(true) }
    }
    // Every library module uses coroutines, Hilt and the same test stack.
    plugins.withId("com.android.library") {
        dependencies {
            add("implementation", libs.coroutines.android)
            add("testImplementation", libs.junit)
            add("testImplementation", libs.truth)
            add("testImplementation", libs.mockk)
            add("testImplementation", libs.turbine)
            add("testImplementation", libs.coroutines.test)
            add("testImplementation", libs.robolectric)
            add("testImplementation", libs.androidx.test.core)
            add("testImplementation", libs.androidx.test.ext.junit)
        }
    }
    plugins.withId("com.google.dagger.hilt.android") {
        dependencies {
            add("implementation", libs.hilt.android)
            add("kapt", libs.hilt.compiler)
        }
    }
}
