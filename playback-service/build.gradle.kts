plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.wxy.playerlite.playback.service"
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
    implementation(project(":cache-core"))
    implementation(project(":player"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.session)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
}
