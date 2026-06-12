package com.softeen.nflocospicks.integration

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.PickRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SubmitPickUseCase
import com.softeen.nflocospicks.presentation.picks.PickUiState
import com.softeen.nflocospicks.presentation.picks.PickViewModel
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests: PickViewModel + real SubmitPickUseCase + FakePickRepository.
 *
 * Unit tests (Step 4) verified the ViewModel's state machine using mocked use cases.
 * These tests verify that the real use case's business rule (kickoff lock) flows
 * correctly through the ViewModel's optimistic-update and error-revert logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PickViewModelIntegrationTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // ── Fake repository — captures calls made by the real use case ─────────────

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

    // ── Shared mocks (not the subject under test) ─────────────────────────────

    private val getGamesUseCase = mockk<GetCurrentWeekGamesUseCase>()
    private val getPicksUseCase = mockk<GetWeekPicksUseCase>()
    private val scoreUseCase    = mockk<ScoreWeekPicksUseCase>()
    private val userRepo        = mockk<UserRepository>()
    private val prefsRepo       = mockk<UserPreferencesRepository>()
    private val mockSessionRepo = mockk<MockSessionRepository>()
    private val logger          = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    private val testGame = Game(
        id = "game1", weekId = "2025-week-12",
        homeTeam = "Chiefs", awayTeam = "Raiders",
        homeTeamAbbr = "KC", awayTeamAbbr = "LV",
        kickoffTime = Long.MAX_VALUE,
        homeScore = null, awayScore = null,
        status = GameStatus.SCHEDULED
    )

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() }              returns testUser
        every { prefsRepo.preferencesFlow }              returns flowOf(UserPreferences())
        every { mockSessionRepo.sessionFlow }            returns flowOf(MockSessionState())
        coEvery { getGamesUseCase(any()) }               returns listOf(testGame)
        coEvery { getPicksUseCase(any(), any(), any()) } returns emptyMap()
        coEvery { scoreUseCase(any()) }                  returns 0
    }

    // ── Factory: real SubmitPickUseCase wired to the provided fake repo ────────

    private fun viewModel(fakePickRepo: FakePickRepository) = PickViewModel(
        getCurrentWeekGamesUseCase = getGamesUseCase,
        getWeekPicksUseCase        = getPicksUseCase,
        submitPickUseCase          = SubmitPickUseCase(fakePickRepo),   // ← REAL
        scoreWeekPicksUseCase      = scoreUseCase,
        userRepository             = userRepo,
        preferencesRepository      = prefsRepo,
        mockSessionRepository      = mockSessionRepo,
        logger                     = logger,
        savedStateHandle           = SavedStateHandle(mapOf("groupId" to "real-group-1"))
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `submitPick with future kickoff persists pick through the real use case`() = runTest(coroutineRule.dispatcher) {
        val fakeRepo = FakePickRepository()
        val vm = viewModel(fakeRepo)

        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE)

        // Real use case did not throw → optimistic update stays
        val state = vm.uiState.value as PickUiState.Success
        assertEquals("KC", state.items.single().pickedTeam)
        // Real use case reached the real repository
        assertFalse("errorMessage debe estar vacío", vm.errorMessage.value != null)
        assertEquals("KC", fakeRepo.lastTeamAbbr)
    }

    @Test
    fun `submitPick with past kickoff triggers real IllegalStateException and reverts state`() = runTest(coroutineRule.dispatcher) {
        val fakeRepo = FakePickRepository()
        val vm = viewModel(fakeRepo)

        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = 0L) // epoch = pasado

        // Real use case threw → ViewModel revirtió el estado
        val state = vm.uiState.value as PickUiState.Success
        assertNull("El pick debe haberse revertido a null", state.items.single().pickedTeam)
        // Error message fue propagado desde la excepción del use case
        assertNotNull("errorMessage debe tener el mensaje del use case", vm.errorMessage.value)
        // El repositorio nunca fue alcanzado
        assertFalse("submitPick en el repo no debe haberse llamado", fakeRepo.submitCalled)
    }
}
