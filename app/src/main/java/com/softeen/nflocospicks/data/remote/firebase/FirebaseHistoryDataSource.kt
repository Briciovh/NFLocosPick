package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GamePickResult
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseHistoryDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getPickHistory(groupId: String, userId: String): List<WeekHistoryEntry> {
        val weeksSnapshot = firestore
            .collection("groups").document(groupId)
            .collection("weeks")
            .get().await()

        val standingsDoc = firestore
            .collection("standings").document(groupId)
            .collection("members").document(userId)
            .get().await()

        @Suppress("UNCHECKED_CAST")
        val weeklyBreakdown = (standingsDoc.get("weeklyBreakdown") as? Map<String, Any>)
            ?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 }
            ?: emptyMap()

        val entries = mutableListOf<WeekHistoryEntry>()
        for (weekDoc in weeksSnapshot.documents) {
            val weekId = weekDoc.id

            @Suppress("UNCHECKED_CAST")
            val rawGames = weekDoc.get("games") as? List<*> ?: continue
            val games = rawGames.filterIsInstance<Map<String, Any>>().mapNotNull { it.toGame() }
            if (games.isEmpty()) continue

            val picksDoc = firestore
                .collection("groups").document(groupId)
                .collection("weeks").document(weekId)
                .collection("picks").document(userId)
                .get().await()

            data class PickData(val pick: Pick, val winnerTeamAbbr: String?)

            val picksMap: Map<String, PickData> = if (picksDoc.exists()) {
                picksDoc.data.orEmpty().mapNotNull { (gameId, value) ->
                    val pickData = value as? Map<*, *> ?: return@mapNotNull null
                    val pickedTeam = pickData["pickedTeam"] as? String ?: return@mapNotNull null
                    val isCorrect = pickData["isCorrect"] as? Boolean
                    val scoredAt = (pickData["scoredAt"] as? Number)?.toLong()
                    val winnerTeamAbbr = (pickData["winnerTeamAbbr"] as? String)?.takeIf { it.isNotEmpty() }
                    gameId to PickData(Pick(gameId, pickedTeam, isCorrect, scoredAt), winnerTeamAbbr)
                }.toMap()
            } else emptyMap()

            val gamePickResults = games.map { game ->
                val pd = picksMap[game.id]
                GamePickResult(
                    game           = game,
                    pickedTeam     = pd?.pick?.pickedTeam,
                    isCorrect      = pd?.pick?.isCorrect,
                    scoredAt       = pd?.pick?.scoredAt,
                    winnerTeamAbbr = pd?.winnerTeamAbbr
                )
            }.sortedBy { it.game.kickoffTime }

            entries.add(
                WeekHistoryEntry(
                    weekId     = weekId,
                    weekPoints = weeklyBreakdown[weekId] ?: 0,
                    picks      = gamePickResults
                )
            )
        }

        return entries.sortedByDescending { it.weekId }
    }

    private fun Map<String, Any>.toGame(): Game? {
        val id           = this["id"] as? String ?: return null
        val weekId       = this["weekId"] as? String ?: return null
        val homeTeam     = this["homeTeam"] as? String ?: return null
        val awayTeam     = this["awayTeam"] as? String ?: return null
        val homeTeamAbbr = this["homeTeamAbbr"] as? String ?: return null
        val awayTeamAbbr = this["awayTeamAbbr"] as? String ?: return null
        val kickoffTime  = (this["kickoffTime"] as? Number)?.toLong() ?: return null
        val status       = runCatching { GameStatus.valueOf(this["status"] as? String ?: "") }
                               .getOrDefault(GameStatus.SCHEDULED)
        return Game(id, weekId, homeTeam, awayTeam, homeTeamAbbr, awayTeamAbbr, kickoffTime, null, null, status)
    }
}
