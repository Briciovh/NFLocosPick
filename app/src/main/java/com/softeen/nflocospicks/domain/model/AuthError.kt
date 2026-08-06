package com.softeen.nflocospicks.domain.model

/**
 * Locale-independent identifiers for every auth failure the UI can surface.
 * The data layer maps Firebase exceptions to these; the UI layer maps them to
 * localized string resources. No layer below the UI ever holds display text.
 */
enum class AuthError {
    GOOGLE_SIGN_IN_FAILED,
    NO_GOOGLE_ACCOUNT,
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    SIGN_IN_FAILED,
    REGISTRATION_FAILED,
    SEND_LINK_FAILED,
    EMAIL_LINK_SIGN_IN_FAILED,
    PHONE_VERIFICATION_FAILED,
    PHONE_SIGN_IN_FAILED,
    VERIFICATION_SESSION_EXPIRED,
    INVALID_VERIFICATION_CODE,
    USERNAME_TAKEN,
    PROFILE_UPDATE_FAILED,
    PASSWORD_CHANGE_FAILED,
    SEND_RESET_EMAIL_FAILED,
    PHONE_ALREADY_IN_USE,
    LINK_EMAIL_FAILED,
    LINK_PHONE_FAILED,
}

/** Carries a typed [AuthError] across layers; [cause] is kept only for logging. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)
