package com.softeen.nflocospicks.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the email address a sign-in link was sent to, so it can be recovered when the
 * user taps that link — possibly on a later app launch, or after the process died.
 */
@Singleton
class EmailLinkPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePendingEmail(email: String) {
        prefs.edit { putString(KEY_PENDING_EMAIL, email) }
    }

    fun readPendingEmail(): String? = prefs.getString(KEY_PENDING_EMAIL, null)

    fun clearPendingEmail() {
        prefs.edit { remove(KEY_PENDING_EMAIL) }
    }

    private companion object {
        const val PREFS_NAME = "nflocospicks_auth_prefs"
        const val KEY_PENDING_EMAIL = "pending_signin_email"
    }
}
