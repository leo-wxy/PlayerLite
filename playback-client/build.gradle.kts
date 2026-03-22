plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playback.client"
}

dependencies {
    implementation(project(":playback-contract"))
    implementation(project(":player"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.session)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
}
