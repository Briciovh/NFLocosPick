package com.softeen.nflocospicks.domain.repository

import android.app.Activity
import android.content.Context
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication + user-profile contract.
 *
 * Note: [android.content.Context]/[android.app.Activity] appear here solely because
 * CredentialManager and PhoneAuthProvider require an Activity context at the call site.
 * No Firebase or Retrofit type crosses this boundary.
 */
interface UserRepository {
    /** Reactive Firebase Auth session state — reflects sign-in/out from ANY source,
     *  including the email-link deep link handled in MainActivity.
     *  Named distinctly from [getCurrentUser] (not "currentUser") because a Kotlin
     *  property of that name would compile to a `getCurrentUser()` JVM getter, colliding
     *  by name with the existing synchronous accessor below and confusing MockK's
     *  reflection-based method matching in unrelated tests. */
    val currentUserFlow: StateFlow<User?>

    /** Sign in with Google via CredentialManager and persist the user profile. */
    suspend fun signInWithGoogle(activityContext: Context): Result<SignInResult>

    /** Sign in with an existing email/password account. */
    suspend fun signInWithEmail(email: String, password: String): Result<SignInResult>

    /** Create a new email/password account. */
    suspend fun signUpWithEmail(email: String, password: String): Result<SignInResult>

    /** Clear the Firebase Auth session. */
    suspend fun signOut()

    // Email link (passwordless) sign-in
    suspend fun sendSignInLinkToEmail(email: String): Result<Unit>
    fun isSignInWithEmailLink(link: String): Boolean
    suspend fun signInWithEmailLink(email: String, link: String): Result<SignInResult>
    fun getPendingSignInEmail(): String?

    // Phone number sign-in
    fun verifyPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent>
    suspend fun signInWithPhoneCredential(verificationId: String, smsCode: String): Result<SignInResult>

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
