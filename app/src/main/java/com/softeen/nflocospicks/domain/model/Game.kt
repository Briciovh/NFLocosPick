package com.softeen.nflocospicks.domain.model

data class Game(
    val id: String,
    val weekId: String,           // e.g. "2025-week-12" — clave para PR-5 picks
    val homeTeam: String,         // displayName completo
    val awayTeam: String,
    val homeTeamAbbr: String,     // e.g. "KC"
    val awayTeamAbbr: String,
    val kickoffTime: Long,        // UTC millis
    val homeScore: Int?,          // null cuando SCHEDULED
    val awayScore: Int?,
    val status: GameStatus
)
