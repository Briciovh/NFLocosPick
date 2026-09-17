package com.softeen.nflocospicks.data.remote.espn

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val paramDateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
private val paramDateFormat: SimpleDateFormat get() = checkNotNull(paramDateFormatThreadLocal.get())

/**
 * Fecha de "hoy" en UTC, formato "YYYYMMDD" — único valor de `dates` que
 * ESPN acepta de forma confiable (ver docs/plans/fix-espn-current-week-400.md):
 * cualquier rango "YYYYMMDD-YYYYMMDD" devuelve 400 sin importar la fecha.
 */
fun todayDateParam(nowMillis: Long = System.currentTimeMillis()): String =
    paramDateFormat.format(java.util.Date(nowMillis))

/**
 * Resuelve (seasonType crudo de ESPN, número de semana crudo) para el
 * instante [nowMillis], usando leagues[0].calendar de una respuesta de
 * scoreboard cualquiera (incluida una de fecha única sin eventos). Entradas
 * mal formadas se ignoran; null si ninguna entrada contiene [nowMillis]
 * (fuera de temporada, antes de pretemporada o después del Super Bowl).
 */
fun List<EspnLeague>?.resolveCurrentSeasonWeek(nowMillis: Long = System.currentTimeMillis()): Pair<Int, Int>? {
    this.orEmpty().forEach { league ->
        league.calendar.orEmpty().forEach { group ->
            val seasonType = group.value.toIntOrNull()
            if (seasonType != null) {
                group.entries.forEach { entry ->
                    val week = entry.value.toIntOrNull()
                    val start = runCatching { espnDateFormat.parse(entry.startDate)?.time }.getOrNull()
                    // endDate llega sin segundos ("...T06:59Z") y la siguiente entrada
                    // arranca en "...T07:00Z" — literalmente son 60s aparte, no 0. Sin
                    // este +59999ms, el segundo 06:59:00.001-06:59:59.999 no cae dentro
                    // de NINGUNA entrada (hallazgo de la revisión de Antigravity, ver
                    // "Cross-review" abajo). Normalizamos el end a "fin de ese minuto".
                    val end = runCatching { espnDateFormat.parse(entry.endDate)?.time?.plus(59_999L) }.getOrNull()
                    if (week != null && start != null && end != null && nowMillis in start..end) {
                        return seasonType to week
                    }
                }
            }
        }
    }
    return null
}
