plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playlist.core"
}

dependencies {
    implementation(project(":playback-contract"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
