package com.softeen.nflocospicks.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.softeen.nflocospicks.data.remote.firebase.FirebaseAuthDataSource
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val fbUser = authDataSource.signIn(activityContext)
        val ref = firestore.collection("users").document(fbUser.uid)

        // Merge profile fields only — never touch an existing role field.
        ref.set(
            mapOf(
                "displayName" to fbUser.displayName.orEmpty(),
                "email"       to fbUser.email.orEmpty(),
                "photoUrl"    to fbUser.photoUrl?.toString()
            ),
            SetOptions.merge()
        ).await()

        // Read the doc to determine role; set REGULAR on first login (no role field yet).
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
                role        = role
            ),
            isNewUser = isNewUser
        )
    }

    override suspend fun signOut() = authDataSource.signOut()

    // Synchronous in-memory restore — role defaults to REGULAR until watchCurrentUser() delivers
    // the real value from Firestore.
    override fun getCurrentUser(): User? =
        authDataSource.getCurrentFirebaseUser()?.let { fbUser ->
            User(
                uid         = fbUser.uid,
                displayName = fbUser.displayName.orEmpty(),
                email       = fbUser.email.orEmpty(),
                photoUrl    = fbUser.photoUrl?.toString()
            )
        }

    override suspend fun saveUserToFirestore(user: User) {
        firestore.collection("users").document(user.uid)
            .set(
                mapOf(
                    "displayName" to user.displayName,
                    "email"       to user.email,
                    "photoUrl"    to user.photoUrl
                ),
                SetOptions.merge()
            ).await()
    }

    override fun watchCurrentUser(uid: String): Flow<User> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                val user = snap.toUser(uid) ?: return@addSnapshotListener
                trySend(user)
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
                trySend(snap.documents.mapNotNull { it.toUser(it.id) })
            }
        awaitClose { listener.remove() }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUser(uid: String): User? {
    val displayName = getString("displayName") ?: return null
    val email       = getString("email")       ?: return null
    return User(
        uid         = uid,
        displayName = displayName,
        email       = email,
        photoUrl    = getString("photoUrl"),
        role        = getString("role")
            ?.let { runCatching { UserRole.valueOf(it) }.getOrDefault(UserRole.REGULAR) }
            ?: UserRole.REGULAR
    )
}
