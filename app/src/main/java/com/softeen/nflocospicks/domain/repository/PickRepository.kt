package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Pick

interface PickRepository {
    /**
     * Guarda o actualiza el pick de un usuario para un partido específico.
     * Usa merge para no sobreescribir picks de otros partidos.
     */
    suspend fun submitPick(
        groupId: String,
        weekId: String,
        userId: String,
        gameId: String,
        teamAbbr: String
    )

    /**
     * Retorna un mapa de gameId → Pick con todos los picks del usuario
     * para la semana dada. Mapa vacío si no hay picks aún.
     */
    suspend fun getPicksForWeek(
        groupId: String,
        weekId: String,
        userId: String
    ): Map<String, Pick>
}
