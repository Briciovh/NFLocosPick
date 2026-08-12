package com.softeen.nflocospicks.domain.model

/**
 * Una semana navegable de la temporada NFL, identificada por el número de
 * semana crudo de la ESPN API (ver [Game.weekNumber]). No genera weekId a
 * propósito: el weekId real siempre viene de los [Game] que ESPN devuelve
 * para esta semana, nunca se predice acá — el año de postemporada se deriva
 * del kickoff de cada evento (puede caer en el año calendario siguiente),
 * así que un weekId construido localmente podría no coincidir con el real.
 */
data class SeasonWeek(val seasonType: SeasonType, val weekNumber: Int) {
    /**
     * Número que ve el usuario. ESPN separa el Hall of Fame Game como su
     * propio tab de sitio ("HOF"), así que en pretemporada la etiqueta va
     * desfasada -1 respecto al número crudo de la API (API 2 → "PRE WK 1").
     */
    val displayNumber: Int get() = if (seasonType == SeasonType.PRESEASON) weekNumber - 1 else weekNumber

    val isHallOfFame: Boolean get() = seasonType == SeasonType.PRESEASON && weekNumber == 1
}

/**
 * Estructura completa y estática de la temporada NFL, en el orden en que se
 * muestran los tabs. Verificado contra la ESPN API real: pretemporada tiene
 * 4 semanas (1=Hall of Fame Game, 2-4=semanas reales), temporada regular
 * 1-18, postemporada 1,2,3,5 (la semana 4 es el bye de Pro Bowl, sin
 * juegos — se omite a propósito, no es un tab).
 */
object NflSeasonCalendar {
    val WEEKS: List<SeasonWeek> = buildList {
        (1..4).forEach { add(SeasonWeek(SeasonType.PRESEASON, it)) }
        (1..18).forEach { add(SeasonWeek(SeasonType.REGULAR, it)) }
        listOf(1, 2, 3, 5).forEach { add(SeasonWeek(SeasonType.POSTSEASON, it)) }
    }

    /** Índice en [WEEKS], o -1 si la combinación no existe (p.ej. POSTSEASON 4). */
    fun indexOf(seasonType: SeasonType, weekNumber: Int): Int =
        WEEKS.indexOfFirst { it.seasonType == seasonType && it.weekNumber == weekNumber }

    /** Tab por defecto cuando no se puede resolver la semana actual (temporada muerta). */
    val DEFAULT_INDEX: Int = indexOf(SeasonType.REGULAR, 1)
}
