package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import com.softeen.nflocospicks.domain.repository.ScoringRepository
import javax.inject.Inject

class ScoreWeekPicksUseCase @Inject constructor(
    private val scheduleRepository : ScheduleRepository,
    private val groupRepository    : GroupRepository,
    private val scoringRepository  : ScoringRepository
) {
    /**
     * Orquesta el puntuado de la semana para un grupo:
     *
     * 1. Obtiene los partidos de la semana desde ESPN (siempre en vivo — ScheduleRepositoryImpl
     *    nunca cachea scores). Retorna 0 si ninguno ha terminado aún.
     * 2. Determina el ganador de cada partido FINAL (lógica pura en el dominio).
     * 3. Lee los memberIds del grupo y delega todo el I/O de Firestore a ScoringRepository.
     *
     * Retorna el número de picks recién puntuados. Retorna 0 si no hay partidos FINAL.
     */
    suspend operator fun invoke(groupId: String): Int {
        // 1. Fetch live scores — Game.homeScore/awayScore están poblados para juegos FINAL
        val games = scheduleRepository.getCurrentWeekGames(groupId)
        val finalGames = games.filter { it.status == GameStatus.FINAL }
        if (finalGames.isEmpty()) return 0

        val weekId = games.first().weekId

        // 2. Determinar ganador de cada juego FINAL (dominio puro, sin imports Android/Firebase)
        val winners: Map<String, String?> = finalGames.associate { game ->
            val winner = when {
                (game.homeScore ?: 0) > (game.awayScore ?: 0) -> game.homeTeamAbbr
                (game.awayScore ?: 0) > (game.homeScore ?: 0) -> game.awayTeamAbbr
                else -> null    // empate — ningún pick puede ser correcto
            }
            game.id to winner
        }

        // 3. Obtener miembros y delegar escrituras a ScoringRepository
        val group = groupRepository.getGroupById(groupId)
        return scoringRepository.scoreWeek(groupId, weekId, group.memberIds, winners)
    }
}
