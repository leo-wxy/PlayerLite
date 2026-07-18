plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.playback.service"
}

dependencies {
    implementation(project(":cache-core"))
    implementation(project(":core-data"))
    implementation(project(":playback-api"))
    implementation(project(":player"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.session)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
}
