package com.softeen.nflocospicks.presentation.account

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.UpdateUserProfileUseCase
import com.softeen.nflocospicks.domain.usecase.UploadProfilePhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
    private val uploadProfilePhotoUseCase: UploadProfilePhotoUseCase
) : ViewModel() {

    private val _usernameAvailability = MutableStateFlow<UsernameAvailability>(UsernameAvailability.Unknown)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()

    private val _saveState = MutableStateFlow<AccountSaveState>(AccountSaveState.Idle)
    val saveState: StateFlow<AccountSaveState> = _saveState.asStateFlow()

    private val _photoUploadState = MutableStateFlow<PhotoUploadState>(PhotoUploadState.Idle)
    val photoUploadState: StateFlow<PhotoUploadState> = _photoUploadState.asStateFlow()

    private val _changePasswordState = MutableStateFlow<ChangePasswordState>(ChangePasswordState.Idle)
    val changePasswordState: StateFlow<ChangePasswordState> = _changePasswordState.asStateFlow()

    private val _emailLinkState = MutableStateFlow<EmailLinkState>(EmailLinkState.Idle)
    val emailLinkState: StateFlow<EmailLinkState> = _emailLinkState.asStateFlow()

    private val _phoneLinkState = MutableStateFlow<PhoneLinkState>(PhoneLinkState.Idle)
    val phoneLinkState: StateFlow<PhoneLinkState> = _phoneLinkState.asStateFlow()

    private var phoneLinkVerificationId: String? = null
    private var phoneLinkJob: Job? = null

    /** False for Google-only / phone-only accounts — no password to change. Read once; a
     *  linked auth provider doesn't change over the lifetime of this screen. */
    val hasPasswordProvider: Boolean get() = userRepository.hasPasswordProvider()

    /** One-shot side effects (e.g. save confirmation). BUFFERED so rapid emissions aren't dropped. */
    val effects = Channel<AccountUiEffect>(Channel.BUFFERED)

    // Pair(candidate query, user's current username) — the current username travels alongside
    // each query so flatMapLatest can short-circuit "unchanged" without a second field to key on.
    private val usernameQueries = MutableStateFlow<Pair<String, String?>?>(null)

    init {
        usernameQueries
            .debounce(400)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query == null) return@flatMapLatest flowOf(UsernameAvailability.Unknown)
                val (candidate, current) = query
                val normalized = candidate.trim().lowercase()
                when {
                    normalized.isBlank() -> flowOf(UsernameAvailability.Unknown)
                    normalized == current?.trim()?.lowercase() -> flowOf(UsernameAvailability.Unchanged)
                    else -> userRepository.isUsernameAvailable(normalized)
                        .map { available -> if (available) UsernameAvailability.Available else UsernameAvailability.Taken }
                        .onStart { emit(UsernameAvailability.Checking) }
                        // Without this, an uncaught exception here (e.g. a permission-denied
                        // Firestore listener error) would cancel the whole flatMapLatest chain,
                        // silently killing username checks for the rest of the screen's lifetime.
                        .catch { emit(UsernameAvailability.Error) }
                }
            }
            .onEach { _usernameAvailability.value = it }
            .launchIn(viewModelScope)
    }

    fun onUsernameChanged(candidate: String, currentUsername: String?) {
        usernameQueries.value = candidate to currentUsername
    }

    fun saveProfile(uid: String, username: String, displayName: String) {
        viewModelScope.launch {
            _saveState.value = AccountSaveState.Saving
            updateUserProfileUseCase(uid = uid, username = username, displayName = displayName)
                .onSuccess {
                    _saveState.value = AccountSaveState.Idle
                    effects.send(AccountUiEffect.Saved)
                }
                .onFailure { e ->
                    val error = (e as? AuthException)?.error ?: AuthError.PROFILE_UPDATE_FAILED
                    _saveState.value = AccountSaveState.Error(error)
                }
        }
    }

    fun uploadPhoto(uid: String, uri: Uri) {
        viewModelScope.launch {
            _photoUploadState.value = PhotoUploadState.Uploading
            uploadProfilePhotoUseCase(uid, uri)
                .onSuccess { downloadUrl ->
                    _photoUploadState.value = PhotoUploadState.Idle
                    effects.send(AccountUiEffect.PhotoUploaded(downloadUrl))
                }
                .onFailure { e ->
                    val error = (e as? AuthException)?.error ?: AuthError.PROFILE_UPDATE_FAILED
                    _photoUploadState.value = PhotoUploadState.Error(error)
                }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _changePasswordState.value = ChangePasswordState.Saving
            userRepository.changePassword(currentPassword, newPassword)
                .onSuccess { _changePasswordState.value = ChangePasswordState.Success }
                .onFailure { e ->
                    val error = (e as? AuthException)?.error ?: AuthError.PASSWORD_CHANGE_FAILED
                    _changePasswordState.value = ChangePasswordState.Error(error)
                }
        }
    }

    fun sendEmailLink(email: String) {
        viewModelScope.launch {
            _emailLinkState.value = EmailLinkState.Sending
            userRepository.sendSignInLinkToEmail(email)
                .onSuccess { _emailLinkState.value = EmailLinkState.Sent(email) }
                .onFailure { e ->
                    val error = (e as? AuthException)?.error ?: AuthError.LINK_EMAIL_FAILED
                    _emailLinkState.value = EmailLinkState.Error(error)
                }
        }
    }

    fun startPhoneLink(activity: Activity, phoneNumber: String) {
        phoneLinkJob?.cancel()
        phoneLinkVerificationId = null
        phoneLinkJob = viewModelScope.launch {
            _phoneLinkState.value = PhoneLinkState.Sending
            userRepository.linkPhoneNumber(activity, phoneNumber).collect { event ->
                when (event) {
                    is PhoneVerificationEvent.CodeSent -> {
                        phoneLinkVerificationId = event.verificationId
                        _phoneLinkState.value = PhoneLinkState.CodeSent(phoneNumber)
                    }
                    is PhoneVerificationEvent.AutoVerified -> {
                        _phoneLinkState.value = PhoneLinkState.Idle
                    }
                    is PhoneVerificationEvent.Failed -> {
                        _phoneLinkState.value = PhoneLinkState.Error(event.error)
                    }
                }
            }
        }
    }

    fun verifyPhoneLinkCode(smsCode: String) {
        val id = phoneLinkVerificationId
        if (id == null) {
            _phoneLinkState.value = PhoneLinkState.Error(AuthError.VERIFICATION_SESSION_EXPIRED)
            return
        }
        viewModelScope.launch {
            _phoneLinkState.value = PhoneLinkState.Verifying
            userRepository.linkPhoneCredential(id, smsCode)
                .onSuccess { _phoneLinkState.value = PhoneLinkState.Idle }
                .onFailure { e ->
                    val error = (e as? AuthException)?.error ?: AuthError.LINK_PHONE_FAILED
                    _phoneLinkState.value = PhoneLinkState.Error(error)
                }
        }
    }
}
