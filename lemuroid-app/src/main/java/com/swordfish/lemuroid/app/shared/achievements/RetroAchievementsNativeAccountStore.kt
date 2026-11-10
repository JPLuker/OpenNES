package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dedicated emulator login token store for future rc_client authentication.
 *
 * This is NOT the read-only website API key, and is deliberately kept in a
 * separate Android Keystore namespace and no-backup encrypted file.
 * Passwords are never persisted; save only rc_client's confirmed login token.
 * Until native authentication exists, this store has no player-facing caller.
 */
internal class RetroAchievementsNativeAccountStore(context: Context) {
    private val file =
        AtomicFile(File(context.applicationContext.noBackupFilesDir, "ra-native-account-v1.enc"))

    @Synchronized
    fun isConfigured(): Boolean = file.baseFile.isFile

    @Synchronized
    fun saveConfirmedToken(
        username: String,
        confirmedToken: String,
    ) {
        require(validUsername(username)) { "Invalid emulator username" }
        require(validToken(confirmedToken)) { "Invalid emulator login token" }
        val plaintext =
            JSONObject()
                .put("username", username)
                .put("token", confirmedToken)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        val payload =
            ByteBuffer.allocate(2 + iv.size + encrypted.size)
                .put(FORMAT_VERSION.toByte())
                .put(iv.size.toByte())
                .put(iv)
                .put(encrypted)
                .array()
        val stream = file.startWrite()
        try {
            stream.write(payload)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    fun load(): NativeCredentials? {
        if (!file.baseFile.isFile) return null
        try {
            val bytes = file.readFully()
            require(bytes.size in 31..4096) { "Invalid native credential payload" }
            val buffer = ByteBuffer.wrap(bytes)
            require((buffer.get().toInt() and 0xFF) == FORMAT_VERSION) { "Unsupported credential version" }
            val ivSize = buffer.get().toInt() and 0xFF
            require(ivSize == 12 && buffer.remaining() > ivSize + 15) { "Invalid credential IV" }
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val record = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
            val username = record.getString("username")
            val token = record.getString("token")
            require(validUsername(username) && validToken(token)) { "Invalid saved credential" }
            return NativeCredentials(username, token)
        } catch (e: Exception) {
            // KeyStore keys are intentionally not restored across devices.
            throw IllegalStateException("RetroAchievements emulator login must be renewed", e)
        }
    }

    @Synchronized
    fun disconnect() {
        file.delete()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
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
        private const val FORMAT_VERSION = 1
        private const val KEY_ALIAS = "opennes_ra_native_token_v1"
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_-]{2,64}")

        fun validUsername(username: String) = username.matches(USERNAME_PATTERN)

        fun validToken(token: String): Boolean =
            token.length in 16..2048 && token.none { it.isWhitespace() || it.isISOControl() }
    }
}

/** Avoid accidentally exposing tokens through loggers or data-class toString(). */
internal class NativeCredentials(
    val username: String,
    val token: String,
) {
    override fun toString(): String = "NativeCredentials([redacted])"
}
