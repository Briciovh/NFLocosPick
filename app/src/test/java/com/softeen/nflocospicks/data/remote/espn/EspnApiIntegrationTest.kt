package com.softeen.nflocospicks.data.remote.espn

import com.softeen.nflocospicks.domain.model.GameStatus
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Integration test: HTTP response → Gson deserialization → EspnMapper → domain Games.
 *
 * Uses a real Retrofit + GsonConverterFactory pointed at MockWebServer.
 * Catches bugs that unit mapper tests miss: misconfigured @SerializedName,
 * missing Gson annotations, wrong Content-Type negotiation, etc.
 */
class EspnApiIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var service: EspnApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // Mirror NetworkModule's Retrofit config (same GsonConverterFactory, no auth)
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EspnApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun jsonResponse(body: String) =
        MockResponse()
            .setBody(body)
            .setHeader("Content-Type", "application/json")

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `full fixture deserializes to correct domain Games`() = runTest {
        server.enqueue(jsonResponse(fixture("espn_scoreboard.json")))

        val games = service.getScoreboard().toDomain()

        assertEquals(2, games.size)
    }

    @Test
    fun `FINAL game in fixture has correct fields after mapping`() = runTest {
        server.enqueue(jsonResponse(fixture("espn_scoreboard.json")))

        val game = service.getScoreboard().toDomain().first { it.id == "401671876" }

        assertEquals("2025-week-12",   game.weekId)
        assertEquals(GameStatus.FINAL, game.status)
        assertEquals("KC",             game.homeTeamAbbr)
        assertEquals("LV",             game.awayTeamAbbr)
        assertEquals(28,               game.homeScore)
        assertEquals(14,               game.awayScore)
    }

    @Test
    fun `SCHEDULED game in fixture has null scores after mapping`() = runTest {
        server.enqueue(jsonResponse(fixture("espn_scoreboard.json")))

        val game = service.getScoreboard().toDomain().first { it.id == "401671877" }

        assertEquals(GameStatus.SCHEDULED, game.status)
        assertNull(game.homeScore)
        assertNull(game.awayScore)
    }

    @Test
    fun `empty events array produces an empty list without error`() = runTest {
        server.enqueue(jsonResponse("""{"week":{"number":1},"events":[]}"""))

        val games = service.getScoreboard().toDomain()

        assertTrue(games.isEmpty())
    }

    @Test
    fun `HTTP 500 response propagates as an exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        var thrown: Exception? = null
        try {
            service.getScoreboard()
        } catch (e: Exception) {
            thrown = e
        }

        assertNotNull("Una respuesta HTTP 500 debe lanzar excepción", thrown)
    }
}
