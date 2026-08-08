package com.softeen.nflocospicks.presentation.leaderboard

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetLeaderboardUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val getLeaderboardUseCase = mockk<GetLeaderboardUseCase>()
    private val getGamesUseCase       = mockk<GetCurrentWeekGamesUseCase>()
    private val userRepo              = mockk<UserRepository>()
    private val prefsRepo             = mockk<UserPreferencesRepository>()
    private val mockSessionRepo       = mockk<MockSessionRepository>()
    private val logger                = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    private val preseasonGame = Game(
        id           = "game1",
        weekId       = "2025-pre-week-01",
        homeTeam     = "Chiefs",
        awayTeam     = "Raiders",
        homeTeamAbbr = "KC",
        awayTeamAbbr = "LV",
        kickoffTime  = Long.MAX_VALUE,
        homeScore    = null,
        awayScore    = null,
        status       = GameStatus.SCHEDULED,
        seasonType   = SeasonType.PRESEASON
    )

    private fun entry(userId: String, regular: Int, preseason: Int) = LeaderboardEntry(
        userId          = userId,
        displayName     = userId,
        photoUrl        = null,
        regularPoints   = regular,
        preseasonPoints = preseason,
        weeklyBreakdown = emptyMap(),
        rank            = 0
    )

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() }   returns testUser
        every { prefsRepo.preferencesFlow }   returns flowOf(UserPreferences())
        every { mockSessionRepo.sessionFlow } returns flowOf(MockSessionState())
    }

    private fun viewModel(groupId: String = "real-group-1") = LeaderboardViewModel(
        savedStateHandle        = SavedStateHandle(mapOf("groupId" to groupId)),
        getLeaderboardUseCase   = getLeaderboardUseCase,
        getCurrentWeekGamesUseCase = getGamesUseCase,
        userRepository          = userRepo,
        preferencesRepository   = prefsRepo,
        mockSessionRepository   = mockSessionRepo,
        logger                  = logger
    )

    @Test
    fun `auto-selects PRESEASON tab when current week games are preseason`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesUseCase(any()) } returns listOf(preseasonGame)
        coEvery { getLeaderboardUseCase(any()) } returns flowOf(
            listOf(entry("user1", regular = 5, preseason = 9))
        )

        val vm = viewModel()

        val state = vm.uiState.value as LeaderboardUiState.Success
        assertEquals(SeasonType.PRESEASON, state.selectedSeasonType)
        assertEquals(9, state.entries.single().preseasonPoints)
    }

    @Test
    fun `stays Loading until standings have emitted, even after season is detected`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesUseCase(any()) } returns listOf(preseasonGame)
        // Sin emisión todavía — el listener de standings aún no manda datos.
        val standingsFlow = MutableSharedFlow<List<LeaderboardEntry>>(replay = 0)
        coEvery { getLeaderboardUseCase(any()) } returns standingsFlow

        val vm = viewModel()

        assertTrue(vm.uiState.value is LeaderboardUiState.Loading)
    }

    @Test
    fun `onTabSelected re-ranks without calling getLeaderboardUseCase again`() = runTest(coroutineRule.dispatcher) {
        coEvery { getGamesUseCase(any()) } returns emptyList() // sin juegos -> detecta REGULAR por default
        coEvery { getLeaderboardUseCase(any()) } returns flowOf(
            listOf(
                entry("user1", regular = 5, preseason = 20),
                entry("user2", regular = 10, preseason = 1)
            )
        )

        val vm = viewModel()
        vm.onTabSelected(SeasonType.PRESEASON)

        val state = vm.uiState.value as LeaderboardUiState.Success
        assertEquals(SeasonType.PRESEASON, state.selectedSeasonType)
        assertEquals("user1", state.entries.first().userId)
        verify(exactly = 1) { getLeaderboardUseCase("real-group-1") }
    }
}
