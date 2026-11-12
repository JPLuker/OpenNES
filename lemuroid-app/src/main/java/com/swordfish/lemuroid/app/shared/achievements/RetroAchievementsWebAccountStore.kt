package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.swordfish.lemuroid.app.shared.security.SecretBlobReader
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
 * Stores a user-entered RetroAchievements WEB API key, not an emulator login
 * token. The key and username are AES-256-GCM encrypted in noBackupFilesDir.
 * This credential only enables viewing existing website progress; it must
 * never be passed to rc_client as an emulator credential.
 */
class RetroAchievementsWebAccountStore(context: Context) {
    private val secretFile =
        AtomicFile(File(context.applicationContext.noBackupFilesDir, "ra-web-account-v1.enc"))

    @Synchronized
    fun isConfigured(): Boolean = secretFile.baseFile.isFile

    @Synchronized
    fun save(
        username: String,
        apiKey: String,
    ) {
        val user = username.trim()
        val key = apiKey.trim()
        require(user.matches(Regex("[A-Za-z0-9_-]{2,64}"))) {
            "Enter a valid RetroAchievements username or ULID"
        }
        require(key.matches(Regex("[A-Za-z0-9]{16,128}"))) {
            "Enter a valid RetroAchievements web API key"
        }
        val plaintext =
            JSONObject()
                .put("username", user)
                .put("apiKey", key)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        val payload =
            ByteBuffer.allocate(2 + iv.size + encrypted.size)
                .put(VERSION.toByte())
                .put(iv.size.toByte())
                .put(iv)
                .put(encrypted)
                .array()
        val stream = secretFile.startWrite()
        try {
            stream.write(payload)
            secretFile.finishWrite(stream)
        } catch (e: Exception) {
            secretFile.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    fun load(): RetroAchievementsWebAccount? {
        if (!secretFile.baseFile.isFile) return null
        try {
            val bytes = secretFile.openRead().use { SecretBlobReader.readBounded(it) }
            require(bytes.size in 31..4096) { "Invalid credential payload" }
            val input = ByteBuffer.wrap(bytes)
            require((input.get().toInt() and 0xff) == VERSION) { "Unsupported credential format" }
            val ivLength = input.get().toInt() and 0xff
            require(ivLength == 12 && input.remaining() > ivLength + 15) { "Invalid credential IV" }
            val iv = ByteArray(ivLength)
            input.get(iv)
            val ciphertext = ByteArray(input.remaining())
            input.get(ciphertext)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val body = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
            val username = body.getString("username")
            val apiKey = body.getString("apiKey")
            return RetroAchievementsWebAccount(username, apiKey)
        } catch (e: Exception) {
            throw IllegalStateException("RetroAchievements web account needs to be entered again", e)
        }
    }

    @Synchronized
    fun disconnect() {
        secretFile.delete()
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore")
        keystore.load(null)
        (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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

    private companion object {
        const val VERSION = 1
        const val KEY_ALIAS = "opennes_retroachievements_web_key_v1"
    }
}

/** No secret may accidentally appear in debug logs via data-class toString(). */
class RetroAchievementsWebAccount(
    val username: String,
    val apiKey: String,
) {
    override fun toString(): String = "RetroAchievementsWebAccount([redacted])"
}
