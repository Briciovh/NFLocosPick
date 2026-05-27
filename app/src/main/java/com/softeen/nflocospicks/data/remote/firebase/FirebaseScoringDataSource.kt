package com.softeen.nflocospicks.data.remote.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.domain.repository.ScoringRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseScoringDataSource @Inject constructor(
    private val firestore    : FirebaseFirestore,
    private val pickDataSource: FirebasePickDataSource
) {
    companion object {
        private const val TAG = "FirebaseScoringDS"
    }

    /**
     * Para cada miembro del grupo:
     *   1. Lee sus picks de la semana.
     *   2. Filtra los que todavía no tienen isCorrect (no puntuados) y corresponden a juegos FINAL.
     *   3. Arma un WriteBatch que escribe isCorrect/scoredAt en los picks
     *      Y actualiza standings/{groupId}/members/{userId} de forma atómica.
     *
     * Retorna el total de picks recién puntuados.
     */
    suspend fun scoreWeek(
        groupId   : String,
        weekId    : String,
        memberIds : List<String>,
        winners   : Map<String, String?>
    ): Int {
        val batch  = firestore.batch()
        val nowMs  = System.currentTimeMillis()
        var scored = 0

        for (userId in memberIds) {
            val picks = pickDataSource.getPicksForWeek(groupId, weekId, userId)

            // Solo procesar picks de juegos FINAL que aún no han sido puntuados
            val unsettled = picks.filter { (gameId, pick) ->
                pick.isCorrect == null && winners.containsKey(gameId)
            }
            if (unsettled.isEmpty()) continue

            // ── Pick updates ────────────────────────────────────────────────────
            val picksRef = firestore
                .collection("groups").document(groupId)
                .collection("weeks").document(weekId)
                .collection("picks").document(userId)

            val pickUpdate = mutableMapOf<String, Any>()
            var newlyCorrect = 0

            for ((gameId, pick) in unsettled) {
                val winner  = winners[gameId]
                val correct = winner != null && pick.pickedTeam == winner
                if (correct) newlyCorrect++
                pickUpdate["$gameId.isCorrect"] = correct
                pickUpdate["$gameId.scoredAt"]  = nowMs
                scored++
            }
            batch.update(picksRef, pickUpdate)

            // ── Standings update ─────────────────────────────────────────────────
            // Contamos los picks ya correctos de la semana para escribir un total
            // absoluto (no un incremento) → operación idempotente.
            val alreadyCorrect   = picks.count { (_, p) -> p.isCorrect == true }
            val totalWeekCorrect = alreadyCorrect + newlyCorrect

            val standingsRef = firestore
                .collection("standings").document(groupId)
                .collection("members").document(userId)

            // Leemos el breakdown existente para recalcular totalPoints correctamente
            @Suppress("UNCHECKED_CAST")
            val existingBreakdown = (standingsRef.get().await().get("weeklyBreakdown")
                as? Map<String, Any>)
                ?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 }
                ?.toMutableMap() ?: mutableMapOf()

            existingBreakdown[weekId] = totalWeekCorrect
            val totalPoints = existingBreakdown.values.sum()

            batch.set(
                standingsRef,
                mapOf(
                    "totalPoints"     to totalPoints,
                    "weeklyBreakdown" to existingBreakdown
                )
            )
        }

        if (scored > 0) {
            batch.commit().await()
            Log.d(TAG, "Puntuados $scored picks — grupo=$groupId semana=$weekId")
        }
        return scored
    }
}
