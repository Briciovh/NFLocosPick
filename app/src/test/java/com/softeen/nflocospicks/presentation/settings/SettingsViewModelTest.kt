package com.softeen.nflocospicks.presentation.settings

import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
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
}
