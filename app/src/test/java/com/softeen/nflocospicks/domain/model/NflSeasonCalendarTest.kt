package com.softeen.nflocospicks.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NflSeasonCalendarTest {

    @Test
    fun `WEEKS has exactly 26 entries with no duplicates`() {
        assertThat(NflSeasonCalendar.WEEKS).hasSize(26)
        assertThat(NflSeasonCalendar.WEEKS.toSet()).hasSize(26)
    }

    @Test
    fun `preseason occupies indices 0 to 3`() {
        assertThat(NflSeasonCalendar.WEEKS[0]).isEqualTo(SeasonWeek(SeasonType.PRESEASON, 1))
        assertThat(NflSeasonCalendar.WEEKS[1]).isEqualTo(SeasonWeek(SeasonType.PRESEASON, 2))
        assertThat(NflSeasonCalendar.WEEKS[2]).isEqualTo(SeasonWeek(SeasonType.PRESEASON, 3))
        assertThat(NflSeasonCalendar.WEEKS[3]).isEqualTo(SeasonWeek(SeasonType.PRESEASON, 4))
    }

    @Test
    fun `regular season occupies indices 4 to 21`() {
        assertThat(NflSeasonCalendar.WEEKS[4]).isEqualTo(SeasonWeek(SeasonType.REGULAR, 1))
        assertThat(NflSeasonCalendar.WEEKS[21]).isEqualTo(SeasonWeek(SeasonType.REGULAR, 18))
    }

    @Test
    fun `postseason week numbers are exactly 1, 2, 3, 5 — week 4 is the Pro Bowl bye`() {
        val postseasonWeekNumbers = NflSeasonCalendar.WEEKS
            .filter { it.seasonType == SeasonType.POSTSEASON }
            .map { it.weekNumber }

        assertThat(postseasonWeekNumbers).containsExactly(1, 2, 3, 5).inOrder()
    }

    @Test
    fun `indexOf finds known weeks and returns -1 for missing ones`() {
        assertThat(NflSeasonCalendar.indexOf(SeasonType.REGULAR, 7)).isEqualTo(10)
        assertThat(NflSeasonCalendar.indexOf(SeasonType.POSTSEASON, 4)).isEqualTo(-1)
        assertThat(NflSeasonCalendar.indexOf(SeasonType.REGULAR, 19)).isEqualTo(-1)
    }

    @Test
    fun `DEFAULT_INDEX resolves to regular season week 1`() {
        assertThat(NflSeasonCalendar.WEEKS[NflSeasonCalendar.DEFAULT_INDEX])
            .isEqualTo(SeasonWeek(SeasonType.REGULAR, 1))
    }

    @Test
    fun `displayNumber offsets preseason by -1 for the HOF tab, other types stay the same`() {
        assertThat(SeasonWeek(SeasonType.PRESEASON, 2).displayNumber).isEqualTo(1)
        assertThat(SeasonWeek(SeasonType.PRESEASON, 4).displayNumber).isEqualTo(3)
        assertThat(SeasonWeek(SeasonType.REGULAR, 5).displayNumber).isEqualTo(5)
        assertThat(SeasonWeek(SeasonType.POSTSEASON, 5).displayNumber).isEqualTo(5)
    }

    @Test
    fun `isHallOfFame is true only for preseason week 1`() {
        assertThat(SeasonWeek(SeasonType.PRESEASON, 1).isHallOfFame).isTrue()
        assertThat(SeasonWeek(SeasonType.PRESEASON, 2).isHallOfFame).isFalse()
        assertThat(SeasonWeek(SeasonType.REGULAR, 1).isHallOfFame).isFalse()
    }
}
