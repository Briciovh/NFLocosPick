package com.softeen.nflocospicks.presentation.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * One-shot side effects (navigation, toasts). BUFFERED so rapid emissions
     * are not dropped while the collector is suspended.
     */
    val effects = Channel<AuthUiEffect>(Channel.BUFFERED)

    init {
        // Restore session synchronously — FirebaseAuth.currentUser is in-memory, no I/O.
        userRepository.getCurrentUser()?.let { user ->
            _uiState.value = AuthUiState.Authenticated(user)
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val user = userRepository.signInWithGoogle(context)
                _uiState.value = AuthUiState.Authenticated(user)
                effects.send(AuthUiEffect.NavigateToGroups)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker — silently return to Idle, no error shown.
                _uiState.value = AuthUiState.Idle
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            userRepository.signOut()
            _uiState.value = AuthUiState.Idle
        }
    }
}
