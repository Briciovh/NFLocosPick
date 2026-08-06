package com.softeen.nflocospicks.presentation.auth

import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.User

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    /** [isProfileSynced] is true only once the user's Firestore profile has actually been
     *  read (via watchCurrentUser) — the synchronous/reactive session-restore paths publish
     *  a FirebaseAuth-only User (no username, since that field lives in Firestore) and must
     *  not be treated as authoritative for profile-completeness decisions. */
    data class Authenticated(val user: User, val isProfileSynced: Boolean = false) : AuthUiState()
    data class LinkSent(val email: String) : AuthUiState()
    data class PhoneCodeSent(val phoneNumber: String) : AuthUiState()
    data class PasswordResetSent(val email: String) : AuthUiState()
    data class Error(val error: AuthError) : AuthUiState()
}
