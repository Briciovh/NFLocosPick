package com.softeen.nflocospicks.presentation.auth

import com.softeen.nflocospicks.domain.model.User

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Authenticated(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class AuthUiEffect {
    data object NavigateToGroups : AuthUiEffect()
}
