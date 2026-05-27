package com.softeen.nflocospicks.domain.model

data class Game(
    val id: String,
    val weekId: String,         // e.g. "2025-week-12" — usado en PR-5 para escribir picks
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamAbbr: String,
    val awayTeamAbbr: String,
    val kickoffTime: Long,      // UTC millis
    val homeScore: Int?,        // null mientras el juego no ha comenzado
    val awayScore: Int?,
    val status: GameStatus
)
