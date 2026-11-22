plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The approved monochrome look (Docs/design/approved-monochrome-ui.md): theme, Inter, dot lettering
// and dot icons, shared by the app and the feature modules that draw their own screens.
android {
    namespace = "dev.akibh.mycompanion.design"
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
