package com.swordfish.lemuroid.app.shared.diagnostics

/**
 * Deliberately allowlisted environment report for support. Do not accept
 * freeform exception messages, arbitrary system properties, user IDs,
 * game titles, server URLs, tokens, file paths or device identifiers.
 */
internal object OpenNesDiagnosticReport {
    fun build(
        appVersion: String,
        versionCode: Long,
        androidSdk: Int,
        deviceAbi: String,
        packageVariant: String,
        sourceCommit: String = "local",
    ): String {
        return listOf(
            "OpenNES support diagnostics (no personal data)",
            "App: ${safeVersion(appVersion)}",
            "Build: ${versionCode.coerceAtLeast(0)}",
            "Android SDK: ${androidSdk.coerceAtLeast(0)}",
            "ABI: ${safeAbi(deviceAbi)}",
            "Variant: ${safeVariant(packageVariant)}",
            "Source: ${safeCommit(sourceCommit)}",
            "Emulator: NES only",
            "Hardcore: Unavailable (pending official approval)",
            "Automatic telemetry: Not included in this report",
        ).joinToString("\n")
    }

    private fun safeVersion(value: String): String =
        value.take(48).takeIf { it.matches(Regex("[A-Za-z0-9._+-]{1,48}")) } ?: "unknown"

    private fun safeAbi(value: String): String =
        when (value) {
            "arm64-v8a", "armeabi-v7a", "x86", "x86_64" -> value
            else -> "unknown"
        }

    private fun safeCommit(value: String): String =
        value.takeIf { it.matches(Regex("[0-9a-f]{7,40}")) } ?: "local"

    private fun safeVariant(value: String): String =
        when (value) {
            "debug" -> "debug"
            "release" -> "release"
            else -> "unknown"
        }
}
