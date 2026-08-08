package com.softeen.nflocospicks.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LeaderboardSeasonSplitTest {

    private val breakdown = mapOf(
        "2026-week-01"     to 3,
        "2026-pre-week-01" to 2,
        "2026-week-02"     to 1
    )

    @Test
    fun `sumBySeasonType sums only regular season weeks`() {
        assertThat(breakdown.sumBySeasonType(SeasonType.REGULAR)).isEqualTo(4)
    }

    @Test
    fun `sumBySeasonType sums only preseason weeks`() {
        assertThat(breakdown.sumBySeasonType(SeasonType.PRESEASON)).isEqualTo(2)
    }

    @Test
    fun `sumBySeasonType on empty map returns zero for both types`() {
        assertThat(emptyMap<String, Int>().sumBySeasonType(SeasonType.REGULAR)).isEqualTo(0)
        assertThat(emptyMap<String, Int>().sumBySeasonType(SeasonType.PRESEASON)).isEqualTo(0)
    }

    @Test
    fun `filterBySeasonType keeps only matching keys`() {
        assertThat(breakdown.filterBySeasonType(SeasonType.PRESEASON))
            .containsExactly("2026-pre-week-01", 2)
        assertThat(breakdown.filterBySeasonType(SeasonType.REGULAR))
            .containsExactly("2026-week-01", 3, "2026-week-02", 1)
    }
}
