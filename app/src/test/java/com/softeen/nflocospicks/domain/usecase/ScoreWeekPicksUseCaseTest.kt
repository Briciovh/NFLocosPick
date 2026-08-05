package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.ScoringRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La orquestación (fetch ESPN, cálculo de ganadores, weekId, escritura de
 * picks/standings) ahora corre server-side en la Cloud Function
 * "scoreGroupWeek" (functions/src/scoring.ts) — ScoreWeekPicksUseCase es un
 * simple delegado a ScoringRepository. Esos casos de negocio (empates,
 * ganador home/away, derivación de weekId) se cubren en las pruebas de la
 * Cloud Function, no aquí.
 */
private class FakeScoringRepository(
    private val returnValue: Int = 3
) : ScoringRepository {
    var wasCalled   = false
    var lastGroupId : String? = null

    override suspend fun scoreWeek(groupId: String): Int {
        wasCalled   = true
        lastGroupId = groupId
        return returnValue
    }
}

class ScoreWeekPicksUseCaseTest {

    @Test
    fun `invoke delegates to ScoringRepository with the given groupId`() = runBlocking {
        val scoring = FakeScoringRepository(returnValue = 5)
        val useCase = ScoreWeekPicksUseCase(scoring)

        val result = useCase("group1")

        assertEquals(5, result)
        assertEquals(true, scoring.wasCalled)
        assertEquals("group1", scoring.lastGroupId)
    }
}
