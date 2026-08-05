package com.softeen.nflocospicks.presentation.auth

import androidx.annotation.StringRes
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.AuthError

/** UI-layer mapping from a typed [AuthError] to its localized message resource. */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.GOOGLE_SIGN_IN_FAILED -> R.string.error_google_sign_in_failed
    AuthError.NO_GOOGLE_ACCOUNT -> R.string.login_no_google_account
    AuthError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AuthError.EMAIL_ALREADY_IN_USE -> R.string.error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.error_weak_password
    AuthError.NETWORK -> R.string.error_network
    AuthError.SIGN_IN_FAILED -> R.string.error_sign_in_failed
    AuthError.REGISTRATION_FAILED -> R.string.error_registration_failed
    AuthError.SEND_LINK_FAILED -> R.string.error_send_link_failed
    AuthError.EMAIL_LINK_SIGN_IN_FAILED -> R.string.error_email_link_sign_in_failed
    AuthError.PHONE_VERIFICATION_FAILED -> R.string.error_phone_verification_failed
    AuthError.PHONE_SIGN_IN_FAILED -> R.string.error_phone_sign_in_failed
    AuthError.VERIFICATION_SESSION_EXPIRED -> R.string.error_verification_expired
    AuthError.INVALID_VERIFICATION_CODE -> R.string.error_invalid_verification_code
}
