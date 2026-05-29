package com.softeen.nflocospicks.presentation.leaderboard

import com.softeen.nflocospicks.domain.model.LeaderboardEntry

sealed class LeaderboardUiState {
    data object Loading : LeaderboardUiState()
    data class Success(val entries: List<LeaderboardEntry>) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}
