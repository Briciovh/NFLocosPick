package com.softeen.nflocospicks.data.remote.espn

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Convierte [EspnScoreboardResponse] al modelo de dominio [List<Game>].
 * Cada evento se procesa con runCatching para que un partido malformado
 * no rompa la lista completa.
 */
fun EspnScoreboardResponse.toDomain(): List<Game> {
    val weekNumber = week?.number ?: 0
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    return events.orEmpty().mapNotNull { event ->
        runCatching {
            val competition = event.competitions?.firstOrNull() ?: return@runCatching null
            val competitors = competition.competitors.orEmpty()

            val home = competitors.firstOrNull { it.homeAway == "home" } ?: return@runCatching null
            val away = competitors.firstOrNull { it.homeAway == "away" } ?: return@runCatching null

            val kickoffMillis = dateFormat.parse(event.date ?: return@runCatching null)?.time
                ?: return@runCatching null

            // weekId derivado del año del kickoff para que sea consistente entre PRs
            val year = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).also {
                it.timeInMillis = kickoffMillis
            }.get(java.util.Calendar.YEAR)
            val weekId = "$year-week-${weekNumber.toString().padStart(2, '0')}"

            val statusType = competition.status?.type
            val status = when {
                statusType?.completed == true -> GameStatus.FINAL
                statusType?.name == "STATUS_IN_PROGRESS" -> GameStatus.IN_PROGRESS
                else -> GameStatus.SCHEDULED
            }

            Game(
                id           = event.id ?: return@runCatching null,
                weekId       = weekId,
                homeTeam     = home.team?.displayName ?: "Home",
                awayTeam     = away.team?.displayName ?: "Away",
                homeTeamAbbr = home.team?.abbreviation ?: "HME",
                awayTeamAbbr = away.team?.abbreviation ?: "AWY",
                kickoffTime  = kickoffMillis,
                homeScore    = home.score?.toIntOrNull(),
                awayScore    = away.score?.toIntOrNull(),
                status       = status
            )
        }.getOrNull()
    }
}
