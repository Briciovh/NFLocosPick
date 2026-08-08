package com.softeen.nflocospicks.presentation.leaderboard

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.SeasonType
import org.junit.Test

class LeaderboardRankingTest {

    private fun entry(userId: String, regular: Int, preseason: Int) = LeaderboardEntry(
        userId          = userId,
        displayName     = userId,
        photoUrl        = null,
        regularPoints   = regular,
        preseasonPoints = preseason,
        weeklyBreakdown = emptyMap(),
        rank            = 0
    )

    @Test
    fun `rankedFor orders by regular points when REGULAR selected`() {
        val entries = listOf(
            entry("a", regular = 5, preseason = 20),
            entry("b", regular = 10, preseason = 1)
        )

        val ranked = entries.rankedFor(SeasonType.REGULAR)

        assertThat(ranked.map { it.userId }).containsExactly("b", "a").inOrder()
        assertThat(ranked.first { it.userId == "b" }.rank).isEqualTo(1)
        assertThat(ranked.first { it.userId == "a" }.rank).isEqualTo(2)
    }

    @Test
    fun `rankedFor orders by preseason points when PRESEASON selected`() {
        val entries = listOf(
            entry("a", regular = 5, preseason = 20),
            entry("b", regular = 10, preseason = 1)
        )

        val ranked = entries.rankedFor(SeasonType.PRESEASON)

        assertThat(ranked.map { it.userId }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `rankedFor assigns the same rank to ties and skips the next rank`() {
        val entries = listOf(
            entry("a", regular = 10, preseason = 0),
            entry("b", regular = 10, preseason = 0),
            entry("c", regular = 5, preseason = 0)
        )

        val ranked = entries.rankedFor(SeasonType.REGULAR)

        assertThat(ranked.first { it.userId == "a" }.rank).isEqualTo(1)
        assertThat(ranked.first { it.userId == "b" }.rank).isEqualTo(1)
        assertThat(ranked.first { it.userId == "c" }.rank).isEqualTo(3)
    }
}
