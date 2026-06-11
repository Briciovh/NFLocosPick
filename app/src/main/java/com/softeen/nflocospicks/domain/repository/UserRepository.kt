package com.softeen.nflocospicks.domain.repository

import android.content.Context
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Authentication + user-profile contract.
 *
 * Note: [android.content.Context] appears here solely because CredentialManager requires
 * an Activity context at the call site. No Firebase or Retrofit type crosses this boundary.
 */
interface UserRepository {
    /** Sign in with Google via CredentialManager and persist the user profile. */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult

    /** Clear the Firebase Auth session. */
    suspend fun signOut()

    /**
     * Returns the currently signed-in user from the in-memory Firebase Auth state,
     * or null if no session exists. Synchronous — safe to call from an init block.
     * Role defaults to REGULAR; use [watchCurrentUser] to receive the real Firestore role.
     */
    fun getCurrentUser(): User?

    /** Write (or merge) the user document to Firestore `users/{uid}`. */
    suspend fun saveUserToFirestore(user: User)

    /** Real-time stream of the user document. Emits whenever role or profile changes in Firestore. */
    fun watchCurrentUser(uid: String): Flow<User>

    /** Update the role field for any user. Only callable by INSIDER users (enforced in UI layer). */
    suspend fun updateUserRole(uid: String, role: UserRole)

    /** Real-time stream of all user documents — used by UserManagementScreen. */
    fun getAllUsers(): Flow<List<User>>
}
