package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetroAchievementsNativeHttpTransportTest {
    @Test
    fun acceptsOnlyOfficialHttpsAuthority() {
        val url =
            RetroAchievementsNativeHttpTransport.validatedEndpoint(
                "https://retroachievements.org/dorequest.php?r=login",
            )
        assertEquals("retroachievements.org", url.host)
    }

    @Test
    fun rejectsRedirectTargetsAndUntrustedHosts() {
        val rejected =
            listOf(
                "http://retroachievements.org/dorequest.php",
                "https://retroachievements.org.evil.example/dorequest.php",
                "https://example.org/dorequest.php",
                "https://retroachievements.org:8443/dorequest.php",
                "https://alice:secret@retroachievements.org/dorequest.php",
            )
        rejected.forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) {
                RetroAchievementsNativeHttpTransport.validatedEndpoint(unsafe)
            }
        }
    }
    @Test
    fun postsToOfficialHostWithExpectedClientIdentification() = runBlocking {
        var seenMethod = ""
        var seenAgent = ""
        val fake =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    seenMethod = chain.request().method
                    seenAgent = chain.request().header("User-Agent").orEmpty()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        val response =
            RetroAchievementsNativeHttpTransport(fake).execute(
                "https://retroachievements.org/dorequest.php",
                "r=login",
            )
        assertEquals(200, response.statusCode)
        assertEquals("{}", String(response.body))
        assertEquals("POST", seenMethod)
        assertEquals(true, seenAgent.startsWith("OpenNES/0.1.0"))
    }

    @Test
    fun rejectsOversizedServerResponse() = runBlocking {
        val fake =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(2 * 1024 * 1024 + 1).toResponseBody())
                        .build()
                }
                .build()
        var refused = false
        try {
            RetroAchievementsNativeHttpTransport(fake).execute(
                "https://retroachievements.org/dorequest.php",
                null,
            )
        } catch (_: java.io.IOException) {
            refused = true
        }
        assertEquals(true, refused)
    }

}
