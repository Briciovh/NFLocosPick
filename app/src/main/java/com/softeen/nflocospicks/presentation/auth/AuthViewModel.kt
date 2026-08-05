package com.softeen.nflocospicks.presentation.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private var verificationId: String? = null
    private var verificationJob: Job? = null

    init {
        // Phase 1 — synchronous restore: FirebaseAuth.currentUser is in-memory, no I/O.
        userRepository.getCurrentUser()?.let { user ->
            _uiState.value = AuthUiState.Authenticated(user)
            // Phase 2 — async role sync: delivers the real Firestore role within seconds.
            watchRole(user.uid)
        }

        // Reactive restore: keeps uiState in sync with sign-ins that complete outside this
        // ViewModel's own methods — namely the email-link deep link handled in MainActivity,
        // which signs in directly through the repository with no ViewModel involved.
        viewModelScope.launch {
            userRepository.currentUserFlow.collect { user ->
                if (user != null) {
                    if (_uiState.value !is AuthUiState.Authenticated) {
                        _uiState.value = AuthUiState.Authenticated(user)
                        watchRole(user.uid)
                        effects.send(AuthUiEffect.NavigateToGroups)
                    }
                } else if (_uiState.value is AuthUiState.Authenticated) {
                    _uiState.value = AuthUiState.Idle
                }
            }
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.signInWithGoogle(context)
                .onSuccess { result ->
                    _uiState.value = AuthUiState.Authenticated(result.user)
                    watchRole(result.user.uid)
                    effects.send(AuthUiEffect.NavigateToGroups)
                    logger.logEvent(AppEvent.SignIn("google"))
                    if (result.isNewUser) logger.logEvent(AppEvent.SignUp("google"))
                }
                .onFailure { e ->
                    // User dismissed the CredentialManager picker — silently return to Idle,
                    // no error shown.
                    if (e is AuthException && e.cause is GetCredentialCancellationException) {
                        _uiState.value = AuthUiState.Idle
                    } else {
                        _uiState.value = AuthUiState.Error(e.toAuthError(AuthError.GOOGLE_SIGN_IN_FAILED))
                    }
                }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.signInWithEmail(email, password)
                .onSuccess { result ->
                    _uiState.value = AuthUiState.Authenticated(result.user)
                    watchRole(result.user.uid)
                    effects.send(AuthUiEffect.NavigateToGroups)
                    logger.logEvent(AppEvent.SignIn("email"))
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState.Error(e.toAuthError(AuthError.SIGN_IN_FAILED))
                }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.signUpWithEmail(email, password)
                .onSuccess { result ->
                    _uiState.value = AuthUiState.Authenticated(result.user)
                    watchRole(result.user.uid)
                    effects.send(AuthUiEffect.NavigateToGroups)
                    logger.logEvent(AppEvent.SignUp("email"))
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState.Error(e.toAuthError(AuthError.REGISTRATION_FAILED))
                }
        }
    }

    fun sendSignInLink(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.sendSignInLinkToEmail(email)
                .onSuccess { _uiState.value = AuthUiState.LinkSent(email) }
                .onFailure { e ->
                    _uiState.value = AuthUiState.Error(e.toAuthError(AuthError.SEND_LINK_FAILED))
                }
        }
    }

    fun startPhoneVerification(activity: Activity, phoneNumber: String) {
        verificationJob?.cancel()
        verificationId = null
        verificationJob = viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.verifyPhoneNumber(activity, phoneNumber).collect { event ->
                when (event) {
                    is PhoneVerificationEvent.CodeSent -> {
                        verificationId = event.verificationId
                        _uiState.value = AuthUiState.PhoneCodeSent(phoneNumber)
                    }
                    is PhoneVerificationEvent.AutoVerified -> {
                        _uiState.value = AuthUiState.Authenticated(event.result.user)
                        watchRole(event.result.user.uid)
                        effects.send(AuthUiEffect.NavigateToGroups)
                        logger.logEvent(AppEvent.SignIn("phone"))
                        if (event.result.isNewUser) logger.logEvent(AppEvent.SignUp("phone"))
                    }
                    is PhoneVerificationEvent.Failed -> {
                        _uiState.value = AuthUiState.Error(event.error)
                    }
                }
            }
        }
    }

    fun verifyPhoneCode(smsCode: String) {
        val id = verificationId
        if (id == null) {
            _uiState.value = AuthUiState.Error(AuthError.VERIFICATION_SESSION_EXPIRED)
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            userRepository.signInWithPhoneCredential(id, smsCode)
                .onSuccess { result ->
                    _uiState.value = AuthUiState.Authenticated(result.user)
                    watchRole(result.user.uid)
                    effects.send(AuthUiEffect.NavigateToGroups)
                    logger.logEvent(AppEvent.SignIn("phone"))
                    if (result.isNewUser) logger.logEvent(AppEvent.SignUp("phone"))
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState.Error(e.toAuthError(AuthError.INVALID_VERIFICATION_CODE))
                }
        }
    }

    fun resetState() {
        verificationJob?.cancel()
        verificationId = null
        _uiState.value = AuthUiState.Idle
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

private fun Throwable.toAuthError(fallback: AuthError): AuthError =
    (this as? AuthException)?.error ?: fallback
