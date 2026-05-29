package com.softeen.nflocospicks.domain.model

data class GamePickResult(
    val game: Game,
    val pickedTeam: String?,
    val isCorrect: Boolean?,
    val scoredAt: Long?,
    val winnerTeamAbbr: String?
)

data class WeekHistoryEntry(
    val weekId: String,
    val weekPoints: Int,
    val picks: List<GamePickResult>
)
