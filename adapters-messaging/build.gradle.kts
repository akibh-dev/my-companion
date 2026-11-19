plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.akibh.mycompanion.messaging"
}

kapt {
    correctErrorTypes = true
}

dependencies {
    api(project(":core"))
    implementation(libs.androidx.core.ktx)
}
