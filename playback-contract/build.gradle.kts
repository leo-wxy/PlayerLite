plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playback.contract"
}

dependencies {
    implementation(project(":player"))
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
}
