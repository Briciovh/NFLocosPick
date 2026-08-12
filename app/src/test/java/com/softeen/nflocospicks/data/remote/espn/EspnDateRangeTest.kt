package com.softeen.nflocospicks.data.remote.espn

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

class EspnDateRangeTest {

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun `mid-week Wednesday resolves to the Tuesday-to-Monday window containing it`() {
        val nowMillis = utcMillis(2026, 8, 12, hour = 15) // miércoles

        val result = currentNflWeekDatesParam(nowMillis)

        assertThat(result).isEqualTo("20260811-20260817")
    }

    @Test
    fun `exactly on Tuesday starts that same day`() {
        val nowMillis = utcMillis(2026, 8, 11, hour = 3) // martes, temprano

        val result = currentNflWeekDatesParam(nowMillis)

        assertThat(result).isEqualTo("20260811-20260817")
    }

    @Test
    fun `exactly on Monday still resolves to the same week's Tuesday start`() {
        val nowMillis = utcMillis(2026, 8, 17, hour = 23) // lunes, tarde

        val result = currentNflWeekDatesParam(nowMillis)

        assertThat(result).isEqualTo("20260811-20260817")
    }

    @Test
    fun `week window crosses a month boundary correctly`() {
        val nowMillis = utcMillis(2026, 9, 1, hour = 12) // martes 1 de septiembre

        val result = currentNflWeekDatesParam(nowMillis)

        assertThat(result).isEqualTo("20260901-20260907")
    }
}
