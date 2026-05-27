package com.softeen.nflocospicks.presentation.schedule

import com.softeen.nflocospicks.domain.model.Game

sealed class ScheduleUiState {
    data object Loading : ScheduleUiState()
    data class Success(val games: List<Game>, val weekId: String) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}
