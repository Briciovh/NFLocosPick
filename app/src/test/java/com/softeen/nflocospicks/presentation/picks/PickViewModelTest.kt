package com.softeen.nflocospicks.presentation.picks

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SubmitPickUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getGamesUseCase   = mockk<GetCurrentWeekGamesUseCase>()
    private val getPicksUseCase   = mockk<GetWeekPicksUseCase>()
    private val submitPickUseCase = mockk<SubmitPickUseCase>()
    private val scoreUseCase      = mockk<ScoreWeekPicksUseCase>()
    private val userRepo          = mockk<UserRepository>()
    private val prefsRepo         = mockk<UserPreferencesRepository>()
    private val mockSessionRepo   = mockk<MockSessionRepository>()
    private val logger            = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    private val testGame = Game(
        id           = "game1",
        weekId       = "2025-week-12",
        homeTeam     = "Chiefs",
        awayTeam     = "Raiders",
        homeTeamAbbr = "KC",
        awayTeamAbbr = "LV",
        kickoffTime  = Long.MAX_VALUE,   // sin bloquear — partido en el futuro
        homeScore    = null,
        awayScore    = null,
        status       = GameStatus.SCHEDULED
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() }       returns testUser
        every { prefsRepo.preferencesFlow }       returns flowOf(UserPreferences())
        every { mockSessionRepo.sessionFlow }     returns flowOf(MockSessionState())
        coEvery { getGamesUseCase(any()) }        returns listOf(testGame)
        coEvery { getPicksUseCase(any(), any(), any()) } returns emptyMap()
        coEvery { scoreUseCase(any()) }           returns 0
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private fun viewModel(groupId: String = "real-group-1") = PickViewModel(
        getCurrentWeekGamesUseCase = getGamesUseCase,
        getWeekPicksUseCase        = getPicksUseCase,
        submitPickUseCase          = submitPickUseCase,
        scoreWeekPicksUseCase      = scoreUseCase,
        userRepository             = userRepo,
        preferencesRepository      = prefsRepo,
        mockSessionRepository      = mockSessionRepo,
        logger                     = logger,
        savedStateHandle           = SavedStateHandle(mapOf("groupId" to groupId))
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `after init, success state contains one GamePickItem per game`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()

        val state = vm.uiState.value as PickUiState.Success
        assertEquals(1, state.items.size)
        assertEquals(testGame, state.items.single().game)
        assertNull(state.items.single().pickedTeam)
        assertEquals("2025-week-12", state.weekId)
    }

    @Test
    fun `submitPick on unlocked game updates pickedTeam in state`() = runTest(coroutineRule.dispatcher) {
        coEvery { submitPickUseCase(any(), any(), any(), any(), any(), any()) } just Runs

        val vm = viewModel()
        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE)

        val state = vm.uiState.value as PickUiState.Success
        assertEquals("KC", state.items.single().pickedTeam)
    }

    @Test
    fun `submitPick reverts optimistic update and sets errorMessage when use case throws`() = runTest(coroutineRule.dispatcher) {
        coEvery {
            submitPickUseCase(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("El partido ya comenzó, no puedes cambiar tu pick")

        val vm = viewModel()
        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE)

        // Estado revertido — pick sigue en null
        val state = vm.uiState.value as PickUiState.Success
        assertNull(state.items.single().pickedTeam)
        // Mensaje de error visible
        assertEquals(
            "El partido ya comenzó, no puedes cambiar tu pick",
            vm.errorMessage.value
        )
    }

    @Test
    fun `onErrorShown clears the errorMessage`() = runTest(coroutineRule.dispatcher) {
        coEvery {
            submitPickUseCase(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Error")

        val vm = viewModel()
        vm.submitPick("game1", "KC", Long.MAX_VALUE)  // sets errorMessage

        vm.onErrorShown()

        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `triggerSync calls scoreWeekPicksUseCase then reloads data`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.triggerSync()

        coVerify(exactly = 1) { scoreUseCase("real-group-1") }
        // loadData() fue llamado de nuevo — use case fue invocado 2 veces en total (init + reload)
        coVerify(exactly = 2) { getGamesUseCase("real-group-1") }
    }
}
