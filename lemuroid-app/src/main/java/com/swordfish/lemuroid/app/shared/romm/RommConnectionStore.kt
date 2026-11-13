package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.swordfish.lemuroid.app.shared.security.SecretBlobReader
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * RomM API tokens live only as AES-GCM ciphertext in noBackupFilesDir.
 * Neither the server password nor its bearer token is written to ordinary
 * preferences or included in Android Auto Backup.
 */
class RommConnectionStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("opennes_romm", Context.MODE_PRIVATE)
    private val secretFile = AtomicFile(File(app.noBackupFilesDir, "romm-client-token.enc"))

    @Synchronized
    fun configuredServer(): String? = prefs.getString(SERVER_KEY, null)

    @Synchronized
    fun hasSavedToken(): Boolean = secretFile.baseFile.isFile

    /** Changes with every login, even if the RomM server URL stays the same. */
    @Synchronized
    fun credentialRevision(): String? = prefs.getString(CREDENTIAL_REVISION_KEY, null)

    @Synchronized
    fun save(
        serverUrl: String,
        token: String,
    ) {
        val url = RommApiClient.validatedServerUrl(serverUrl)
        val trimmed = token.trim()
        require(
            trimmed.isNotEmpty() &&
                trimmed.length <= 2048 &&
                trimmed.none { it.isWhitespace() || it.isISOControl() },
        ) {
            "Enter a valid RomM client token"
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val bytes =
            ByteBuffer.allocate(2 + iv.size + encrypted.size)
                .put(FORMAT_VERSION.toByte())
                .put(iv.size.toByte())
                .put(iv)
                .put(encrypted)
                .array()
        val stream = secretFile.startWrite()
        try {
            stream.write(bytes)
            secretFile.finishWrite(stream)
            prefs.edit()
                .putString(SERVER_KEY, url)
                .putString(CREDENTIAL_REVISION_KEY, UUID.randomUUID().toString())
                .remove(VERIFIED_ACCOUNT_ID_KEY)
                .remove(VERIFIED_ACCOUNT_SERVER_KEY)
                .remove(VERIFIED_ACCOUNT_REVISION_KEY)
                .apply()
            // A token rotation invalidates metadata from the previous account.
            RommCatalogueCache(app).clear()
            RommLibraryCatalog.invalidateSession()
            // A token rotation must never reuse an old account's device ID.
            RommDeviceIdentityStore(app).clearBinding()
        } catch (e: Exception) {
            secretFile.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    fun load(): RommConnection? {
        val serverUrl = configuredServer() ?: return null
        if (!secretFile.baseFile.isFile) return null
        try {
            val payload =
                ByteBuffer.wrap(secretFile.openRead().use { SecretBlobReader.readBounded(it) })
            require(payload.remaining() >= 2) { "Invalid credential format" }
            require((payload.get().toInt() and 0xFF) == FORMAT_VERSION) {
                "Unsupported credential format"
            }
            val ivSize = payload.get().toInt() and 0xFF
            require(ivSize == 12 && payload.remaining() > ivSize + 15) {
                "Invalid credential size"
            }
            val iv = ByteArray(ivSize)
            payload.get(iv)
            val encrypted = ByteArray(payload.remaining())
            payload.get(encrypted)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val token = String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
            return RommConnection(serverUrl, token)
        } catch (e: Exception) {
            // A restored backup cannot decrypt an Android Keystore key.
            // Require a new user-entered token; never fall back to plaintext.
            throw IllegalStateException("RomM credentials need to be entered again", e)
        }
    }

    /**
     * A live /api/users/me response pins a read-only account ID to the
     * current encrypted credential revision. It is NOT authorization for
     * offline sync or future remote writes.
     */
    @Synchronized
    internal fun pinVerifiedAccount(
        connection: RommConnection,
        profile: RommAccountIdentity.Profile,
    ): Boolean {
        val id = profile.userId ?: return false
        val current = load() ?: return false
        val fingerprint = RommServerIdentity.fingerprint(current.serverUrl) ?: return false
        if (current.serverUrl != RommApiClient.validatedServerUrl(connection.serverUrl) ||
            !MessageDigest.isEqual(
                current.token.toByteArray(Charsets.UTF_8),
                connection.token.toByteArray(Charsets.UTF_8),
            )
        ) {
            return false
        }
        val revision = credentialRevision() ?: return false
        prefs.edit()
            .putLong(VERIFIED_ACCOUNT_ID_KEY, id)
            .putString(VERIFIED_ACCOUNT_SERVER_KEY, fingerprint)
            .putString(VERIFIED_ACCOUNT_REVISION_KEY, revision)
            .apply()
        return true
    }

    /** Offline display/provenance only. Re-verify online before any future sync. */
    @Synchronized
    fun lastVerifiedUserId(): Long? {
        val userId = prefs.getLong(VERIFIED_ACCOUNT_ID_KEY, 0L).takeIf { it > 0L }
        val serverFingerprint = RommServerIdentity.fingerprint(configuredServer())
        val revision = credentialRevision()
        return if (
            RommAccountIdentity.matchesPin(
                serverFingerprint = serverFingerprint,
                credentialRevision = revision,
                userId = userId,
                pinnedServerFingerprint = prefs.getString(VERIFIED_ACCOUNT_SERVER_KEY, null),
                pinnedCredentialRevision = prefs.getString(VERIFIED_ACCOUNT_REVISION_KEY, null),
                pinnedUserId = userId,
            )
        ) {
            userId
        } else {
            null
        }
    }

    @Synchronized
    fun disconnect() {
        secretFile.delete()
        prefs.edit()
            .remove(SERVER_KEY)
            .remove(CREDENTIAL_REVISION_KEY)
            .remove(VERIFIED_ACCOUNT_ID_KEY)
            .remove(VERIFIED_ACCOUNT_SERVER_KEY)
            .remove(VERIFIED_ACCOUNT_REVISION_KEY)
            .apply()
        RommCatalogueCache(app).clear()
        RommLibraryCatalog.invalidateSession()
        RommDeviceIdentityStore(app).clearBinding()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val SERVER_KEY = "server_url"
        private const val CREDENTIAL_REVISION_KEY = "credential_revision"
        private const val VERIFIED_ACCOUNT_ID_KEY = "verified_user_id"
        private const val VERIFIED_ACCOUNT_SERVER_KEY = "verified_user_server"
        private const val VERIFIED_ACCOUNT_REVISION_KEY = "verified_user_revision"
        private const val KEY_ALIAS = "opennes_romm_client_token_v1"
        private const val FORMAT_VERSION = 1
    }
}

/** Never expose the token through auto-generated data-class toString(). */
class RommConnection(val serverUrl: String, val token: String) {
    override fun toString(): String = "RommConnection(serverUrl=[configured], token=[redacted])"
}
