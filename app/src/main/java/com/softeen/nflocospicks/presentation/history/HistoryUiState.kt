package com.softeen.nflocospicks.presentation.history

import com.softeen.nflocospicks.domain.model.WeekHistoryEntry

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Success(
        val weeks: List<WeekHistoryEntry>,
        val expandedWeekId: String? = null
    ) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}
