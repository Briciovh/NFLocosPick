package com.softeen.nflocospicks.data.repository

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.softeen.nflocospicks.data.local.EmailLinkPrefs
import com.softeen.nflocospicks.data.remote.firebase.FirebaseAuthDataSource
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.model.GlobalGroupConstants
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
import timber.log.Timber

class UserRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
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
        phoneAuthFlow(activity, phoneNumber, AuthError.PHONE_SIGN_IN_FAILED) { credential ->
            firebaseAuth.signInWithCredential(credential).await().user
                ?: throw AuthException(AuthError.PHONE_SIGN_IN_FAILED)
        }

    override suspend fun signInWithPhoneCredential(verificationId: String, smsCode: String): Result<SignInResult> =
        consumePhoneCredential(verificationId, smsCode, AuthError.PHONE_SIGN_IN_FAILED) { credential ->
            firebaseAuth.signInWithCredential(credential).await().user
                ?: throw AuthException(AuthError.PHONE_SIGN_IN_FAILED)
        }

    override fun linkPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent> =
        phoneAuthFlow(activity, phoneNumber, AuthError.LINK_PHONE_FAILED) { credential ->
            val current = firebaseAuth.currentUser ?: throw AuthException(AuthError.LINK_PHONE_FAILED)
            current.linkWithCredential(credential).await().user
                ?: throw AuthException(AuthError.LINK_PHONE_FAILED)
        }

    override suspend fun linkPhoneCredential(verificationId: String, smsCode: String): Result<Unit> =
        consumePhoneCredential(verificationId, smsCode, AuthError.LINK_PHONE_FAILED) { credential ->
            val current = firebaseAuth.currentUser ?: throw AuthException(AuthError.LINK_PHONE_FAILED)
            current.linkWithCredential(credential).await().user
                ?: throw AuthException(AuthError.LINK_PHONE_FAILED)
        }.map { }

    override suspend fun linkEmailCredential(email: String, link: String): Result<Unit> =
        authCatching(AuthError.LINK_EMAIL_FAILED) {
            val current = firebaseAuth.currentUser ?: throw AuthException(AuthError.LINK_EMAIL_FAILED)
            val credential = EmailAuthProvider.getCredentialWithLink(email, link)
            val fbUser = current.linkWithCredential(credential).await().user
                ?: throw AuthException(AuthError.LINK_EMAIL_FAILED)
            emailLinkPrefs.clearPendingEmail()
            upsertAndResolveRole(fbUser)
        }.map { }

    /**
     * Shared machinery behind [verifyPhoneNumber]/[linkPhoneNumber]: runs the
     * `PhoneAuthProvider` verification callbacks and emits [PhoneVerificationEvent]s, but
     * defers to [consumeCredential] for whether the resulting credential signs in or links.
     */
    private fun phoneAuthFlow(
        activity: Activity,
        phoneNumber: String,
        fallbackError: AuthError,
        consumeCredential: suspend (PhoneAuthCredential) -> FirebaseUser
    ): Flow<PhoneVerificationEvent> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (instant SMS retrieval, or an already-trusted device).
                // Complete sign-in/linking here so callers only ever see AutoVerified or
                // CodeSent, never a raw credential to consume themselves.
                launch {
                    authCatching(fallbackError) {
                        upsertAndResolveRole(consumeCredential(credential))
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

    /** Shared machinery behind [signInWithPhoneCredential]/[linkPhoneCredential]. */
    private suspend fun consumePhoneCredential(
        verificationId: String,
        smsCode: String,
        fallbackError: AuthError,
        consumeCredential: suspend (PhoneAuthCredential) -> FirebaseUser
    ): Result<SignInResult> =
        authCatching(fallbackError) {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
                upsertAndResolveRole(consumeCredential(credential))
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                // A bad SMS code raises the same exception type as a bad password; remap it
                // so the user isn't told "incorrect email or password".
                throw AuthException(AuthError.INVALID_VERIFICATION_CODE, e)
            }
        }

    // Synchronous in-memory restore — role defaults to REGULAR until watchCurrentUser() delivers
    // the real value from Firestore.
    override fun getCurrentUser(): User? =
        authDataSource.getCurrentFirebaseUser()?.toBasicUser()

    override fun isUsernameAvailable(username: String): Flow<Boolean> = callbackFlow {
        val normalized = username.trim().lowercase()
        val listener = firestore.collection("usernames").document(normalized)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.exists() != true)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun updateProfile(
        uid: String,
        username: String,
        displayName: String?,
        photoUrl: String?
    ): Result<Unit> {
        // The reservation doc ID (and therefore uniqueness) stays case-insensitive, but the
        // casing/symbols the user actually typed are what gets persisted on users/{uid}.
        val displayUsername = username.trim()
        val normalizedUsername = displayUsername.lowercase()
        Timber.i("updateProfile: uid=$uid newUsername=$displayUsername")
        return authCatching(AuthError.PROFILE_UPDATE_FAILED) {
            val userRef = firestore.collection("users").document(uid)
            val usernamesRef = firestore.collection("usernames")

            firestore.runTransaction { transaction ->
                val userSnap = transaction.get(userRef)
                val oldUsername = userSnap.getString("username")
                val oldNormalizedUsername = oldUsername?.lowercase()

                if (normalizedUsername != oldNormalizedUsername) {
                    val newUsernameRef = usernamesRef.document(normalizedUsername)
                    val newUsernameSnap = transaction.get(newUsernameRef)
                    if (newUsernameSnap.exists()) {
                        throw AuthException(AuthError.USERNAME_TAKEN)
                    }
                    transaction.set(newUsernameRef, mapOf("userId" to uid))
                    if (oldNormalizedUsername != null) {
                        transaction.delete(usernamesRef.document(oldNormalizedUsername))
                    }
                }

                val profileUpdate = buildMap {
                    put("username", displayUsername)
                    if (displayName != null) put("displayName", displayName)
                    if (photoUrl != null) put("photoUrl", photoUrl)
                }
                transaction.set(userRef, profileUpdate, SetOptions.merge())
            }.await()
            Unit
        }.also { result ->
            // Diagnostic breadcrumb for the profile-completion-screen-reappears bug report:
            // this is a real transaction (not an optimistic local write), so a logged success
            // here means the username genuinely committed server-side at this uid/value.
            Timber.i(
                "updateProfile result: uid=$uid username=$displayUsername " +
                    "success=${result.isSuccess} error=${result.exceptionOrNull()}"
            )
        }
    }

    override suspend fun uploadProfilePhoto(uid: String, uri: Uri): Result<String> =
        authCatching(AuthError.PROFILE_UPDATE_FAILED) {
            val photoRef = storage.reference.child("profile_photos/$uid")
            photoRef.putFile(uri).await()
            val downloadUrl = photoRef.downloadUrl.await().toString()
            firestore.collection("users").document(uid)
                .set(mapOf("photoUrl" to downloadUrl), SetOptions.merge())
                .await()
            downloadUrl
        }

    override fun hasPasswordProvider(): Boolean =
        firebaseAuth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        authCatching(AuthError.PASSWORD_CHANGE_FAILED) {
            val user = firebaseAuth.currentUser ?: throw AuthException(AuthError.PASSWORD_CHANGE_FAILED)
            val email = user.email ?: throw AuthException(AuthError.PASSWORD_CHANGE_FAILED)
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
        }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        authCatching(AuthError.SEND_RESET_EMAIL_FAILED) {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }

    override fun watchCurrentUser(uid: String): Flow<User> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.w(error, "watchCurrentUser: uid=$uid listener error")
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                // Diagnostic breadcrumb for the profile-completion-screen-reappears bug report:
                // confirms exactly what this device read back for username on this snapshot.
                Timber.i(
                    "watchCurrentUser: uid=$uid hasUsername=${snap.contains("username")} " +
                        "isFromCache=${snap.metadata.isFromCache} " +
                        "hasPendingWrites=${snap.metadata.hasPendingWrites()}"
                )
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

    override suspend fun deleteAccount(): Result<Unit> =
        authCatching(AuthError.ACCOUNT_DELETION_FAILED) {
            functions.getHttpsCallable("deleteAccount").call().await()
            firebaseAuth.signOut()
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
                "phoneNumber" to fbUser.phoneNumber,
                // Se reescribe en CADA login (no solo el primero, a diferencia de "role") — es
                // lo que resetea el reloj de 1 año de inactividad (PR-20) y reactiva una cuenta
                // que la Cloud Function "scheduledInactivityCheck" hubiera deshabilitado.
                "lastActive"  to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        val snap = ref.get().await()
        val isNewUser = !snap.contains("role")
        val role: UserRole
        if (isNewUser) {
            ref.update("role", UserRole.REGULAR.name).await()
            role = UserRole.REGULAR
            ensureGlobalGroupMembership(fbUser.uid)
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
                phoneNumber = fbUser.phoneNumber,
                username    = snap.getString("username"),
                lastActive  = snap.getTimestamp("lastActive")?.toDate()?.time
            ),
            isNewUser = isNewUser
        )
    }

    /**
     * Auto-afilia al usuario recién registrado al grupo global "NFLocos de Corazón" (PR-17):
     * se autoagrega a `memberIds` (ya permitido por la regla `update` existente — mismo
     * mecanismo que unirse por código) y siembra su standing en 0 puntos vía la Cloud
     * Function `ensureGlobalStanding` (standings tiene `allow write: if false` para
     * clientes). Falla en silencio: es un efecto secundario del sign-in, no debe poder
     * tumbar el login si el grupo global no existe todavía o si hay un error de red.
     */
    private suspend fun ensureGlobalGroupMembership(uid: String) {
        runCatching {
            firestore.collection("groups").document(GlobalGroupConstants.GROUP_ID)
                .update("memberIds", FieldValue.arrayUnion(uid))
                .await()
            functions.getHttpsCallable("ensureGlobalStanding").call().await()
        }.onFailure { e ->
            Timber.w(e, "ensureGlobalGroupMembership: no se pudo afiliar uid=$uid al grupo global")
        }
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
        // Firebase raises the same exception type for an email collision and a phone
        // collision — disambiguate using which operation was being attempted.
        is FirebaseAuthUserCollisionException -> AuthException(
            if (fallback == AuthError.LINK_PHONE_FAILED) AuthError.PHONE_ALREADY_IN_USE
            else AuthError.EMAIL_ALREADY_IN_USE,
            this
        )
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
        username    = getString("username"),
        role        = getString("role")
            ?.let { runCatching { UserRole.valueOf(it) }.getOrDefault(UserRole.REGULAR) }
            ?: UserRole.REGULAR,
        lastActive  = getTimestamp("lastActive")?.toDate()?.time
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
