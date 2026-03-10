plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.wxy.playerlite.playback.contract"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":player"))
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
}
