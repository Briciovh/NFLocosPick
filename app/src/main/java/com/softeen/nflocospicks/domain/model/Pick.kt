package com.softeen.nflocospicks.domain.model

data class Pick(
    val gameId: String,
    val pickedTeam: String,    // abreviatura del equipo, e.g. "KC"
    val isCorrect: Boolean?,   // null hasta que se puntúe en PR-6
    val scoredAt: Long?        // null hasta que se puntúe en PR-6
)
