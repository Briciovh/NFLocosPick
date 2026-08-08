package com.softeen.nflocospicks.presentation.leaderboard

import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.SeasonType

sealed class LeaderboardUiState {
    data object Loading : LeaderboardUiState()
    data class Success(
        val entries: List<LeaderboardEntry>,
        val selectedSeasonType: SeasonType
    ) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}
