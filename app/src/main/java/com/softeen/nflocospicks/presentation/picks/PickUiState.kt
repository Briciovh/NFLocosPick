package com.softeen.nflocospicks.presentation.picks

import com.softeen.nflocospicks.domain.model.Game

data class GamePickItem(
    val game: Game,
    val pickedTeam: String?,   // null = sin pick aún
    val isLocked: Boolean      // true cuando kickoffTime ya pasó
)

sealed class PickUiState {
    data object Loading : PickUiState()
    data class Success(val items: List<GamePickItem>, val weekId: String) : PickUiState()
    data class Error(val message: String) : PickUiState()
}
