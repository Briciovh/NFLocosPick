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

    private fun picksDoc(groupId: String, weekId: String, userId: String) =
        firestore.collection("groups").document(groupId)
            .collection("weeks").document(weekId)
            .collection("picks").document(userId)

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
     * Lee todos los picks del usuario para la semana dada.
     * Cada campo del documento es un gameId que mapea a sus datos de pick.
     */
    suspend fun getPicksForWeek(
        groupId: String,
        weekId: String,
        userId: String
    ): Map<String, Pick> {
        val snapshot = picksDoc(groupId, weekId, userId).get().await()
        if (!snapshot.exists()) return emptyMap()

        return snapshot.data.orEmpty().mapNotNull { (gameId, value) ->
            val pickData = value as? Map<*, *> ?: return@mapNotNull null
            val pickedTeam = pickData["pickedTeam"] as? String ?: return@mapNotNull null
            val isCorrect  = pickData["isCorrect"] as? Boolean
            val scoredAt   = (pickData["scoredAt"] as? Number)?.toLong()
            gameId to Pick(
                gameId     = gameId,
                pickedTeam = pickedTeam,
                isCorrect  = isCorrect,
                scoredAt   = scoredAt
            )
        }.toMap()
    }
}
