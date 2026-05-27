package com.softeen.nflocospicks.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

    override suspend fun getCurrentWeekGames(groupId: String): List<Game> {
        val games = espnApiService.getScoreboard().toDomain()

        // Cachear en Firestore como side effect — error no fatal
        if (games.isNotEmpty()) {
            val weekId = games.first().weekId
            try {
                val gamesData = games.map { game ->
                    mapOf(
                        "id"           to game.id,
                        "weekId"       to game.weekId,
                        "homeTeam"     to game.homeTeam,
                        "awayTeam"     to game.awayTeam,
                        "homeTeamAbbr" to game.homeTeamAbbr,
                        "awayTeamAbbr" to game.awayTeamAbbr,
                        "kickoffTime"  to game.kickoffTime,
                        "status"       to game.status.name
                        // scores omitidos — datos live que caducan inmediatamente
                    )
                }
                firestore.collection("groups").document(groupId)
                    .collection("weeks").document(weekId)
                    .set(mapOf("games" to gamesData), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w("ScheduleRepo", "No se pudo cachear en Firestore: ${e.message}")
            }
        }

        return games
    }
}
