package com.softeen.nflocospicks.presentation.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val logger: AppLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * One-shot side effects (navigation, toasts). BUFFERED so rapid emissions
     * are not dropped while the collector is suspended.
     */
    val effects = Channel<AuthUiEffect>(Channel.BUFFERED)

    private var roleWatcherJob: Job? = null

    init {
        // Phase 1 — synchronous restore: FirebaseAuth.currentUser is in-memory, no I/O.
        userRepository.getCurrentUser()?.let { user ->
            _uiState.value = AuthUiState.Authenticated(user)
            // Phase 2 — async role sync: delivers the real Firestore role within seconds.
            watchRole(user.uid)
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = userRepository.signInWithGoogle(context)
                _uiState.value = AuthUiState.Authenticated(result.user)
                watchRole(result.user.uid)
                effects.send(AuthUiEffect.NavigateToGroups)
                logger.logEvent(AppEvent.SignIn)
                if (result.isNewUser) logger.logEvent(AppEvent.SignUp)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker — silently return to Idle, no error shown.
                _uiState.value = AuthUiState.Idle
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        roleWatcherJob?.cancel()
        roleWatcherJob = null
        viewModelScope.launch {
            userRepository.signOut()
            logger.logEvent(AppEvent.SignOut)
            _uiState.value = AuthUiState.Idle
        }
    }

    private fun watchRole(uid: String) {
        roleWatcherJob?.cancel()
        roleWatcherJob = viewModelScope.launch {
            userRepository.watchCurrentUser(uid).collect { freshUser ->
                if (_uiState.value is AuthUiState.Authenticated) {
                    _uiState.value = AuthUiState.Authenticated(freshUser)
                }
            }
        }
    }
}
