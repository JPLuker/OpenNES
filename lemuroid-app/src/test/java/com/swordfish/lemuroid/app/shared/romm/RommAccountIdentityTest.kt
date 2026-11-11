package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RommAccountIdentityTest {
    private val serverA = "a".repeat(64)
    private val serverB = "b".repeat(64)

    @Test
    fun accountProfileRecordsNumericUserIdAndDisplayName() {
        val account = RommAccountIdentity.parseProfile(JSONObject("""{"id":42,"username":" player1 "}"""))
        assertEquals("player1", account.username)
        assertEquals(42L, account.userId)
    }

    @Test
    fun invalidAccountIdCannotBecomeVerifiedSyncIdentity() {
        val missing = RommAccountIdentity.parseProfile(JSONObject("""{"username":"player1"}"""))
        assertNull(missing.userId)
        val textId = RommAccountIdentity.parseProfile(JSONObject("""{"id":"42","username":"player1"}"""))
        assertNull(textId.userId)
        val fractional = RommAccountIdentity.parseProfile(JSONObject("""{"id":1.5,"username":"player1"}"""))
        assertNull(fractional.userId)
        val negative = RommAccountIdentity.parseProfile(JSONObject("""{"id":-1,"username":"player1"}"""))
        assertNull(negative.userId)
        val overflow =
            RommAccountIdentity.parseProfile(
                JSONObject("""{"id":9999999999999999999999,"username":"player1"}"""),
            )
        assertNull(overflow.userId)
    }

    @Test
    fun missingOrUnsafeDisplayNameFailsClosed() {
        assertThrows(IOException::class.java) {
            RommAccountIdentity.parseProfile(JSONObject("""{"id":5,"username":""}"""))
        }
        assertThrows(IOException::class.java) {
            RommAccountIdentity.parseProfile(
                JSONObject().put("id", 5).put("username", "a" + '\n' + "b"),
            )
        }
        assertThrows(IOException::class.java) {
            RommAccountIdentity.parseProfile(
                JSONObject().put("id", 5).put("username", "x".repeat(129)),
            )
        }
    }

    @Test
    fun credentialRevisionAndAccountAndServerAllMustMatch() {
        fun matches(
            activeServer: String = serverA,
            activeRevision: String = "revision1",
            activeUser: Long = 42L,
        ) = RommAccountIdentity.matchesPin(
            serverFingerprint = activeServer,
            credentialRevision = activeRevision,
            userId = activeUser,
            pinnedServerFingerprint = serverA,
            pinnedCredentialRevision = "revision1",
            pinnedUserId = 42L,
        )
        assertTrue(matches())
        assertFalse(matches(activeServer = serverB))
        assertFalse(matches(activeRevision = "revision2"))
        assertFalse(matches(activeUser = 43L))
        assertFalse(matches(activeServer = "bad"))
    }
}
