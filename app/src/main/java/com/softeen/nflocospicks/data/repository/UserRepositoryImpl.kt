package com.softeen.nflocospicks.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.data.remote.firebase.FirebaseAuthDataSource
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.repository.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun signInWithGoogle(activityContext: Context): User {
        val fbUser = authDataSource.signIn(activityContext)
        val user = User(
            uid          = fbUser.uid,
            displayName  = fbUser.displayName.orEmpty(),
            email        = fbUser.email.orEmpty(),
            photoUrl     = fbUser.photoUrl?.toString()
        )
        saveUserToFirestore(user)
        return user
    }

    override suspend fun signOut() = authDataSource.signOut()

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
        val doc = mapOf(
            "displayName" to user.displayName,
            "email"       to user.email,
            "photoUrl"    to user.photoUrl
        )
        firestore.collection("users").document(user.uid).set(doc).await()
    }
}
