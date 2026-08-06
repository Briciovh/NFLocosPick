package com.softeen.nflocospicks.presentation.account

import com.softeen.nflocospicks.domain.model.AuthError

/** Live result of checking a candidate username against `usernames/{username}`. */
sealed class UsernameAvailability {
    data object Unknown : UsernameAvailability()
    data object Checking : UsernameAvailability()
    data object Available : UsernameAvailability()
    data object Taken : UsernameAvailability()
    /** The candidate equals the user's current username — no collision check needed. */
    data object Unchanged : UsernameAvailability()
    /** The availability check itself failed (network, permissions) — neither taken nor free. */
    data object Error : UsernameAvailability()
}

sealed class AccountSaveState {
    data object Idle : AccountSaveState()
    data object Saving : AccountSaveState()
    data class Error(val error: AuthError) : AccountSaveState()
}

/** Decoupled from [AccountSaveState] — a photo upload commits immediately on its own,
 *  independent of the username/display-name Save button. */
sealed class PhotoUploadState {
    data object Idle : PhotoUploadState()
    data object Uploading : PhotoUploadState()
    data class Error(val error: AuthError) : PhotoUploadState()
}

sealed class ChangePasswordState {
    data object Idle : ChangePasswordState()
    data object Saving : ChangePasswordState()
    data object Success : ChangePasswordState()
    data class Error(val error: AuthError) : ChangePasswordState()
}

/** Linking an email to an account that had none — a real Firebase Auth credential, completed
 *  by the user opening the emailed link (see MainActivity.handleEmailLinkIntent). */
sealed class EmailLinkState {
    data object Idle : EmailLinkState()
    data object Sending : EmailLinkState()
    data class Sent(val email: String) : EmailLinkState()
    data class Error(val error: AuthError) : EmailLinkState()
}

/** Linking a phone number to an account that had none — mirrors [PhoneVerificationEvent] but
 *  scoped to the Account screen's own linking flow rather than sign-in. */
sealed class PhoneLinkState {
    data object Idle : PhoneLinkState()
    data object Sending : PhoneLinkState()
    data class CodeSent(val phoneNumber: String) : PhoneLinkState()
    data object Verifying : PhoneLinkState()
    data class Error(val error: AuthError) : PhoneLinkState()
}

sealed class AccountUiEffect {
    data object Saved : AccountUiEffect()
    data class PhotoUploaded(val photoUrl: String) : AccountUiEffect()
}
