package com.softeen.nflocospicks.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
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
        private const val TAG = "ScheduleRepository"
    }

    override suspend fun getCurrentWeekGames(groupId: String): List<Game> {
        val response = espnApiService.getScoreboard()
        val games = response.toDomain()

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
            Log.w(TAG, "No se pudo cachear juegos en Firestore (no fatal)", e)
        }
    }
}
