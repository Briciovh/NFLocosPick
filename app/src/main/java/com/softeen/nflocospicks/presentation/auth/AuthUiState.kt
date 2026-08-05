package com.softeen.nflocospicks.presentation.auth

import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.User

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Authenticated(val user: User) : AuthUiState()
    data class LinkSent(val email: String) : AuthUiState()
    data class PhoneCodeSent(val phoneNumber: String) : AuthUiState()
    data class Error(val error: AuthError) : AuthUiState()
}

sealed class AuthUiEffect {
    data object NavigateToGroups : AuthUiEffect()
}
