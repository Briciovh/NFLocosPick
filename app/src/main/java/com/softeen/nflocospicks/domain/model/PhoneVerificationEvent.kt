package com.softeen.nflocospicks.domain.model

/**
 * Events emitted while Firebase resolves a phone number sign-in attempt.
 * [CodeSent] is not terminal — Firebase may still auto-verify later via the SMS Retriever
 * API while the user is looking at the OTP entry field.
 */
sealed interface PhoneVerificationEvent {
    data class CodeSent(val verificationId: String) : PhoneVerificationEvent
    data class AutoVerified(val result: SignInResult) : PhoneVerificationEvent
    data class Failed(val error: AuthError) : PhoneVerificationEvent
}
