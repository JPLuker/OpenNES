package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A registered RomM device belongs to one authenticated account and one
 * server. Both the installation seed and binding live in Android no-backup
 * storage: cloning the app to another handset must not clone its device ID.
 * No token, hardware MAC, IP address or real hostname is persisted here.
 */
internal class RommDeviceIdentityStore(context: Context) {
    private val root = context.applicationContext.noBackupFilesDir
    private val seedFile = AtomicFile(File(root, "romm-device-seed-v1.bin"))
    private val bindingFile = AtomicFile(File(root, "romm-device-binding-v1.json"))

    @Synchronized
    fun alias(
        serverFingerprint: String,
        userId: Long,
    ): String = RommDeviceAlias.derive(seed(), serverFingerprint, userId)

    @Synchronized
    fun pinnedDevice(
        serverFingerprint: String?,
        credentialRevision: String?,
        userId: Long?,
    ): String? {
        if (!RommServerIdentity.validFingerprint(serverFingerprint.orEmpty()) ||
            credentialRevision.isNullOrBlank() || userId == null || userId <= 0L ||
            !bindingFile.baseFile.isFile
        ) {
            return null
        }
        return runCatching {
            val raw =
                bindingFile.openRead().use {
                    RommSafeStreamRead.readBounded(it, 4 * 1024)
                }
            val binding = JSONObject(String(raw, Charsets.UTF_8))
            val deviceId = binding.getString("device_id")
            if (
                binding.getInt("version") != 1 ||
                binding.getString("server") != serverFingerprint ||
                binding.getString("revision") != credentialRevision ||
                binding.getLong("user_id") != userId ||
                !RommDeviceAlias.validDeviceId(deviceId)
            ) {
                null
            } else {
                deviceId
            }
        }.getOrNull()
    }

    @Synchronized
    fun pin(
        serverFingerprint: String,
        credentialRevision: String,
        userId: Long,
        registeredId: String,
    ) {
        require(
            RommServerIdentity.validFingerprint(serverFingerprint) &&
                credentialRevision.isNotBlank() &&
                credentialRevision.length <= 128 &&
                userId > 0 &&
                RommDeviceAlias.validDeviceId(registeredId),
        ) { "Unverified RomM device registration" }
        val json =
            JSONObject()
                .put("version", 1)
                .put("server", serverFingerprint)
                .put("revision", credentialRevision)
                .put("user_id", userId)
                .put("device_id", registeredId)
                .toString().toByteArray(Charsets.UTF_8)
        val sink = bindingFile.startWrite()
        try {
            sink.write(json)
            bindingFile.finishWrite(sink)
        } catch (e: Exception) {
            bindingFile.failWrite(sink)
            throw e
        }
    }

    @Synchronized
    fun clearBinding() {
        bindingFile.delete()
    }

    private fun seed(): ByteArray {
        if (seedFile.baseFile.isFile) {
            val existing =
                seedFile.openRead().use {
                    RommSafeStreamRead.readBounded(it, 64)
                }
            if (existing.size != 32) {
                throw IOException("Private device identity needs to be reset")
            }
            return existing
        }
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val stream = seedFile.startWrite()
        try {
            stream.write(random)
            seedFile.finishWrite(stream)
        } catch (e: Exception) {
            seedFile.failWrite(stream)
            throw e
        }
        return random
    }
}

/** Stable across updates; different for each server/account and installation. */
internal object RommDeviceAlias {
    private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun validDeviceId(value: String): Boolean = idPattern.matches(value)

    fun derive(
        installationSeed: ByteArray,
        serverFingerprint: String,
        accountId: Long,
    ): String {
        require(installationSeed.size == 32) { "Invalid private installation seed" }
        require(RommServerIdentity.validFingerprint(serverFingerprint) && accountId > 0L) {
            "Verified RomM server/account required"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(installationSeed)
        digest.update(serverFingerprint.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(accountId.toString().toByteArray(Charsets.UTF_8))
        val pseudonym =
            digest.digest().take(8)
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return "opennes-$pseudonym"
    }
}
