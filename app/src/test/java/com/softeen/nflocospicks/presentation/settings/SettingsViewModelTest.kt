package com.softeen.nflocospicks.presentation.settings

import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val prefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private val mockSessionRepo = mockk<MockSessionRepository>(relaxed = true)
    private val logger = mockk<AppLogger>(relaxed = true)

    @Before
    fun setUp() {
        every { prefsRepo.preferencesFlow } returns flowOf(UserPreferences())
    }

    private fun viewModel() = SettingsViewModel(prefsRepo, mockSessionRepo, logger)

    @Test
    fun `setFontScale logs analytics event`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setFontScale("GRANDE")
        verify { logger.logEvent(match { it.name == "font_scale_changed" && it.params["scale"] == "GRANDE" }) }
    }

    @Test
    fun `setIconScale logs analytics event`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setIconScale("PEQUENO")
        verify { logger.logEvent(match { it.name == "icon_scale_changed" && it.params["scale"] == "PEQUENO" }) }
    }
    
    @Test
    fun `setFavoriteTeam logs analytics event with team name`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setFavoriteTeam("KC")
        verify { logger.logEvent(match { it.name == "favorite_team_set" && it.params["team_name"] == "Chiefs" }) }
    }

    @Test
    fun `setFavoriteTeam with null clears the preference and does not log`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setFavoriteTeam(null)

        coVerify(exactly = 1) { prefsRepo.setFavoriteTeam(null) }
        verify(exactly = 0) { logger.logEvent(match { it.name == "favorite_team_set" }) }
    }

    @Test
    fun `disabling testing data clears the mock session and simulate flag before writing the toggle`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setUseTestingData(false)

        coVerifyOrder {
            mockSessionRepo.clearSession()
            prefsRepo.setSimulateGamesStarted(false)
            prefsRepo.setUseTestingData(false)
        }
    }

    @Test
    fun `enabling testing data does not touch the mock session`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setUseTestingData(true)

        coVerify(exactly = 1) { prefsRepo.setUseTestingData(true) }
        coVerify(exactly = 0) { mockSessionRepo.clearSession() }
        coVerify(exactly = 0) { prefsRepo.setSimulateGamesStarted(any()) }
    }

    @Test
    fun `enabling simulate-games pre-generates scores before setting the flag`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setSimulateGamesStarted(true)

        coVerifyOrder {
            mockSessionRepo.generateAndSaveScores(any())
            prefsRepo.setSimulateGamesStarted(true)
        }
        coVerify(exactly = 0) { mockSessionRepo.clearSession() }
    }

    @Test
    fun `disabling simulate-games clears the session`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()
        vm.setSimulateGamesStarted(false)

        coVerify(exactly = 1) { mockSessionRepo.clearSession() }
        coVerify(exactly = 1) { prefsRepo.setSimulateGamesStarted(false) }
        coVerify(exactly = 0) { mockSessionRepo.generateAndSaveScores(any()) }
    }
}
