package com.softeen.nflocospicks.domain.model

import java.text.Normalizer

data class User(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val role: UserRole = UserRole.REGULAR,
    val phoneNumber: String? = null,
    val username: String? = null,
    // Epoch millis del último sign-in (PR-20). Re-escrito en CADA login (no solo el primero) —
    // es lo que resetea el reloj de 1 año de inactividad y reactiva una cuenta deshabilitada.
    val lastActive: Long? = null
)

/** Falls back to the username wherever a blank displayName would otherwise show empty text. */
val User.effectiveDisplayName: String get() = displayName.ifBlank { username.orEmpty() }

/**
 * A profile is complete once it has a username plus at least one contact method (email or
 * phone) — the minimum the app needs to identify and reach the user. Once true for an account,
 * the profile-completion screen must never be shown to it again.
 */
val User.isProfileComplete: Boolean
    get() = !username.isNullOrBlank() && (email.isNotBlank() || !phoneNumber.isNullOrBlank())

/**
 * Suggests a starting-point username derived from whatever identity info the account already
 * has (Google displayName, or the email's local part), so a first-time-setup user doesn't have
 * to think one up from scratch. Never overrides an existing username — the field stays freely
 * editable either way, this is only a pre-filled starting point. Returns null when there's
 * nothing to derive from (e.g. a phone-only account with no displayName).
 */
fun User.suggestedUsername(): String? {
    if (!username.isNullOrBlank()) return username
    displayName.toUsernameSlug().takeIf { it.isNotBlank() }?.let { return it }
    email.substringBefore("@").toUsernameSlug().takeIf { it.isNotBlank() }?.let { return it }
    return null
}

/** Lowercases, strips diacritics, and collapses anything outside `[a-z0-9]` into `_`. */
private fun String.toUsernameSlug(): String {
    val withoutDiacritics = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return withoutDiacritics
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .take(20)
        .trim('_')
}
