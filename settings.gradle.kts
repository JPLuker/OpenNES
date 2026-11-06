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
    ":lemuroid-metadata-libretro-db",
    ":lemuroid-app-ext-free",
    ":lemuroid-app-ext-play",
    ":bundled-cores",
    ":baselineprofile"
)

project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

if (usePlayDynamicFeatures()) {
    include(":lemuroid_core_fceumm")
    project(":lemuroid_core_fceumm").projectDir = File("lemuroid-cores/lemuroid_core_fceumm")
}
