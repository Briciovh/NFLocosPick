package com.softeen.nflocospicks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.HistoryRepository
import com.softeen.nflocospicks.domain.repository.LeaderboardRepository
import com.softeen.nflocospicks.domain.repository.PickRepository
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Read-only query use cases: each is a thin pass-through to a repository. Tests
 * pin that the right repository method receives the arguments unchanged and the
 * result (or Flow) is returned as-is.
 */
class QueryUseCasesTest {

    private fun game(id: String) = Game(
        id = id, weekId = "2025-week-01", homeTeam = "H", awayTeam = "A",
        homeTeamAbbr = "H$id", awayTeamAbbr = "A$id", kickoffTime = 0L,
        homeScore = null, awayScore = null, status = GameStatus.SCHEDULED,
        homeTeamRecord = null, awayTeamRecord = null
    )

    @Test
    fun `GetCurrentWeekGamesUseCase returns the repository games for the group`() = runBlocking {
        val repo = mockk<ScheduleRepository>()
        val games = listOf(game("1"), game("2"))
        coEvery { repo.getCurrentWeekGames("g1") } returns games

        val result = GetCurrentWeekGamesUseCase(repo).invoke("g1")

        assertThat(result).isEqualTo(games)
        coVerify(exactly = 1) { repo.getCurrentWeekGames("g1") }
    }

    @Test
    fun `GetGamesForWeekUseCase forwards seasonType and weekNumber`() = runBlocking {
        val repo = mockk<ScheduleRepository>()
        val games = listOf(game("1"))
        coEvery { repo.getGamesForWeek(SeasonType.PRESEASON, 3) } returns games

        val result = GetGamesForWeekUseCase(repo).invoke(SeasonType.PRESEASON, 3)

        assertThat(result).isEqualTo(games)
        coVerify(exactly = 1) { repo.getGamesForWeek(SeasonType.PRESEASON, 3) }
    }

    @Test
    fun `GetGroupsForUserUseCase re-emits the repository flow`() = runBlocking {
        val repo = mockk<GroupRepository>()
        val groups = listOf(Group("g1", "Los Locos", "ABC123", "u1", listOf("u1")))
        every { repo.getGroupsForUser("u1") } returns flowOf(groups)

        val emitted = GetGroupsForUserUseCase(repo).invoke("u1").first()

        assertThat(emitted).isEqualTo(groups)
        verify(exactly = 1) { repo.getGroupsForUser("u1") }
    }

    @Test
    fun `GetLeaderboardUseCase re-emits the repository flow`() = runBlocking {
        val repo = mockk<LeaderboardRepository>()
        val entries = listOf(
            LeaderboardEntry("u1", "Alex", null, regularPoints = 10, preseasonPoints = 0, weeklyBreakdown = emptyMap(), rank = 1)
        )
        every { repo.getLeaderboard("g1") } returns flowOf(entries)

        val emitted = GetLeaderboardUseCase(repo).invoke("g1").first()

        assertThat(emitted).isEqualTo(entries)
        verify(exactly = 1) { repo.getLeaderboard("g1") }
    }

    @Test
    fun `GetPickHistoryUseCase forwards groupId and userId`() = runBlocking {
        val repo = mockk<HistoryRepository>()
        val history = listOf(WeekHistoryEntry("2025-week-01", weekPoints = 2, picks = emptyList()))
        coEvery { repo.getPickHistory("g1", "u1") } returns history

        val result = GetPickHistoryUseCase(repo).invoke("g1", "u1")

        assertThat(result).isEqualTo(history)
        coVerify(exactly = 1) { repo.getPickHistory("g1", "u1") }
    }

    @Test
    fun `GetWeekPicksUseCase forwards groupId weekId userId and returns the pick map`() = runBlocking {
        val repo = mockk<PickRepository>()
        val picks = mapOf("401" to Pick("401", "KC", isCorrect = null, scoredAt = null))
        coEvery { repo.getPicksForWeek("g1", "2025-week-01", "u1") } returns picks

        val result = GetWeekPicksUseCase(repo).invoke("g1", "2025-week-01", "u1")

        assertThat(result).isEqualTo(picks)
        coVerify(exactly = 1) { repo.getPicksForWeek("g1", "2025-week-01", "u1") }
    }
}
