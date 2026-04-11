plugins {
    id("playerlite.android.library")
    id("playerlite.android.compose")
}

android {
    namespace = "com.wxy.playerlite.feature.song"
}

dependencies {
    implementation(project(":design-system"))
    implementation(project(":feature-detail-support"))
    implementation(project(":playback-contract"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.coil.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
