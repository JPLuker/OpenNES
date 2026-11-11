package com.swordfish.lemuroid.app.shared.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenNesDiagnosticReportTest {
    @Test
    fun containsOnlyAllowlistedBuildInformation() {
        val result =
            OpenNesDiagnosticReport.build(
                appVersion = "0.1.0",
                versionCode = 14,
                androidSdk = 35,
                deviceAbi = "arm64-v8a",
                packageVariant = "debug",
                sourceCommit = "abc123def456",
            )
        assertTrue(result.contains("Android SDK: 35"))
        assertTrue(result.contains("ABI: arm64-v8a"))
        assertTrue(result.contains("Source: abc123def456"))
        assertTrue(result.contains("Hardcore: Unavailable"))
        assertFalse(result.contains("token"))
        assertFalse(result.contains("RomM URL"))
        assertFalse(result.contains("password"))
    }

    @Test
    fun rejectsUntrustedStringsInsteadOfCopyingPrivatePathsOrTokens() {
        val token = "abcdef_super_secret"
        val result =
            OpenNesDiagnosticReport.build(
                appVersion = "0.1.0/?token=$token",
                versionCode = -10,
                androidSdk = -10,
                deviceAbi = "arm64-v8a; /data/user/0/private",
                packageVariant = "debug?key=$token",
                sourceCommit = "abc123?secret=$token",
            )
        assertFalse(result.contains(token))
        assertFalse(result.contains("/data/user/0"))
        assertFalse(result.contains("0.1.0/?"))
        assertTrue(result.contains("App: unknown"))
        assertTrue(result.contains("Build: 0"))
        assertTrue(result.contains("Variant: unknown"))
        assertTrue(result.contains("ABI: unknown"))
        assertTrue(result.contains("Source: local"))
    }
}
