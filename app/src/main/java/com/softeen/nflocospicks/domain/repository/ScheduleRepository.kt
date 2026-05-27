package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Game

interface ScheduleRepository {
    /**
     * Obtiene la lista de partidos de la semana NFL actual para el grupo dado.
     * Hace fetch de ESPN y cachea los juegos en Firestore como side effect.
     */
    suspend fun getCurrentWeekGames(groupId: String): List<Game>
}
