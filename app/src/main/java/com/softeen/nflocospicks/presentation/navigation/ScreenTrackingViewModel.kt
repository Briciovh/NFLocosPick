package com.softeen.nflocospicks.presentation.navigation

import androidx.lifecycle.ViewModel
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScreenTrackingViewModel @Inject constructor(
    private val logger: AppLogger
) : ViewModel() {
    fun trackScreen(screenName: String) {
        logger.logEvent(AppEvent.ScreenViewed(screenName))
    }
}
