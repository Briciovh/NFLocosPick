package com.softeen.nflocospicks.data.remote.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.softeen.nflocospicks.R
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Wraps the CredentialManager → Firebase Auth sign-in flow.
 *
 * [signIn] requires an Activity context because CredentialManager needs it to display
 * the Google account picker bottom sheet.
 */
class FirebaseAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth
) {
    /**
     * Launches the Google Sign-In picker via CredentialManager, exchanges the ID token
     * for a Firebase credential, and returns the authenticated [FirebaseUser].
     *
     * @throws androidx.credentials.exceptions.GetCredentialException on picker failure/cancellation.
     */
    suspend fun signIn(activityContext: Context): FirebaseUser {
        val credentialManager = CredentialManager.create(activityContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false) // allow any Google account, not just previously-used ones
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

        return auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase signInWithCredential returned a null user")
    }

    fun signOut() = auth.signOut()

    fun getCurrentFirebaseUser(): FirebaseUser? = auth.currentUser
}
