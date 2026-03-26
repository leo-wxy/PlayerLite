plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playback.orchestrator"
}

dependencies {
    implementation(project(":playback-client"))
    implementation(project(":playback-contract"))
    implementation(project(":player"))
    implementation("androidx.media3:media3-common:1.4.1")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
