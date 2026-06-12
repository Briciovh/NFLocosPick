package com.softeen.nflocospicks.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val getGamesUseCase = mockk<GetCurrentWeekGamesUseCase>()
    private val prefsRepo       = mockk<UserPreferencesRepository>()
    private val mockSessionRepo = mockk<MockSessionRepository>()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun game(id: String = "g1", weekId: String = "2025-week-12") = Game(
        id           = id,
        weekId       = weekId,
        homeTeam     = "Chiefs",
        awayTeam     = "Raiders",
        homeTeamAbbr = "KC",
        awayTeamAbbr = "LV",
        kickoffTime  = Long.MAX_VALUE,
        homeScore    = null,
        awayScore    = null,
        status       = GameStatus.SCHEDULED
    )

    private fun viewModel(groupId: String = "real-group-1") = ScheduleViewModel(
        getCurrentWeekGamesUseCase = getGamesUseCase,
        preferencesRepository      = prefsRepo,
        mockSessionRepository      = mockSessionRepo,
        savedStateHandle           = SavedStateHandle(mapOf("groupId" to groupId))
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `after init, success state contains the games returned by the use case`() = runTest(coroutineRule.dispatcher) {
        val game = game()
        coEvery { getGamesUseCase(any()) } returns listOf(game)

        val vm = viewModel()

        val state = vm.uiState.value as ScheduleUiState.Success
        assertEquals(listOf(game), state.games)
        assertEquals("2025-week-12", state.weekId)
    }

    @Test
    fun `after init, error state contains the exception message when use case throws`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesUseCase(any()) } throws RuntimeException("Sin conexión")

        val vm = viewModel()

        val state = vm.uiState.value as ScheduleUiState.Error
        assertEquals("Sin conexión", state.message)
    }

    @Test
    fun `calling loadGames again re-fetches and replaces the previous state`() = runTest(coroutineRule.dispatcher) {
        val first  = listOf(game("g1", "2025-week-12"))
        val second = listOf(game("g2", "2025-week-13"))
        coEvery { getGamesUseCase(any()) } returnsMany listOf(first, second)

        val vm = viewModel()          // init triggers first call → state = Success(first)
        vm.loadGames()                // explicit reload → second call → state = Success(second)

        val state = vm.uiState.value as ScheduleUiState.Success
        assertEquals("g2", state.games.single().id)
        assertEquals("2025-week-13", state.weekId)
    }

    @Test
    fun `mock group ID uses mock data instead of calling GetCurrentWeekGamesUseCase`() = runTest(coroutineRule.dispatcher) {
        every { prefsRepo.preferencesFlow }       returns flowOf(UserPreferences())
        every { mockSessionRepo.sessionFlow }     returns flowOf(MockSessionState())

        val vm = viewModel(groupId = MockDataProvider.MOCK_GROUP_ID)
        vm.loadGames()   // explicit call — must be ignored for mock group

        // Use case was never invoked
        coVerify(exactly = 0) { getGamesUseCase(any()) }
        // State is Success with mock games
        assertTrue(vm.uiState.value is ScheduleUiState.Success)
    }
}
