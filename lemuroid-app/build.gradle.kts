plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The stable version is stored once. CI supplies a monotonically increasing
// development build number, so each rolling APK installs over the previous one.
val openNesVersion = rootProject.file("VERSION").readText().trim()
require(openNesVersion.matches(Regex("""[0-9]+\.[0-9]+\.[0-9]+"""))) {
    "VERSION must be a semantic major.minor.patch version"
}
// Production Play versionCodes come from the ordered VERSION tuple, not
// GitHub Actions run numbers. Debug remains on its separate package/sequence.
val (majorVersion, minorVersion, patchVersion) = openNesVersion.split(".").map(String::toInt)
require(majorVersion in 0..2_100 && minorVersion in 0..999 && patchVersion in 0..999) {
    "VERSION exceeds the safe Play versionCode representation"
}
val playVersionCode = majorVersion * 1_000_000 + minorVersion * 1_000 + patchVersion
require(playVersionCode in 1..2_100_000_000) { "Invalid Play versionCode" }

val developmentRunRaw = System.getenv("OPENNES_DEV_RUN_NUMBER")
val developmentRun = developmentRunRaw?.toIntOrNull()
require(developmentRunRaw == null || (developmentRun != null && developmentRun in 1..1_000_000)) {
    "OPENNES_DEV_RUN_NUMBER must be a positive CI run number"
}

android {
    defaultConfig {
        versionCode = developmentRun?.plus(100_000) ?: playVersionCode
        versionName = openNesVersion
        applicationId = "com.jpluker.opennes"
        // Private-free source revision in user-copied diagnostics, not a signing identity.
        val sourceSha = System.getenv("GITHUB_SHA").orEmpty()
        val verifiedSha =
            sourceSha.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }
                ?.take(12)?.lowercase() ?: "local"
        buildConfigField("String", "SOURCE_COMMIT", "\"$verifiedSha\"")
    }
    flavorDimensions += listOf("opensource", "cores")

    // Preserve the historical freeBundle task name for developer upgrades
    // and Play AAB validation. Unsupported Play/dynamic combinations are no
    // longer generated; every OpenNES build bundles FCEUmm for offline use.
    productFlavors {
        create("free") {
            dimension = "opensource"
        }
        create("bundle") {
            dimension = "cores"
        }
    }

    packagingOptions {
        jniLibs {
            // Prevent non-NES emulator binaries from being packaged.
            // The upstream bundled-cores submodule remains available as source.
            excludes +=
                setOf(
                    "**/libcitra_libretro_android.so",
                    "**/libdesmume_libretro_android.so",
                    "**/libdosbox_pure_libretro_android.so",
                    "**/libfbneo_libretro_android.so",
                    "**/libgambatte_libretro_android.so",
                    "**/libgenesis_plus_gx_libretro_android.so",
                    "**/libhandy_libretro_android.so",
                    "**/libmame2003_plus_libretro_android.so",
                    "**/libmednafen_ngp_libretro_android.so",
                    "**/libmednafen_pce_fast_libretro_android.so",
                    "**/libmednafen_wswan_libretro_android.so",
                    "**/libmelonds_libretro_android.so",
                    "**/libmgba_libretro_android.so",
                    "**/libmupen64plus_next_gles3_libretro_android.so",
                    "**/libpcsx_rearmed_libretro_android.so",
                    "**/libppsspp_libretro_android.so",
                    "**/libprosystem_libretro_android.so",
                    "**/libsnes9x_libretro_android.so",
                    "**/libstella_libretro_android.so",
                )
            keepDebugSymbols += setOf("*/*/libfceumm_libretro_android.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module")
        }
    }

    signingConfigs {
        maybeCreate("debug").apply {
            storeFile = file("$rootDir/debug.keystore")
        }

        // A production release must NEVER be signed with inherited
        // upstream credentials. Without all four environment variables,
        // Gradle can assemble only an unsigned release artifact.
        val keystore = System.getenv("OPENNES_RELEASE_KEYSTORE")
        val storeSecret = System.getenv("OPENNES_RELEASE_STORE_PASSWORD")
        val alias = System.getenv("OPENNES_RELEASE_KEY_ALIAS")
        val keySecret = System.getenv("OPENNES_RELEASE_KEY_PASSWORD")
        if (listOf(keystore, storeSecret, alias, keySecret).all { !it.isNullOrBlank() }) {
            create("openNesRelease") {
                storeFile = file(requireNotNull(keystore))
                storePassword = requireNotNull(storeSecret)
                keyAlias = requireNotNull(alias)
                keyPassword = requireNotNull(keySecret)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("openNesRelease")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "lemuroid_name", "OpenNES")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = developmentRun?.let { "-dev.$it" } ?: "-local"
            resValue("string", "lemuroid_name", "OpenNES Debug")
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = deps.versions.kotlinExtension
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Android's org.json classes are method stubs in local JVM unit tests.
    testImplementation("org.json:json:20240303")

    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":lemuroid-metadata-libretro-db"))
    implementation(project(":lemuroid-touchinput"))

    "baselineProfile"(project(":baselineprofile"))
    implementation(deps.libs.androidx.profileInstaller)

    "bundleImplementation"(project(":bundled-cores"))

    "freeImplementation"(project(":lemuroid-app-ext-free"))

    implementation(deps.libs.androidx.navigation.navigationFragment)
    implementation(deps.libs.androidx.navigation.navigationUi)
    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.androidx.lifecycle.reactiveStreams)

    kapt(deps.libs.androidx.lifecycle.processor)

    implementation(deps.libs.androidx.leanback.leanbackPreference)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    // Offline, on-device QR scanner; no Google Play Services dependency.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.guava)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.systemUiController)
    implementation(deps.libs.androidx.compose.accompanist.navigationMaterial)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)
    implementation(deps.libs.composeHtmlText)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    implementation(project(":opennes-libretrodroid"))

    // Uncomment this when using a local aar file.
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    kapt(deps.libs.dagger.android.processor)
    kapt(deps.libs.dagger.compiler)
}
