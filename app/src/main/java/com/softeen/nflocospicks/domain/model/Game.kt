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
    val status: GameStatus,
    val homeTeamRecord: String? = null,  // e.g. "10-3", null fuera de temporada regular
    val awayTeamRecord: String? = null,
    val seasonType: SeasonType = SeasonType.REGULAR,
    val weekNumber: Int = 0    // número de semana crudo de la ESPN API, no la etiqueta que ve
                                // el usuario (pretemporada: 1=Hall of Fame Game, 2-4=semanas
                                // reales; regular: 1-18; postemporada: 1,2,3,5). 0 = desconocido.
)
