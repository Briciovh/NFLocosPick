package com.softeen.nflocospicks.data.remote.espn

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

class EspnCurrentWeekResolverTest {

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0
    ): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
        set(Calendar.MILLISECOND, millisecond)
    }.timeInMillis

    private val sampleCalendar = listOf(
        EspnLeague(
            calendar = listOf(
                EspnCalendarGroup(
                    value = "1", // Preseason
                    entries = listOf(
                        EspnCalendarEntry(
                            value = "1",
                            startDate = "2026-08-06T07:00Z",
                            endDate = "2026-08-13T06:59Z"
                        ),
                        EspnCalendarEntry(
                            value = "2",
                            startDate = "2026-08-13T07:00Z",
                            endDate = "2026-08-20T06:59Z"
                        )
                    )
                ),
                EspnCalendarGroup(
                    value = "2", // Regular Season
                    entries = listOf(
                        EspnCalendarEntry(
                            value = "1",
                            startDate = "2026-09-06T07:00Z",
                            endDate = "2026-09-16T06:59Z"
                        ),
                        EspnCalendarEntry(
                            value = "2",
                            startDate = "2026-09-16T07:00Z",
                            endDate = "2026-09-23T06:59Z"
                        )
                    )
                ),
                EspnCalendarGroup(
                    value = "3", // Postseason
                    entries = listOf(
                        EspnCalendarEntry(
                            value = "1",
                            startDate = "2027-01-10T07:00Z",
                            endDate = "2027-01-17T06:59Z"
                        ),
                        EspnCalendarEntry(
                            value = "5", // Super Bowl
                            startDate = "2027-02-07T07:00Z",
                            endDate = "2027-02-15T06:59Z"
                        )
                    )
                )
            )
        )
    )

    @Test
    fun `todayDateParam formats fixed timestamp as YYYYMMDD in UTC`() {
        val nowMillis = utcMillis(2026, 9, 16, hour = 15, minute = 30)
        assertThat(todayDateParam(nowMillis)).isEqualTo("20260916")
    }

    @Test
    fun `resolveCurrentSeasonWeek returns correct season and week during mid-window of Week 2`() {
        val nowMillis = utcMillis(2026, 9, 18, hour = 12)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isEqualTo(2 to 2)
    }

    @Test
    fun `resolveCurrentSeasonWeek resolves to the entry starting exactly at startDate`() {
        val nowMillis = utcMillis(2026, 9, 16, hour = 7, minute = 0, second = 0)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isEqualTo(2 to 2)
    }

    @Test
    fun `resolveCurrentSeasonWeek resolves to prior entry at exactly parsed endDate`() {
        val nowMillis = utcMillis(2026, 9, 16, hour = 6, minute = 59, second = 0)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isEqualTo(2 to 1)
    }

    @Test
    fun `resolveCurrentSeasonWeek resolves to prior entry during 59 second truncated minute gap`() {
        // Between 2026-09-16T06:59:00Z and 2026-09-16T07:00:00Z (e.g. at 06:59:30)
        val nowMillis = utcMillis(2026, 9, 16, hour = 6, minute = 59, second = 30)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isEqualTo(2 to 1)
    }

    @Test
    fun `resolveCurrentSeasonWeek resolves to prior entry at end of truncated minute`() {
        val nowMillis = utcMillis(2026, 9, 16, hour = 6, minute = 59, second = 59, millisecond = 999)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isEqualTo(2 to 1)
    }

    @Test
    fun `resolveCurrentSeasonWeek returns null before first preseason entry`() {
        val nowMillis = utcMillis(2026, 8, 1, hour = 0)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isNull()
    }

    @Test
    fun `resolveCurrentSeasonWeek returns null after last postseason entry`() {
        val nowMillis = utcMillis(2027, 3, 1, hour = 0)
        val result = sampleCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isNull()
    }

    @Test
    fun `resolveCurrentSeasonWeek returns null safely when leagues or calendar is null`() {
        val nullLeagues: List<EspnLeague>? = null
        assertThat(nullLeagues.resolveCurrentSeasonWeek()).isNull()

        val emptyCalendarLeagues = listOf(EspnLeague(calendar = null))
        assertThat(emptyCalendarLeagues.resolveCurrentSeasonWeek()).isNull()
    }

    @Test
    fun `resolveCurrentSeasonWeek ignores non-numeric values without throwing`() {
        val invalidEntriesCalendar = listOf(
            EspnLeague(
                calendar = listOf(
                    EspnCalendarGroup(
                        value = "not-a-number",
                        entries = listOf(
                            EspnCalendarEntry(
                                value = "1",
                                startDate = "2026-09-06T07:00Z",
                                endDate = "2026-09-16T06:59Z"
                            )
                        )
                    ),
                    EspnCalendarGroup(
                        value = "2",
                        entries = listOf(
                            EspnCalendarEntry(
                                value = "not-a-number",
                                startDate = "2026-09-06T07:00Z",
                                endDate = "2026-09-16T06:59Z"
                            ),
                            EspnCalendarEntry(
                                value = "1",
                                startDate = "invalid-date",
                                endDate = "2026-09-16T06:59Z"
                            )
                        )
                    )
                )
            )
        )
        val nowMillis = utcMillis(2026, 9, 10, hour = 12)
        val result = invalidEntriesCalendar.resolveCurrentSeasonWeek(nowMillis)
        assertThat(result).isNull()
    }
}
