plugins {
    id("playerlite.android.application")
    id("playerlite.android.compose")
}

val apiBaseUrl = providers.gradleProperty("playerlite.apiBaseUrl")
    .orElse("http://139.9.223.233:3000")
    .get()

android {
    namespace = "com.wxy.playerlite"
    ndkVersion = "27.0.12077973"
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.wxy.playerlite"
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":design-system"))
    implementation(project(":feature-player"))
    implementation(project(":feature-detail-support"))
    implementation(project(":feature-player"))
    implementation(project(":feature-search"))
    implementation(project(":feature-playlist-detail"))
    implementation(project(":feature-album-detail"))
    implementation(project(":feature-artist-detail"))
    implementation(project(":network-core"))
    implementation(project(":playlist-core"))
    implementation(project(":playback-orchestrator"))
    implementation(project(":user"))
    implementation(project(":playback-client"))
    implementation(project(":playback-contract"))
    implementation(project(":playback-service"))
    implementation(project(":player"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3.session)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
