plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.akibh.mycompanion.quran"
    buildFeatures { compose = true }
    // The page fonts are brotli files already; deflating them again only costs build time.
    androidResources { noCompress += listOf("br", "db") }
}

kapt {
    correctErrorTypes = true
    // tools/quran/build_quran_db.py reads this schema to write quran.db exactly as Room expects it.
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    api(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.brotli.dec)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// The Quran text, layouts and fonts are built by tools/quran (git-ignored, like the IndicXlit models).
val verifyQuranData by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/quran/build_quran_db.py", "--check")
}
tasks.named("preBuild") { dependsOn(verifyQuranData) }
