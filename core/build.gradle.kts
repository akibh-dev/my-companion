plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.akib.mycompanion.core"
}

kapt {
    correctErrorTypes = true
}

dependencies {
    api(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.work.testing)
}
