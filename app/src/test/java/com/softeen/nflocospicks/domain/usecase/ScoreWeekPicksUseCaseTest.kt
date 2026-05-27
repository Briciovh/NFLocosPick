package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import com.softeen.nflocospicks.domain.repository.ScoringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ── Fakes ────────────────────────────────────────────────────────────────────

private class FakeScheduleRepository(
    private val games: List<Game>
) : ScheduleRepository {
    override suspend fun getCurrentWeekGames(groupId: String) = games
}

private class FakeGroupRepository(
    private val group: Group = DEFAULT_GROUP
) : GroupRepository {
    override suspend fun createGroup(name: String, creatorUserId: String): Group = group
    override suspend fun joinGroup(inviteCode: String, userId: String): Group = group
    override fun getGroupsForUser(userId: String): Flow<List<Group>> =
        throw NotImplementedError("not needed in unit tests")
    override suspend fun getGroupById(groupId: String): Group = group
}

private class FakeScoringRepository(
    private val returnValue: Int = 3
) : ScoringRepository {
    var wasCalled            = false
    var lastGroupId          : String?              = null
    var lastWeekId           : String?              = null
    var lastWinners          : Map<String, String?> = emptyMap()
    var lastMemberIds        : List<String>         = emptyList()

    override suspend fun scoreWeek(
        groupId  : String,
        weekId   : String,
        memberIds: List<String>,
        winners  : Map<String, String?>
    ): Int {
        wasCalled     = true
        lastGroupId   = groupId
        lastWeekId    = weekId
        lastMemberIds = memberIds
        lastWinners   = winners
        return returnValue
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private val DEFAULT_GROUP = Group(
    id         = "group1",
    name       = "Los Locos",
    inviteCode = "ABC123",
    createdBy  = "user1",
    memberIds  = listOf("user1", "user2")
)

private fun makeGame(
    id          : String,
    weekId      : String      = "2025-week-12",
    status      : GameStatus  = GameStatus.SCHEDULED,
    homeTeamAbbr: String      = "KC",
    awayTeamAbbr: String      = "LV",
    homeScore   : Int?        = null,
    awayScore   : Int?        = null
) = Game(
    id           = id,
    weekId       = weekId,
    homeTeam     = "Kansas City Chiefs",
    awayTeam     = "Las Vegas Raiders",
    homeTeamAbbr = homeTeamAbbr,
    awayTeamAbbr = awayTeamAbbr,
    kickoffTime  = 1_700_000_000_000L,
    homeScore    = homeScore,
    awayScore    = awayScore,
    status       = status
)

private fun makeUseCase(
    games    : List<Game>             = emptyList(),
    scoring  : FakeScoringRepository  = FakeScoringRepository(),
    group    : Group                  = DEFAULT_GROUP
) = Triple(
    ScoreWeekPicksUseCase(
        scheduleRepository = FakeScheduleRepository(games),
        groupRepository    = FakeGroupRepository(group),
        scoringRepository  = scoring
    ),
    scoring,
    group
)

// ── Tests ────────────────────────────────────────────────────────────────────

class ScoreWeekPicksUseCaseTest {

    /**
     * Test 1 — Lista vacía → retorna 0 sin llamar a ScoringRepository.
     */
    @Test
    fun `empty game list returns 0 and does not call ScoringRepository`() = runBlocking {
        val (useCase, scoring) = makeUseCase(games = emptyList())

        val result = useCase("group1")

        assertEquals(0, result)
        assertFalse("ScoringRepository no debe llamarse con lista vacía", scoring.wasCalled)
    }

    /**
     * Test 2 — Todos los juegos en SCHEDULED → retorna 0 sin llamar a ScoringRepository.
     */
    @Test
    fun `all SCHEDULED games returns 0 and does not call ScoringRepository`() = runBlocking {
        val games = listOf(
            makeGame("g1", status = GameStatus.SCHEDULED),
            makeGame("g2", status = GameStatus.IN_PROGRESS)
        )
        val (useCase, scoring) = makeUseCase(games = games)

        val result = useCase("group1")

        assertEquals(0, result)
        assertFalse(scoring.wasCalled)
    }

    /**
     * Test 3 — Un juego FINAL y uno SCHEDULED → ScoringRepository es llamado
     * y retorna el valor que devuelva el fake.
     */
    @Test
    fun `at least one FINAL game delegates to ScoringRepository and returns its value`() = runBlocking {
        val games = listOf(
            makeGame("g1", status = GameStatus.FINAL, homeScore = 28, awayScore = 14),
            makeGame("g2", status = GameStatus.SCHEDULED)
        )
        val scoring = FakeScoringRepository(returnValue = 5)
        val (useCase, _) = makeUseCase(games = games, scoring = scoring)

        val result = useCase("group1")

        assertEquals(5, result)
        assertTrue("ScoringRepository debe ser llamado", scoring.wasCalled)
        assertEquals("group1", scoring.lastGroupId)
    }

    /**
     * Test 4 — Juego FINAL con empate (homeScore == awayScore) →
     * winners[gameId] debe ser null (nadie acierta en empate).
     */
    @Test
    fun `tied FINAL game maps to null winner`() = runBlocking {
        val games = listOf(
            makeGame(
                id           = "g_tie",
                status       = GameStatus.FINAL,
                homeTeamAbbr = "KC",
                awayTeamAbbr = "LV",
                homeScore    = 17,
                awayScore    = 17
            )
        )
        val scoring = FakeScoringRepository()
        val (useCase, _) = makeUseCase(games = games, scoring = scoring)

        useCase("group1")

        assertTrue(scoring.wasCalled)
        assertTrue("El mapa de winners debe contener el gameId", scoring.lastWinners.containsKey("g_tie"))
        assertNull("El ganador de un empate debe ser null", scoring.lastWinners["g_tie"])
    }

    /**
     * Test 5 — El weekId se extrae del primer juego de la lista.
     */
    @Test
    fun `weekId is derived from the first game in the list`() = runBlocking {
        val games = listOf(
            makeGame("g1", weekId = "2025-week-12", status = GameStatus.FINAL,
                homeScore = 21, awayScore = 10),
            makeGame("g2", weekId = "2025-week-12", status = GameStatus.SCHEDULED)
        )
        val scoring = FakeScoringRepository()
        val (useCase, _) = makeUseCase(games = games, scoring = scoring)

        useCase("group1")

        assertEquals("2025-week-12", scoring.lastWeekId)
    }

    /**
     * Test 6 — El ganador se determina correctamente:
     * homeScore > awayScore → homeTeamAbbr; awayScore > homeScore → awayTeamAbbr.
     */
    @Test
    fun `winner is home team when homeScore is greater and away team when awayScore is greater`() = runBlocking {
        val games = listOf(
            makeGame("home_wins", status = GameStatus.FINAL,
                homeTeamAbbr = "KC", awayTeamAbbr = "LV",
                homeScore = 30, awayScore = 20),
            makeGame("away_wins", status = GameStatus.FINAL,
                homeTeamAbbr = "SF", awayTeamAbbr = "DAL",
                homeScore = 10, awayScore = 27)
        )
        val scoring = FakeScoringRepository()
        val (useCase, _) = makeUseCase(games = games, scoring = scoring)

        useCase("group1")

        assertEquals("KC",  scoring.lastWinners["home_wins"])
        assertEquals("DAL", scoring.lastWinners["away_wins"])
    }

    /**
     * Test 7 — Los memberIds del grupo se pasan correctamente a ScoringRepository.
     */
    @Test
    fun `memberIds from group are forwarded to ScoringRepository`() = runBlocking {
        val group = DEFAULT_GROUP.copy(memberIds = listOf("alice", "bob", "carlos"))
        val games = listOf(
            makeGame("g1", status = GameStatus.FINAL, homeScore = 14, awayScore = 7)
        )
        val scoring = FakeScoringRepository()
        val (useCase, _) = makeUseCase(games = games, scoring = scoring, group = group)

        useCase("group1")

        assertEquals(listOf("alice", "bob", "carlos"), scoring.lastMemberIds)
    }
}
