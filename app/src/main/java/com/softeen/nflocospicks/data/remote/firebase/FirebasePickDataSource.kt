package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.softeen.nflocospicks.domain.model.Pick
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebasePickDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun weekDoc(groupId: String, weekId: String) =
        firestore.collection("groups").document(groupId)
            .collection("weeks").document(weekId)

    private fun picksDoc(groupId: String, weekId: String, userId: String) =
        weekDoc(groupId, weekId).collection("picks").document(userId)

    private fun resultsDoc(groupId: String, weekId: String, userId: String) =
        weekDoc(groupId, weekId).collection("results").document(userId)

    // ── Operaciones ───────────────────────────────────────────────────────────

    /**
     * Guarda el pick del usuario para [gameId] usando merge para no sobreescribir
     * picks de otros partidos en el mismo documento.
     * Estructura Firestore: picks/{userId} → { "{gameId}": { "pickedTeam": "KC" } }
     */
    suspend fun submitPick(
        groupId: String,
        weekId: String,
        userId: String,
        gameId: String,
        teamAbbr: String
    ) {
        val data = mapOf(gameId to mapOf("pickedTeam" to teamAbbr))
        picksDoc(groupId, weekId, userId)
            .set(data, SetOptions.merge())
            .await()
    }

    /**
     * Lee todos los picks del usuario para la semana dada, combinando
     * picks/{userId} (pickedTeam, escrito por el usuario) con
     * results/{userId} (isCorrect/scoredAt, escrito solo por la Cloud
     * Function de scoring — ver firestore.rules).
     */
    suspend fun getPicksForWeek(
        groupId: String,
        weekId: String,
        userId: String
    ): Map<String, Pick> {
        val picksSnapshot = picksDoc(groupId, weekId, userId).get().await()
        if (!picksSnapshot.exists()) return emptyMap()

        val resultsSnapshot = resultsDoc(groupId, weekId, userId).get().await()
        val results = resultsSnapshot.data.orEmpty()

        return picksSnapshot.data.orEmpty().mapNotNull { (gameId, value) ->
            val pickData = value as? Map<*, *> ?: return@mapNotNull null
            val pickedTeam = pickData["pickedTeam"] as? String ?: return@mapNotNull null
            val resultData = results[gameId] as? Map<*, *>
            val isCorrect  = resultData?.get("isCorrect") as? Boolean
            val scoredAt   = (resultData?.get("scoredAt") as? Number)?.toLong()
            gameId to Pick(
                gameId     = gameId,
                pickedTeam = pickedTeam,
                isCorrect  = isCorrect,
                scoredAt   = scoredAt
            )
        }.toMap()
    }
}
