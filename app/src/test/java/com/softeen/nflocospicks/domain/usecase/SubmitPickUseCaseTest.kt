package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.repository.PickRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class FakePickRepository : PickRepository {
    var submitCalled = false
    var lastTeamAbbr: String? = null

    override suspend fun submitPick(
        groupId: String, weekId: String, userId: String,
        gameId: String, teamAbbr: String
    ) {
        submitCalled = true
        lastTeamAbbr = teamAbbr
    }

    override suspend fun getPicksForWeek(
        groupId: String, weekId: String, userId: String
    ): Map<String, Pick> = emptyMap()
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class SubmitPickUseCaseTest {

    @Test
    fun `pick before kickoff calls submitPick on the repository`() = runBlocking {
        val repo = FakePickRepository()
        val useCase = SubmitPickUseCase(repo)

        useCase(
            groupId     = "g1",
            weekId      = "2025-week-12",
            userId      = "user1",
            gameId      = "game1",
            teamAbbr    = "KC",
            kickoffTime = Long.MAX_VALUE   // muy en el futuro — nunca llegará
        )

        assertTrue("submitPick debe llamarse cuando el partido no ha comenzado", repo.submitCalled)
        assertEquals("KC", repo.lastTeamAbbr)
    }

    @Test
    fun `pick after kickoff throws IllegalStateException without calling the repository`() = runBlocking {
        val repo = FakePickRepository()
        val useCase = SubmitPickUseCase(repo)

        var thrown: IllegalStateException? = null
        try {
            useCase(
                groupId     = "g1",
                weekId      = "2025-week-12",
                userId      = "user1",
                gameId      = "game1",
                teamAbbr    = "KC",
                kickoffTime = 0L   // epoch — definitivamente en el pasado
            )
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertNotNull("Debe lanzar IllegalStateException", thrown)
        assertFalse("No debe llamar al repositorio si el partido ya comenzó", repo.submitCalled)
    }

    @Test
    fun `exception carries the expected Spanish message`() = runBlocking {
        val useCase = SubmitPickUseCase(FakePickRepository())

        var message: String? = null
        try {
            useCase(
                groupId     = "g1",
                weekId      = "2025-week-12",
                userId      = "user1",
                gameId      = "game1",
                teamAbbr    = "KC",
                kickoffTime = 0L
            )
        } catch (e: IllegalStateException) {
            message = e.message
        }

        assertEquals("El partido ya comenzó, no puedes cambiar tu pick", message)
    }
}
