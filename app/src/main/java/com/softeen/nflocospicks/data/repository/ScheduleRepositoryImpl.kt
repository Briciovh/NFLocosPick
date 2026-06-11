package com.softeen.nflocospicks.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber
import com.softeen.nflocospicks.data.remote.espn.EspnApiService
import com.softeen.nflocospicks.data.remote.espn.toDomain
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val espnApiService: EspnApiService,
    private val firestore: FirebaseFirestore
) : ScheduleRepository {

    companion object {
        /**
         * SOLO para desarrollo: desplaza el kickoffTime N horas al futuro para
         * poder testear picks durante la temporada muerta.
         * Cambiar a 0L en producción / cuando arranque la temporada real.
         */
        private const val DEBUG_KICKOFF_OFFSET_MS = 24 * 60 * 60 * 1000L  // 24 h hacia el futuro
    }

    override suspend fun getCurrentWeekGames(groupId: String): List<Game> {
        val response = espnApiService.getScoreboard()
        val games = response.toDomain().map { game ->
            // En debug desplazamos el kickoff para que los picks estén desbloqueados.
            // Quitar este bloque (o poner DEBUG_KICKOFF_OFFSET_MS = 0) para producción.
            if (DEBUG_KICKOFF_OFFSET_MS > 0L) {
                game.copy(kickoffTime = System.currentTimeMillis() + DEBUG_KICKOFF_OFFSET_MS)
            } else {
                game
            }
        }

        if (games.isNotEmpty()) {
            cacheGamesToFirestore(groupId, games)
        }

        return games
    }

    /**
     * Persiste un resumen de los juegos en Firestore bajo
     * groups/{groupId}/weeks/{weekId}.
     * Scores omitidos — son datos live que caducan; se obtienen en PR-6.
     * Errores no fatales: el usuario ve los juegos aunque Firestore falle.
     */
    private suspend fun cacheGamesToFirestore(groupId: String, games: List<Game>) {
        try {
            val weekId = games.first().weekId
            val serialized = games.map { game ->
                mapOf(
                    "id"           to game.id,
                    "weekId"       to game.weekId,
                    "homeTeam"     to game.homeTeam,
                    "awayTeam"     to game.awayTeam,
                    "homeTeamAbbr" to game.homeTeamAbbr,
                    "awayTeamAbbr" to game.awayTeamAbbr,
                    "kickoffTime"  to game.kickoffTime,
                    "status"       to game.status.name
                )
            }
            firestore
                .collection("groups").document(groupId)
                .collection("weeks").document(weekId)
                .set(mapOf("games" to serialized))
                .await()
        } catch (e: Exception) {
            Timber.w(e, "No se pudo cachear juegos en Firestore (no fatal)")
        }
    }
}
