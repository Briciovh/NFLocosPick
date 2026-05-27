package com.softeen.nflocospicks.domain.repository

import android.content.Context
import com.softeen.nflocospicks.domain.model.User

/**
 * Authentication + user-profile contract.
 *
 * Note: [android.content.Context] appears here solely because CredentialManager requires
 * an Activity context at the call site. No Firebase or Retrofit type crosses this boundary.
 */
interface UserRepository {
    /** Sign in with Google via CredentialManager and persist the user profile. */
    suspend fun signInWithGoogle(activityContext: Context): User

    /** Clear the Firebase Auth session. */
    suspend fun signOut()

    /**
     * Returns the currently signed-in user from the in-memory Firebase Auth state,
     * or null if no session exists. Synchronous — safe to call from an init block.
     */
    fun getCurrentUser(): User?

    /** Write (or merge) the user document to Firestore `users/{uid}`. */
    suspend fun saveUserToFirestore(user: User)
}
