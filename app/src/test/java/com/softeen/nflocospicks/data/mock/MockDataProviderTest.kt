package com.softeen.nflocospicks.data.mock

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import org.junit.Test

class MockDataProviderTest {

    // A local, isolated list so applyScores assertions don't depend on the exact
    // contents/statuses of the shared MockDataProvider.MOCK_GAMES fixture.
    private fun scheduledGame(id: String) = Game(
        id = id,
        weekId = "mock-week-01",
        homeTeam = "Home $id",
        awayTeam = "Away $id",
        homeTeamAbbr = "H$id",
        awayTeamAbbr = "A$id",
        kickoffTime = 0L,
        homeScore = null,
        awayScore = null,
        status = GameStatus.SCHEDULED,
        homeTeamRecord = null,
        awayTeamRecord = null
    )

    private val scheduledGames = listOf(scheduledGame("1"), scheduledGame("2"), scheduledGame("3"))

    @Test
    fun `applyScores marks only the games present in the score map as FINAL`() {
        val target = scheduledGames.first()
        val result = MockDataProvider.applyScores(scheduledGames, mapOf(target.id to (24 to 17)))

        val scored = result.first { it.id == target.id }
        assertThat(scored.homeScore).isEqualTo(24)
        assertThat(scored.awayScore).isEqualTo(17)
        assertThat(scored.status).isEqualTo(GameStatus.FINAL)

        result.filter { it.id != target.id }.forEach { untouched ->
            assertThat(untouched.homeScore).isNull()
            assertThat(untouched.awayScore).isNull()
            assertThat(untouched.status).isEqualTo(GameStatus.SCHEDULED)
        }
    }

    @Test
    fun `applyScores with an empty map returns every game unchanged`() {
        assertThat(MockDataProvider.applyScores(scheduledGames, emptyMap())).isEqualTo(scheduledGames)
    }

    @Test
    fun `applyScores ignores score entries whose game id is not in the list`() {
        val result = MockDataProvider.applyScores(scheduledGames, mapOf("not_a_real_game" to (10 to 3)))
        assertThat(result).isEqualTo(scheduledGames)
    }

    @Test
    fun `getPicksForMockUser returns one pick per game keyed by game id`() {
        val games = MockDataProvider.MOCK_GAMES
        val picks = MockDataProvider.getPicksForMockUser(userIndex = 0, games = games)

        assertThat(picks.keys).containsExactlyElementsIn(games.map { it.id })
        picks.forEach { (gameId, abbr) ->
            val game = games.first { it.id == gameId }
            assertThat(abbr).isAnyOf(game.homeTeamAbbr, game.awayTeamAbbr)
        }
    }

    @Test
    fun `getPicksForMockUser is deterministic by index-parity`() {
        val games = MockDataProvider.MOCK_GAMES

        val user0 = MockDataProvider.getPicksForMockUser(0, games)
        val user1 = MockDataProvider.getPicksForMockUser(1, games)

        games.forEachIndexed { gameIndex, game ->
            val expected0 = if (gameIndex % 2 == 0) game.homeTeamAbbr else game.awayTeamAbbr
            val expected1 = if ((1 + gameIndex) % 2 == 0) game.homeTeamAbbr else game.awayTeamAbbr
            assertThat(user0[game.id]).isEqualTo(expected0)
            assertThat(user1[game.id]).isEqualTo(expected1)
            // Consecutive users pick opposite sides of every game.
            assertThat(user0[game.id]).isNotEqualTo(user1[game.id])
        }
    }

    @Test
    fun `getPicksForMockUser is stable across calls`() {
        val games = MockDataProvider.MOCK_GAMES
        assertThat(MockDataProvider.getPicksForMockUser(3, games))
            .isEqualTo(MockDataProvider.getPicksForMockUser(3, games))
    }
}
