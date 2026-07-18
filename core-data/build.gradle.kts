plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.core.data"
}

dependencies {
    implementation(project(":playback-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
