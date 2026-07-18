plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playback.api"
}

dependencies {
    implementation(project(":player"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.session)
    implementation("androidx.media3:media3-common:1.4.1")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
