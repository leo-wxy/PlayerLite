plugins {
    id("playerlite.android.library")
}

android {
    namespace = "com.wxy.playerlite.player"
    ndkVersion = "27.0.12077973"

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs {
                directories.add(file("../third_party/FFmpeg-n6.1.4/out-jniLibs").path)
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}
