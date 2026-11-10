package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsNativeGameResolverTest {
    private val md5 = "0123456789abcdef0123456789abcdef"
    private val identity =
        NesRetroAchievementsHash.Identity(
            md5 = md5,
            console = NesRetroAchievementsHash.Console.NES,
        )

    private fun resolver(
        encoder: (String) -> Array<String>? = {
            arrayOf("https://retroachievements.org/dorequest.php", "r=gameid&m=$it")
        },
        parser: (ByteArray, Int) -> Int = { _, _ -> 1454 },
        request: suspend (String, String) -> RetroAchievementsNativeHttpTransport.Reply = { _, _ ->
            RetroAchievementsNativeHttpTransport.Reply(200, """{"Success":true,"GameID":1454}""".toByteArray())
        },
    ) = RetroAchievementsNativeGameResolver(encoder, parser, request)

    @Test
    fun serverVerifiedGameIdIsReportedAsRecognizedNotUnlocked() {
        runBlocking {
            var sentUrl = ""
            var sentForm = ""
            val result =
                resolver(
                    request = { url, form ->
                        sentUrl = url
                        sentForm = form
                        RetroAchievementsNativeHttpTransport.Reply(200, "{}".toByteArray())
                    },
                ).resolve(identity)
            assertEquals(RetroAchievementsNativeGameResolver.Result.VerifiedGame(1454), result)
            assertEquals("https://retroachievements.org/dorequest.php", sentUrl)
            assertEquals("r=gameid&m=$md5", sentForm)
        }
    }

    @Test
    fun zeroGameIdDoesNotInventAMatch() {
        runBlocking {
            assertEquals(
                RetroAchievementsNativeGameResolver.Result.NoMatch,
                resolver(parser = { _, _ -> 0 }).resolve(identity),
            )
        }
    }

    @Test
    fun malformedNativeResponseIsUnavailableNotNoMatch() {
        runBlocking {
            assertEquals(
                RetroAchievementsNativeGameResolver.Result.Unavailable,
                resolver(parser = { _, _ -> -1 }).resolve(identity),
            )
        }
    }

    @Test
    fun rejectsInsecureOrForeignEndpointsWithoutNetwork() {
        runBlocking {
            var called = false
            val forbidden = listOf(
                "http://retroachievements.org/dorequest.php",
                "https://example.org/dorequest.php",
                "https://retroachievements.org/login.php",
                "https://retroachievements.org/dorequest.php?r=gameid",
            )
            for (url in forbidden) {
                val result =
                    resolver(
                        encoder = { arrayOf(url, "r=gameid&m=$md5") },
                        request = { _, _ ->
                            called = true
                            RetroAchievementsNativeHttpTransport.Reply(200, ByteArray(0))
                        },
                    ).resolve(identity)
                assertEquals(RetroAchievementsNativeGameResolver.Result.Unavailable, result)
            }
            assertFalse(called)
        }
    }

    @Test
    fun rejectsNativeRequestForDifferentRomHash() {
        runBlocking {
            var called = false
            val result =
                resolver(
                    encoder = { arrayOf("https://retroachievements.org/dorequest.php", "r=gameid&m=" + "a".repeat(32)) },
                    request = { _, _ ->
                        called = true
                        RetroAchievementsNativeHttpTransport.Reply(200, ByteArray(0))
                    },
                ).resolve(identity)
            assertEquals(RetroAchievementsNativeGameResolver.Result.Unavailable, result)
            assertFalse(called)
        }
    }

    @Test
    fun rejectsInvalidLocalHashWithoutNetwork() {
        runBlocking {
            var called = false
            val result =
                resolver(
                    request = { _, _ ->
                        called = true
                        RetroAchievementsNativeHttpTransport.Reply(200, ByteArray(0))
                    },
                ).resolve(identity.copy(md5 = "bad"))
            assertEquals(RetroAchievementsNativeGameResolver.Result.Unavailable, result)
            assertFalse(called)
        }
    }

    @Test
    fun nonSuccessHttpStatusIsNotReportedAsNoMatch() {
        runBlocking {
            assertEquals(
                RetroAchievementsNativeGameResolver.Result.Unavailable,
                resolver(
                    request = { _, _ -> RetroAchievementsNativeHttpTransport.Reply(503, ByteArray(0)) },
                ).resolve(identity),
            )
        }
    }

    @Test
    fun cancellationPropagatesToGameScreenCoroutine() {
        runBlocking {
            var cancelled = false
            try {
                resolver(
                    request = { _, _ -> throw CancellationException("game closed") },
                ).resolve(identity)
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
    }
}
