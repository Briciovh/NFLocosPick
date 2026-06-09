package com.softeen.nflocospicks.presentation.groups

import com.softeen.nflocospicks.domain.model.Group

// ── Estado de la lista (real-time listener) ───────────────────────────────────

sealed class GroupListUiState {
    data object Loading : GroupListUiState()
    data class Success(val groups: List<Group>) : GroupListUiState()
    data class Error(val message: String) : GroupListUiState()
}

// ── Estado de las acciones de mutación (crear / unirse) ───────────────────────

sealed class GroupActionUiState {
    data object Idle : GroupActionUiState()
    data object Loading : GroupActionUiState()
    data class Success(val group: Group) : GroupActionUiState()
    data class Error(val message: String) : GroupActionUiState()
}

// ── Efectos de un solo disparo (navegación, toasts) ───────────────────────────

sealed class GroupUiEffect {
    data class NavigateToGroupSession(val groupId: String)                   : GroupUiEffect()
    data object NavigateToLogin                                              : GroupUiEffect()
    data class ScoringResult(val groupId: String, val newlyScoredCount: Int) : GroupUiEffect()
    data class ScoringError(val message: String)                             : GroupUiEffect()
}
