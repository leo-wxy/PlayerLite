plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.1.0-rc01")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}

gradlePlugin {
    plugins {
        register("playerliteAndroidApplication") {
            id = "playerlite.android.application"
            implementationClass = "PlayerLiteAndroidApplicationConventionPlugin"
        }
        register("playerliteAndroidLibrary") {
            id = "playerlite.android.library"
            implementationClass = "PlayerLiteAndroidLibraryConventionPlugin"
        }
        register("playerliteAndroidCompose") {
            id = "playerlite.android.compose"
            implementationClass = "PlayerLiteAndroidComposeConventionPlugin"
        }
    }
}
