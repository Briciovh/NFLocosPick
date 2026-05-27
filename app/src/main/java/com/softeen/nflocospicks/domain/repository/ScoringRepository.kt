package com.softeen.nflocospicks.domain.repository

interface ScoringRepository {

    /**
     * Escribe en Firestore (batch) los campos isCorrect/scoredAt de cada pick
     * sin puntuar de los miembros del grupo, y actualiza standings/{groupId}/members/{userId}.
     *
     * [winners] mapea gameId → abreviatura del equipo ganador (null = empate → nadie acierta).
     *
     * La operación es idempotente: los picks donde isCorrect != null se ignoran.
     * Retorna el número de picks recién puntuados (0 = nada nuevo que puntuar).
     */
    suspend fun scoreWeek(
        groupId   : String,
        weekId    : String,
        memberIds : List<String>,
        winners   : Map<String, String?>    // gameId → abbr ganador o null si empate
    ): Int
}
