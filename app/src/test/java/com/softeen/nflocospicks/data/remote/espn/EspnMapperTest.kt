package com.softeen.nflocospicks.data.remote.espn

import com.softeen.nflocospicks.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ── DTO builders ──────────────────────────────────────────────────────────────
// Construyen el árbol de DTOs mínimo necesario para cada test,
// evitando repetición sin introducir abstracciones innecesarias.

private fun statusType(name: String, completed: Boolean) =
    EspnStatusType(name = name, completed = completed)

private fun competitor(homeAway: String, abbr: String, score: String? = null) =
    EspnCompetitor(
        homeAway = homeAway,
        score    = score,
        team     = EspnTeam(displayName = "Team $abbr", abbreviation = abbr)
    )

private fun competition(
    homeAbbr: String   = "KC",
    awayAbbr: String   = "LV",
    homeScore: String? = null,
    awayScore: String? = null,
    statusName: String = "STATUS_SCHEDULED",
    completed: Boolean = false
) = EspnCompetition(
    competitors = listOf(
        competitor("home", homeAbbr, homeScore),
        competitor("away", awayAbbr, awayScore)
    ),
    status = EspnStatus(statusType(statusName, completed))
)

private fun event(
    id: String        = "g1",
    date: String      = "2025-11-24T18:00Z",
    homeAbbr: String  = "KC",
    awayAbbr: String  = "LV",
    homeScore: String? = null,
    awayScore: String? = null,
    statusName: String = "STATUS_SCHEDULED",
    completed: Boolean = false
) = EspnEvent(
    id           = id,
    date         = date,
    competitions = listOf(competition(homeAbbr, awayAbbr, homeScore, awayScore, statusName, completed))
)

private fun response(weekNumber: Int, vararg events: EspnEvent) =
    EspnScoreboardResponse(week = EspnWeek(weekNumber), events = events.toList())

// ── Tests ─────────────────────────────────────────────────────────────────────

class EspnMapperTest {

    // ── GameStatus mapping ────────────────────────────────────────────────────

    @Test
    fun `completed event maps to GameStatus FINAL`() {
        val games = response(12, event(completed = true, statusName = "STATUS_FINAL")).toDomain()

        assertEquals(GameStatus.FINAL, games.single().status)
    }

    @Test
    fun `STATUS_IN_PROGRESS name maps to GameStatus IN_PROGRESS`() {
        val games = response(12, event(completed = false, statusName = "STATUS_IN_PROGRESS")).toDomain()

        assertEquals(GameStatus.IN_PROGRESS, games.single().status)
    }

    @Test
    fun `any other status maps to GameStatus SCHEDULED`() {
        val games = response(12, event(completed = false, statusName = "STATUS_SCHEDULED")).toDomain()

        assertEquals(GameStatus.SCHEDULED, games.single().status)
    }

    // ── weekId formatting ─────────────────────────────────────────────────────

    @Test
    fun `single-digit week number is zero-padded in weekId`() {
        // Semana 3 del 2025 → "2025-week-03"
        val games = response(3, event(date = "2025-09-22T13:00Z")).toDomain()

        assertEquals("2025-week-03", games.single().weekId)
    }

    @Test
    fun `double-digit week number is not padded in weekId`() {
        val games = response(12, event(date = "2025-11-24T18:00Z")).toDomain()

        assertEquals("2025-week-12", games.single().weekId)
    }

    @Test
    fun `year in weekId is derived from kickoff date not from the device clock`() {
        // Un juego de playoffs en enero 2026 debe producir "2026-week-20"
        val games = response(20, event(date = "2026-01-12T21:00Z")).toDomain()

        assertEquals("2026-week-20", games.single().weekId)
    }

    // ── Score parsing ─────────────────────────────────────────────────────────

    @Test
    fun `string scores are parsed to Int in the domain model`() {
        val games = response(
            12,
            event(homeScore = "28", awayScore = "14", completed = true, statusName = "STATUS_FINAL")
        ).toDomain()

        val game = games.single()
        assertEquals(28, game.homeScore)
        assertEquals(14, game.awayScore)
    }

    @Test
    fun `null scores remain null in the domain model`() {
        val games = response(12, event(homeScore = null, awayScore = null)).toDomain()

        val game = games.single()
        assertNull(game.homeScore)
        assertNull(game.awayScore)
    }

    // ── Error resilience ──────────────────────────────────────────────────────

    @Test
    fun `malformed event with empty competitions list is silently discarded`() {
        val valid    = event(id = "g_valid")
        val malformed = EspnEvent(id = "g_bad", date = "2025-11-24T18:00Z", competitions = emptyList())

        val games = response(12, valid, malformed).toDomain()

        assertEquals(1, games.size)
        assertEquals("g_valid", games.single().id)
    }

    // ── Team assignment ───────────────────────────────────────────────────────

    @Test
    fun `homeAway field correctly assigns home and away team abbreviations`() {
        val games = response(12, event(homeAbbr = "KC", awayAbbr = "SF")).toDomain()

        val game = games.single()
        assertEquals("KC", game.homeTeamAbbr)
        assertEquals("SF", game.awayTeamAbbr)
    }
}
