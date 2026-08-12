package com.softeen.nflocospicks.presentation.picks

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.NflSeasonCalendar
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetGamesForWeekUseCase
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
import org.junit.Assert.assertFalse
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

    private val getGamesUseCase        = mockk<GetCurrentWeekGamesUseCase>()
    private val getGamesForWeekUseCase = mockk<GetGamesForWeekUseCase>()
    private val getPicksUseCase        = mockk<GetWeekPicksUseCase>()
    private val submitPickUseCase      = mockk<SubmitPickUseCase>()
    private val scoreUseCase           = mockk<ScoreWeekPicksUseCase>()
    private val userRepo               = mockk<UserRepository>()
    private val prefsRepo              = mockk<UserPreferencesRepository>()
    private val mockSessionRepo        = mockk<MockSessionRepository>()
    private val logger                 = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    // Semana regular 12 — coincide con lo que getGamesUseCase (la "semana
    // actual") devuelve por defecto, así que init la resuelve como el tab
    // seleccionado sin pasar por getGamesForWeekUseCase.
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
        status       = GameStatus.SCHEDULED,
        weekNumber   = 12
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() }       returns testUser
        every { prefsRepo.preferencesFlow }       returns flowOf(UserPreferences())
        every { mockSessionRepo.sessionFlow }     returns flowOf(MockSessionState())
        coEvery { getGamesUseCase(any()) }        returns listOf(testGame)
        coEvery { getGamesForWeekUseCase(any(), any()) } returns listOf(testGame)
        coEvery { getPicksUseCase(any(), any(), any()) } returns emptyMap()
        coEvery { scoreUseCase(any()) }           returns 0
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private fun viewModel(groupId: String = "real-group-1") = PickViewModel(
        getCurrentWeekGamesUseCase = getGamesUseCase,
        getGamesForWeekUseCase     = getGamesForWeekUseCase,
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
        assertEquals(false, state.isPreseason)
    }

    @Test
    fun `preseason games mark isPreseason true in success state`() = runTest(coroutineRule.dispatcher) {
        val preseasonGame = testGame.copy(
            weekId     = "2025-pre-week-02",
            seasonType = SeasonType.PRESEASON,
            weekNumber = 2
        )
        coEvery { getGamesUseCase(any()) } returns listOf(preseasonGame)

        val vm = viewModel()

        val state = vm.uiState.value as PickUiState.Success
        assertEquals(true, state.isPreseason)
    }

    @Test
    fun `FINAL game with future kickoffTime is still locked`() = runTest(coroutineRule.dispatcher) {
        // Reproduce el bug: kickoffTime corrupto hacia el futuro (p.ej. hack de debug)
        // en un juego que ESPN ya reporta como FINAL no debe dejarlo desbloqueado.
        val finalGame = testGame.copy(status = GameStatus.FINAL, kickoffTime = Long.MAX_VALUE)
        coEvery { getGamesUseCase(any()) } returns listOf(finalGame)

        val vm = viewModel()

        val state = vm.uiState.value as PickUiState.Success
        assertTrue(state.items.single().isLocked)
    }

    @Test
    fun `submitPick on unlocked game updates pickedTeam in state`() = runTest(coroutineRule.dispatcher) {
        coEvery { submitPickUseCase(any(), any(), any(), any(), any(), any(), any()) } just Runs

        val vm = viewModel()
        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE, status = GameStatus.SCHEDULED)

        val state = vm.uiState.value as PickUiState.Success
        assertEquals("KC", state.items.single().pickedTeam)
    }

    @Test
    fun `submitPick reverts optimistic update and sets errorMessage when use case throws`() = runTest(coroutineRule.dispatcher) {
        coEvery {
            submitPickUseCase(any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("El partido ya comenzó, no puedes cambiar tu pick")

        val vm = viewModel()
        vm.submitPick(gameId = "game1", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE, status = GameStatus.SCHEDULED)

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
            submitPickUseCase(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Error")

        val vm = viewModel()
        vm.submitPick("game1", "KC", Long.MAX_VALUE, GameStatus.SCHEDULED)  // sets errorMessage

        vm.onErrorShown()

        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `triggerSync calls scoreWeekPicksUseCase then reloads the selected week`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.triggerSync()

        coVerify(exactly = 1) { scoreUseCase("real-group-1") }
        // La semana "actual" solo se resuelve una vez (init) — triggerSync recarga
        // la semana seleccionada vía getGamesForWeekUseCase, no getGamesUseCase de nuevo.
        coVerify(exactly = 1) { getGamesUseCase("real-group-1") }
        coVerify(exactly = 1) { getGamesForWeekUseCase(SeasonType.REGULAR, 12) }
    }

    // ── Week tabs ────────────────────────────────────────────────────────────

    @Test
    fun `init selects the tab matching the week ESPN reports as current`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()

        val expectedIndex = NflSeasonCalendar.indexOf(SeasonType.REGULAR, 12)
        assertEquals(expectedIndex, vm.selectedWeekIndex.value)
        assertEquals(expectedIndex, vm.currentWeekIndex.value)
    }

    @Test
    fun `init falls back to the default tab when ESPN returns no games`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesUseCase(any()) } returns emptyList()
        coEvery { getGamesForWeekUseCase(SeasonType.REGULAR, 1) } returns
            listOf(testGame.copy(weekId = "2025-week-01", weekNumber = 1))

        val vm = viewModel()

        assertEquals(NflSeasonCalendar.DEFAULT_INDEX, vm.selectedWeekIndex.value)
        assertNull(vm.currentWeekIndex.value)
        coVerify(exactly = 1) { getGamesForWeekUseCase(SeasonType.REGULAR, 1) }
    }

    @Test
    fun `onWeekSelected maps the HOF tab to ESPN preseason week 1`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesForWeekUseCase(SeasonType.PRESEASON, 1) } returns
            listOf(testGame.copy(weekId = "2025-pre-week-01", seasonType = SeasonType.PRESEASON, weekNumber = 1))

        val vm = viewModel()
        vm.onWeekSelected(0)

        coVerify(exactly = 1) { getGamesForWeekUseCase(SeasonType.PRESEASON, 1) }
    }

    @Test
    fun `onWeekSelected maps the Super Bowl tab to ESPN postseason week 5`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesForWeekUseCase(SeasonType.POSTSEASON, 5) } returns
            listOf(testGame.copy(weekId = "2026-week-05", seasonType = SeasonType.POSTSEASON, weekNumber = 5))

        val vm = viewModel()
        vm.onWeekSelected(NflSeasonCalendar.indexOf(SeasonType.POSTSEASON, 5))

        coVerify(exactly = 1) { getGamesForWeekUseCase(SeasonType.POSTSEASON, 5) }
    }

    @Test
    fun `submitPick uses the weekId of the selected week, not the current week`() = runTest(coroutineRule.dispatcher) {
        val otherWeekGame = testGame.copy(id = "game3", weekId = "2025-week-03", weekNumber = 3)
        coEvery { getGamesForWeekUseCase(SeasonType.REGULAR, 3) } returns listOf(otherWeekGame)
        coEvery { submitPickUseCase(any(), any(), any(), any(), any(), any(), any()) } just Runs

        val vm = viewModel()
        vm.onWeekSelected(NflSeasonCalendar.indexOf(SeasonType.REGULAR, 3))
        vm.submitPick(gameId = "game3", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE, status = GameStatus.SCHEDULED)

        coVerify(exactly = 1) {
            submitPickUseCase(
                groupId     = "real-group-1",
                weekId      = "2025-week-03",
                userId      = "user1",
                gameId      = "game3",
                teamAbbr    = "KC",
                kickoffTime = Long.MAX_VALUE,
                status      = GameStatus.SCHEDULED
            )
        }
    }

    @Test
    fun `returning to an already loaded week shows it without a Loading state`() = runTest(coroutineRule.dispatcher) {
        val otherWeekGame = testGame.copy(id = "game3", weekId = "2025-week-03", weekNumber = 3)
        coEvery { getGamesForWeekUseCase(SeasonType.REGULAR, 3) } returns listOf(otherWeekGame)
        val otherIndex = NflSeasonCalendar.indexOf(SeasonType.REGULAR, 3)

        val vm = viewModel()
        val homeIndex = vm.selectedWeekIndex.value

        vm.onWeekSelected(otherIndex)  // primera visita — cachea
        vm.onWeekSelected(homeIndex)   // vuelve a la semana inicial (ya cacheada por init)
        vm.onWeekSelected(otherIndex)  // vuelve — debe salir de la cache, sin Loading

        val state = vm.uiState.value as PickUiState.Success
        assertEquals("2025-week-03", state.weekId)
    }

    @Test
    fun `an optimistic pick survives switching tabs away and back`() = runTest(coroutineRule.dispatcher) {
        val otherWeekGame = testGame.copy(id = "game3", weekId = "2025-week-03", weekNumber = 3)
        coEvery { getGamesForWeekUseCase(SeasonType.REGULAR, 3) } returns listOf(otherWeekGame)
        coEvery { submitPickUseCase(any(), any(), any(), any(), any(), any(), any()) } just Runs
        val otherIndex = NflSeasonCalendar.indexOf(SeasonType.REGULAR, 3)

        val vm = viewModel()
        val homeIndex = vm.selectedWeekIndex.value

        vm.onWeekSelected(otherIndex)
        vm.submitPick(gameId = "game3", teamAbbr = "KC", kickoffTime = Long.MAX_VALUE, status = GameStatus.SCHEDULED)
        vm.onWeekSelected(homeIndex)
        vm.onWeekSelected(otherIndex)

        val state = vm.uiState.value as PickUiState.Success
        assertEquals("KC", state.items.single().pickedTeam)
    }

    @Test
    fun `loadData after an error retries the selected week, not the current one`() = runTest(coroutineRule.dispatcher) {
        val otherIndex = NflSeasonCalendar.indexOf(SeasonType.REGULAR, 3)
        coEvery { getGamesForWeekUseCase(SeasonType.REGULAR, 3) } throws RuntimeException("network down") andThen
            listOf(testGame.copy(id = "game3", weekId = "2025-week-03", weekNumber = 3))

        val vm = viewModel()
        vm.onWeekSelected(otherIndex)   // falla -> Error
        assertTrue(vm.uiState.value is PickUiState.Error)

        vm.loadData()   // Retry

        assertTrue(vm.uiState.value is PickUiState.Success)
        coVerify(exactly = 2) { getGamesForWeekUseCase(SeasonType.REGULAR, 3) }
    }

    @Test
    fun `week tabs are hidden and no week fetch happens for the mock group`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel(groupId = MockDataProvider.MOCK_GROUP_ID)

        assertFalse(vm.weekTabsVisible)
        vm.onWeekSelected(5)

        coVerify(exactly = 0) { getGamesForWeekUseCase(any(), any()) }
    }

    @Test
    fun `refresh reloads the selected week and ends with isRefreshing false`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        val initialItems = (vm.uiState.value as PickUiState.Success).items

        vm.refresh()

        val state = vm.uiState.value as PickUiState.Success
        assertFalse(state.isRefreshing)
        assertEquals(initialItems, state.items)
        coVerify(exactly = 1) { getGamesForWeekUseCase(SeasonType.REGULAR, 12) }
    }
}
