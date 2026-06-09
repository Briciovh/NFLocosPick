package com.softeen.nflocospicks.data.remote.espn

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
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
 */
fun EspnScoreboardResponse.toDomain(): List<Game> {
    val weekNumber = week.number
    return events.mapNotNull { event ->
        runCatching { event.toGame(weekNumber) }.getOrNull()
    }
}

private fun EspnEvent.toGame(weekNumber: Int): Game {
    val competition = competitions.first()
    val home = competition.competitors.first { it.homeAway == "home" }
    val away = competition.competitors.first { it.homeAway == "away" }

    val kickoffMillis = espnDateFormat.parse(date)?.time ?: 0L

    // El año se deriva del kickoff para ser correcto en transiciones de temporada
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = kickoffMillis
    }
    val year = cal.get(Calendar.YEAR)
    val weekId = "$year-week-${weekNumber.toString().padStart(2, '0')}"

    return Game(
        id             = id,
        weekId         = weekId,
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
