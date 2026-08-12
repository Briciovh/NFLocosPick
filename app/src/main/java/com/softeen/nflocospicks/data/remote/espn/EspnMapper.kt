package com.softeen.nflocospicks.data.remote.espn

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.SeasonType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val espnDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Mapea la respuesta completa del scoreboard a una lista de domain [Game].
 * Eventos malformados se descartan silenciosamente con runCatching.
 *
 * week/season se leen de cada evento, no de la respuesta — cuando se pide
 * con "dates" como rango (ver EspnDateRange.kt), ESPN puede omitir el
 * week/season a nivel de respuesta por completo, y un rango puede además
 * cruzar un límite de semana o de tipo de temporada. Cada evento siempre
 * trae su propio week/season, sin esa ambigüedad.
 */
fun EspnScoreboardResponse.toDomain(): List<Game> =
    events.mapNotNull { event ->
        runCatching { event.toGame() }.getOrNull()
    }

private fun Int.toSeasonType(): SeasonType = when (this) {
    1    -> SeasonType.PRESEASON
    3    -> SeasonType.POSTSEASON
    else -> SeasonType.REGULAR
}

// Keep in sync with functions/src/espn.ts::buildWeekId — ambos deben producir el mismo
// weekId, ya que los picks se escriben en el cliente y se puntúan en Cloud Functions.
// Post-temporada se deja sin prefijo (misma ambigüedad de hoy, fuera de alcance).
private fun buildWeekId(year: Int, weekNumber: Int, seasonType: SeasonType): String {
    val nn = weekNumber.toString().padStart(2, '0')
    return if (seasonType == SeasonType.PRESEASON) "$year-pre-week-$nn" else "$year-week-$nn"
}

private fun EspnEvent.toGame(): Game {
    val weekNumber = week.number
    val seasonType = season.type.toSeasonType()
    val competition = competitions.first()
    val home = competition.competitors.first { it.homeAway == "home" }
    val away = competition.competitors.first { it.homeAway == "away" }

    val kickoffMillis = espnDateFormat.parse(date)?.time ?: 0L

    // El año se deriva del kickoff para ser correcto en transiciones de temporada
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = kickoffMillis
    }
    val year = cal.get(Calendar.YEAR)
    val weekId = buildWeekId(year, weekNumber, seasonType)

    return Game(
        id             = id,
        weekId         = weekId,
        seasonType     = seasonType,
        homeTeam       = home.team.displayName,
        awayTeam       = away.team.displayName,
        homeTeamAbbr   = home.team.abbreviation,
        awayTeamAbbr   = away.team.abbreviation,
        kickoffTime    = kickoffMillis,
        homeScore      = home.score?.toIntOrNull(),
        awayScore      = away.score?.toIntOrNull(),
        status         = competition.status.type.toGameStatus(),
        homeTeamRecord = home.records?.firstOrNull { it.name == "overall" }?.summary,
        awayTeamRecord = away.records?.firstOrNull { it.name == "overall" }?.summary
    )
}

private fun EspnStatusType.toGameStatus(): GameStatus = when {
    completed                    -> GameStatus.FINAL
    name == "STATUS_IN_PROGRESS" -> GameStatus.IN_PROGRESS
    else                         -> GameStatus.SCHEDULED
}
