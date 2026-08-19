package com.softeen.nflocospicks.domain.repository

import android.app.Activity
import android.content.Context
import android.net.Uri
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
     * Completes an email-link that was opened while a session was already active: links the
     * email as an additional credential on the signed-in Firebase Auth account (via
     * `linkWithCredential`) instead of starting a fresh sign-in. Fails with
     * [com.softeen.nflocospicks.domain.model.AuthError.EMAIL_ALREADY_IN_USE] if the email is
     * already claimed by a different account. Sending the link itself reuses
     * [sendSignInLinkToEmail] — no separate "send" method is needed.
     */
    suspend fun linkEmailCredential(email: String, link: String): Result<Unit>

    /**
     * Like [verifyPhoneNumber], but the resulting credential is linked onto the currently
     * signed-in account (via `linkWithCredential`) instead of being used to sign in.
     */
    fun linkPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent>

    /**
     * Like [signInWithPhoneCredential], but links the credential onto the currently signed-in
     * account. Fails with [com.softeen.nflocospicks.domain.model.AuthError.PHONE_ALREADY_IN_USE]
     * if the phone number is already claimed by a different account.
     */
    suspend fun linkPhoneCredential(verificationId: String, smsCode: String): Result<Unit>

    /**
     * Returns the currently signed-in user from the in-memory Firebase Auth state,
     * or null if no session exists. Synchronous — safe to call from an init block.
     * Role defaults to REGULAR; use [watchCurrentUser] to receive the real Firestore role.
     */
    fun getCurrentUser(): User?

    /**
     * Live availability of a username: emits `true` when `usernames/{username}` (lowercased)
     * has no reservation document, or when the existing reservation already belongs to [uid]
     * (so a user resubmitting their own current/former username is never told it's taken).
     * Emits `false` when it's claimed by a different user.
     */
    fun isUsernameAvailable(username: String, uid: String): Flow<Boolean>

    /**
     * Claims [username] (case-insensitively, via the `usernames/{username}` reservation
     * collection) and merges the given profile fields into `users/{uid}`, all inside a single
     * Firestore transaction. Fails with [com.softeen.nflocospicks.domain.model.AuthException]
     * carrying [com.softeen.nflocospicks.domain.model.AuthError.USERNAME_TAKEN] if the username
     * is already claimed by a different user.
     */
    suspend fun updateProfile(
        uid: String,
        username: String,
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<Unit>

    /**
     * Uploads [uri] to Storage at `profile_photos/{uid}` (overwriting any previous photo) and
     * merges the resulting download URL into `users/{uid}.photoUrl`. Returns the download URL.
     */
    suspend fun uploadProfilePhoto(uid: String, uri: Uri): Result<String>

    /**
     * True when the signed-in user has an email/password credential linked to their Firebase
     * Auth account — false for Google-only or phone-only accounts, which have no password to
     * change. Used to hide the change-password section entirely for those accounts.
     */
    fun hasPasswordProvider(): Boolean

    /**
     * Re-authenticates with [currentPassword] before setting [newPassword], per Firebase Auth's
     * "recent login required" requirement for sensitive account changes. Fails with
     * [com.softeen.nflocospicks.domain.model.AuthError.INVALID_CREDENTIALS] if [currentPassword]
     * is wrong.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** Sends a Firebase Auth password-reset email to [email]. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Real-time stream of the user document. Emits whenever role or profile changes in Firestore. */
    fun watchCurrentUser(uid: String): Flow<User>

    /** Update the role field for any user. Only callable by INSIDER users (enforced in UI layer). */
    suspend fun updateUserRole(uid: String, role: UserRole)

    /** Real-time stream of all user documents — used by UserManagementScreen. */
    fun getAllUsers(): Flow<List<User>>

    /**
     * Deletes the signed-in user's account (Google Play account-deletion requirement).
     * Invokes the "deleteAccount" Cloud Function, which anonymizes the user's profile and
     * board messages, removes them from every group's `memberIds`, and deletes their
     * username reservation, profile photo, and Firebase Auth record. Past picks/results/
     * standings are left in place so other members' group history stays intact. Ends the
     * local session on success, same as [signOut].
     */
    suspend fun deleteAccount(): Result<Unit>
}
