plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.user"
}

dependencies {
    implementation(project(":network-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
