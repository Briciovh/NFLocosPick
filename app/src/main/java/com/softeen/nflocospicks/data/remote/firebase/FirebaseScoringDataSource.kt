package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseScoringDataSource @Inject constructor(
    private val functions: FirebaseFunctions
) {

    /**
     * Invoca la Cloud Function "scoreGroupWeek", que corre con Admin SDK y por
     * lo tanto puede escribir isCorrect/scoredAt en los picks y actualizar
     * standings — algo que las reglas de seguridad niegan explícitamente al
     * cliente (ver firestore.rules: standings.write == false).
     *
     * La función valida que el usuario autenticado sea miembro de [groupId].
     */
    suspend fun scoreWeek(groupId: String): Int {
        val result = functions
            .getHttpsCallable("scoreGroupWeek")
            .call(mapOf("groupId" to groupId))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        return (data?.get("scoredCount") as? Number)?.toInt() ?: 0
    }
}
