package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.SeasonType

interface ScheduleRepository {
    /**
     * Obtiene los partidos de la semana NFL actual desde la ESPN API y los
     * cachea en Firestore bajo groups/{groupId}/weeks/{weekId}/games[].
     * Retorna la lista de partidos parseados.
     */
    suspend fun getCurrentWeekGames(groupId: String): List<Game>

    /**
     * Obtiene los partidos de una semana arbitraria de la temporada (para
     * navegar los tabs de semana). A propósito NO cachea en Firestore:
     * HistoryScreen genera una entrada por cada doc weeks/{weekId} con
     * games[] no vacío, así que cachear semanas solo navegadas llenaría el
     * historial de semanas sin picks.
     */
    suspend fun getGamesForWeek(seasonType: SeasonType, weekNumber: Int): List<Game>
}
