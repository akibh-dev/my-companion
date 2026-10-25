plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.akib.mycompanion.ai"
}

kapt {
    correctErrorTypes = true
}

dependencies {
    api(project(":core"))
}
