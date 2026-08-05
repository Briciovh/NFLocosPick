package com.softeen.nflocospicks.domain.repository

interface ScoringRepository {

    /**
     * Dispara el puntuado de la semana actual para [groupId].
     * Toda la lógica (fetch de ESPN, cálculo de ganadores, escritura de
     * isCorrect/scoredAt y standings) corre server-side en una Cloud Function
     * con Admin SDK — el cliente no tiene permiso de escritura sobre esos
     * campos (ver firestore.rules).
     *
     * Retorna el número de picks recién puntuados.
     */
    suspend fun scoreWeek(groupId: String): Int
}
