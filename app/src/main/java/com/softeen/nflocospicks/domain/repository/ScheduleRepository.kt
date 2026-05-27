package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Game

interface ScheduleRepository {
    /**
     * Obtiene los partidos de la semana NFL actual desde la ESPN API y los
     * cachea en Firestore bajo groups/{groupId}/weeks/{weekId}/games[].
     * Retorna la lista de partidos parseados.
     */
    suspend fun getCurrentWeekGames(groupId: String): List<Game>
}
