@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include(
    ":retrograde-util",
    ":retrograde-app-shared",
    ":lemuroid-touchinput",
    ":lemuroid-app",
    ":opennes-libretrodroid",
    ":lemuroid-metadata-libretro-db",
    ":lemuroid-app-ext-free",
    ":bundled-cores",
    ":baselineprofile"
)

project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")
