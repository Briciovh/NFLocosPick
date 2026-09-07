package com.softeen.nflocospicks.presentation.navigation

import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class ScreenTrackingViewModelTest {

    // trackScreen() is synchronous today, but the rule keeps Dispatchers.Main
    // valid in case the ViewModel ever touches viewModelScope.
    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val logger = mockk<AppLogger>(relaxed = true)
    private val viewModel = ScreenTrackingViewModel(logger)

    @Test
    fun `trackScreen logs a screen_viewed event with the given name`() {
        viewModel.trackScreen("groups")

        verify(exactly = 1) {
            logger.logEvent(match {
                it is AppEvent.ScreenViewed && it.name == "screen_viewed" && it.params["screen_name"] == "groups"
            })
        }
    }

    @Test
    fun `each trackScreen call logs its own event`() {
        viewModel.trackScreen("login")
        viewModel.trackScreen("picks")

        verify(exactly = 1) { logger.logEvent(match { it.params["screen_name"] == "login" }) }
        verify(exactly = 1) { logger.logEvent(match { it.params["screen_name"] == "picks" }) }
    }
}
