plugins {
    id("com.android.library")
    id("kotlin-android")
}

/**
 * Rebuild the *pinned upstream v0.14.0 source*, not a second LibretroDroid AAR.
 * patches are applied by scripts/prepare-libretrodroid-bridge.py before Gradle.
 * Retain upstream's GPL-3.0-or-later license and attribution.
 */
android {
    namespace = "com.swordfish.libretrodroid"
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = rootProject.file("vendor/LibretroDroid/libretrodroid/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs(rootProject.file("vendor/LibretroDroid/libretrodroid/src/main/java"))
            manifest.srcFile(rootProject.file("vendor/LibretroDroid/libretrodroid/src/main/AndroidManifest.xml"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
}
