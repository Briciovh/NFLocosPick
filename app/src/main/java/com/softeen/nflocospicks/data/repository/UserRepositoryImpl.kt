package com.softeen.nflocospicks.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.softeen.nflocospicks.data.local.EmailLinkPrefs
import com.softeen.nflocospicks.data.remote.firebase.FirebaseAuthDataSource
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val emailLinkPrefs: EmailLinkPrefs
) : UserRepository {

    private val _currentUserFlow = MutableStateFlow(firebaseAuth.currentUser?.toBasicUser())
    override val currentUserFlow: StateFlow<User?> = _currentUserFlow.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUserFlow.value = auth.currentUser?.toBasicUser()
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): Result<SignInResult> =
        authCatching(AuthError.GOOGLE_SIGN_IN_FAILED) {
            val fbUser = authDataSource.signIn(activityContext)
            upsertAndResolveRole(fbUser)
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<SignInResult> =
        authCatching(AuthError.SIGN_IN_FAILED) {
            val fbUser = firebaseAuth.signInWithEmailAndPassword(email, password).await().user
                ?: throw AuthException(AuthError.SIGN_IN_FAILED)
            upsertAndResolveRole(fbUser)
        }

    override suspend fun signUpWithEmail(email: String, password: String): Result<SignInResult> =
        authCatching(AuthError.REGISTRATION_FAILED) {
            val fbUser = firebaseAuth.createUserWithEmailAndPassword(email, password).await().user
                ?: throw AuthException(AuthError.REGISTRATION_FAILED)
            upsertAndResolveRole(fbUser)
        }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun sendSignInLinkToEmail(email: String): Result<Unit> =
        authCatching(AuthError.SEND_LINK_FAILED) {
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl(EMAIL_LINK_CONTINUE_URL)
                .setHandleCodeInApp(true)
                .setAndroidPackageName(ANDROID_PACKAGE_NAME, true, null)
                .build()

            firebaseAuth.sendSignInLinkToEmail(email, actionCodeSettings).await()
            emailLinkPrefs.savePendingEmail(email)
        }

    override fun isSignInWithEmailLink(link: String): Boolean =
        firebaseAuth.isSignInWithEmailLink(link)

    override suspend fun signInWithEmailLink(email: String, link: String): Result<SignInResult> =
        authCatching(AuthError.EMAIL_LINK_SIGN_IN_FAILED) {
            val fbUser = firebaseAuth.signInWithEmailLink(email, link).await().user
                ?: throw AuthException(AuthError.EMAIL_LINK_SIGN_IN_FAILED)
            emailLinkPrefs.clearPendingEmail()
            upsertAndResolveRole(fbUser)
        }

    override fun getPendingSignInEmail(): String? = emailLinkPrefs.readPendingEmail()

    override fun verifyPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent> =
        callbackFlow {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification (instant SMS retrieval, or an already-trusted device).
                    // Complete sign-in here so callers only ever see AutoVerified or CodeSent,
                    // never a raw credential to sign in with themselves.
                    launch {
                        authCatching(AuthError.PHONE_SIGN_IN_FAILED) {
                            val fbUser = firebaseAuth.signInWithCredential(credential).await().user
                                ?: throw AuthException(AuthError.PHONE_SIGN_IN_FAILED)
                            upsertAndResolveRole(fbUser)
                        }.onSuccess { trySend(PhoneVerificationEvent.AutoVerified(it)) }
                            .onFailure {
                                trySend(PhoneVerificationEvent.Failed((it as AuthException).error))
                            }
                        close()
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    trySend(
                        PhoneVerificationEvent.Failed(
                            e.toAuthException(AuthError.PHONE_VERIFICATION_FAILED).error
                        )
                    )
                    close()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    // Not closing the flow: Firebase can still call onVerificationCompleted
                    // later via the SMS Retriever API while the user looks at the OTP field.
                    trySend(PhoneVerificationEvent.CodeSent(verificationId))
                }
            }

            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)

            // The SDK exposes no cancellation handle for an in-flight call; awaitClose only
            // needs to exist to satisfy callbackFlow's contract.
            awaitClose { }
        }

    override suspend fun signInWithPhoneCredential(verificationId: String, smsCode: String): Result<SignInResult> =
        authCatching(AuthError.PHONE_SIGN_IN_FAILED) {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
                val fbUser = firebaseAuth.signInWithCredential(credential).await().user
                    ?: throw AuthException(AuthError.PHONE_SIGN_IN_FAILED)
                upsertAndResolveRole(fbUser)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                // In the phone flow a bad SMS code raises the same exception type as a bad
                // password; remap it so the user isn't told "incorrect email or password".
                throw AuthException(AuthError.INVALID_VERIFICATION_CODE, e)
            }
        }

    // Synchronous in-memory restore — role defaults to REGULAR until watchCurrentUser() delivers
    // the real value from Firestore.
    override fun getCurrentUser(): User? =
        authDataSource.getCurrentFirebaseUser()?.toBasicUser()

    override suspend fun saveUserToFirestore(user: User) {
        firestore.collection("users").document(user.uid)
            .set(
                mapOf(
                    "displayName" to user.displayName,
                    "email"       to user.email,
                    "photoUrl"    to user.photoUrl,
                    "phoneNumber" to user.phoneNumber
                ),
                SetOptions.merge()
            ).await()
    }

    override fun watchCurrentUser(uid: String): Flow<User> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                trySend(snap.toUser(uid))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateUserRole(uid: String, role: UserRole) {
        firestore.collection("users").document(uid)
            .update("role", role.name)
            .await()
    }

    override fun getAllUsers(): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("users")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.map { it.toUser(it.id) })
            }
        awaitClose { listener.remove() }
    }

    /** Merges profile fields into `users/{uid}` (never touching an existing role field),
     *  determines [SignInResult.isNewUser] from the absence of a role field, and assigns
     *  REGULAR on first login. Shared by every sign-in method so isNewUser detection and
     *  role assignment stay consistent regardless of provider. */
    private suspend fun upsertAndResolveRole(fbUser: FirebaseUser): SignInResult {
        val ref = firestore.collection("users").document(fbUser.uid)

        ref.set(
            mapOf(
                "displayName" to fbUser.displayName.orEmpty(),
                "email"       to fbUser.email.orEmpty(),
                "photoUrl"    to fbUser.photoUrl?.toString(),
                "phoneNumber" to fbUser.phoneNumber
            ),
            SetOptions.merge()
        ).await()

        val snap = ref.get().await()
        val isNewUser = !snap.contains("role")
        val role: UserRole
        if (isNewUser) {
            ref.update("role", UserRole.REGULAR.name).await()
            role = UserRole.REGULAR
        } else {
            role = snap.getString("role")
                ?.let { runCatching { UserRole.valueOf(it) }.getOrDefault(UserRole.REGULAR) }
                ?: UserRole.REGULAR
        }

        return SignInResult(
            user = User(
                uid         = fbUser.uid,
                displayName = fbUser.displayName.orEmpty(),
                email       = fbUser.email.orEmpty(),
                photoUrl    = fbUser.photoUrl?.toString(),
                role        = role,
                phoneNumber = fbUser.phoneNumber
            ),
            isNewUser = isNewUser
        )
    }

    /**
     * Like [runCatching], but every failure crossing this boundary is an [AuthException]:
     * known Firebase/CredentialManager exception types map to their specific [AuthError],
     * anything else gets [fallback]. Cancellation is rethrown so coroutine semantics stay intact.
     */
    private inline fun <T> authCatching(fallback: AuthError, block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e.toAuthException(fallback))
        }

    private fun Throwable.toAuthException(fallback: AuthError): AuthException = when (this) {
        is AuthException -> this
        // Before FirebaseAuthInvalidCredentialsException: WeakPassword is its subclass.
        is FirebaseAuthWeakPasswordException -> AuthException(AuthError.WEAK_PASSWORD, this)
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> AuthException(AuthError.INVALID_CREDENTIALS, this)
        is FirebaseAuthUserCollisionException -> AuthException(AuthError.EMAIL_ALREADY_IN_USE, this)
        is FirebaseNetworkException -> AuthException(AuthError.NETWORK, this)
        is NoCredentialException -> AuthException(AuthError.NO_GOOGLE_ACCOUNT, this)
        else -> AuthException(fallback, this)
    }

    private fun FirebaseUser.toBasicUser() = User(
        uid = uid,
        displayName = displayName.orEmpty(),
        email = email.orEmpty(),
        photoUrl = photoUrl?.toString(),
        phoneNumber = phoneNumber
    )

    private fun DocumentSnapshot.toUser(uid: String): User = User(
        uid         = uid,
        displayName = getString("displayName") ?: "",
        email       = getString("email") ?: "",
        photoUrl    = getString("photoUrl"),
        phoneNumber = getString("phoneNumber"),
        role        = getString("role")
            ?.let { runCatching { UserRole.valueOf(it) }.getOrDefault(UserRole.REGULAR) }
            ?: UserRole.REGULAR
    )

    private companion object {
        // Keep in sync with the intent-filter android:host in AndroidManifest.xml.
        // TODO confirm against the live Firebase Console (Authentication > Settings >
        // Authorized domains) before shipping email-link sign-in — this is the
        // default-pattern guess based on google-services.json's project_id, not yet verified.
        const val EMAIL_LINK_CONTINUE_URL = "https://nflocospicks.firebaseapp.com"
        const val ANDROID_PACKAGE_NAME = "com.softeen.nflocospicks"
    }
}
