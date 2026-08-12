package com.softeen.nflocospicks.data.remote.espn

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val paramDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Devuelve el parámetro "dates=YYYYMMDD-YYYYMMDD" de ESPN para la semana NFL
 * que contiene el instante actual: martes 00:00 UTC (día libre de la NFL,
 * nunca hay kickoffs ahí) hasta el lunes siguiente inclusive.
 *
 * Sin este parámetro, ESPN decide "hoy" del lado del servidor y, si no hay
 * juegos hoy, cae al día más reciente con juegos en vez de la semana
 * completa — por eso el scoreboard se veía "atorado" en un solo juego FINAL.
 */
fun currentNflWeekDatesParam(nowMillis: Long = System.currentTimeMillis()): String {
    val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.TUESDAY) {
            add(Calendar.DAY_OF_MONTH, -1)
        }
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
    return "${paramDateFormat.format(start.time)}-${paramDateFormat.format(end.time)}"
}
