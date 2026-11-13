package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import java.io.IOException

/**
 * RomM 5.4 device registration schema. No Android hardware ID, real hostname,
 * IP address, or MAC address is shared with the server.
 *
 * Creating the registration request is pure. Only an explicit user action may
 * submit it. Registration does not authorize save uploads or replacements.
 */
internal object RommDeviceRegistration {
    data class Registered(val deviceId: String, val name: String)

    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val hostname = Regex("opennes-[0-9a-f]{16}")
    private val clientVersion = Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}")

    /**
     * hostAlias is generated for the specific RomM server and account from a
     * local private random installation ID; never the real Android hostname.
     */
    fun request(
        hostAlias: String,
        version: String,
        displayName: String = "OpenNES Android",
    ): JSONObject {
        require(hostname.matches(hostAlias)) { "Invalid private device alias" }
        require(clientVersion.matches(version)) { "Invalid OpenNES version" }
        require(displayName.length in 1..64 && displayName.none { it.isISOControl() }) {
            "Invalid RomM device label"
        }
        return JSONObject()
            .put("name", displayName)
            .put("platform", "android")
            .put("client", "opennes")
            .put("client_version", version)
            .put("hostname", hostAlias)
            .put("sync_mode", "api")
            .put("allow_existing", true)
            .put("allow_duplicate", false)
            .put("reset_syncs", false)
            .put("capabilities", JSONObject().put("remote_install", false))
    }

    fun parse(response: String): Registered {
        if (response.length !in 2..16_384) throw IOException("Unexpected RomM registration response")
        val result =
            try {
                JSONObject(response)
            } catch (_: Exception) {
                throw IOException("Malformed RomM device registration")
            }
        val deviceId = result.optString("device_id")
        val name = result.optString("name")
        if (!uuid.matches(deviceId) || name.length !in 1..128 || name.any { it.isISOControl() }) {
            throw IOException("RomM returned an invalid device registration")
        }
        return Registered(deviceId, name)
    }
}
