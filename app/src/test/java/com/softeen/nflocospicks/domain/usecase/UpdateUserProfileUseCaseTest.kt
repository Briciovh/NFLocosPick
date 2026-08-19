package com.softeen.nflocospicks.domain.usecase

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class CapturingUserRepository : UserRepository {
    var capturedUid: String? = null
    var capturedUsername: String? = null
    var capturedDisplayName: String? = null

    override val currentUserFlow: StateFlow<User?> = MutableStateFlow(null)

    override suspend fun updateProfile(
        uid: String,
        username: String,
        displayName: String?,
        photoUrl: String?
    ): Result<Unit> {
        capturedUid = uid
        capturedUsername = username
        capturedDisplayName = displayName
        return Result.success(Unit)
    }

    override fun isUsernameAvailable(username: String, uid: String): Flow<Boolean> = throw NotImplementedError()
    override suspend fun uploadProfilePhoto(uid: String, uri: Uri): Result<String> = throw NotImplementedError()
    override suspend fun linkEmailCredential(email: String, link: String): Result<Unit> = throw NotImplementedError()
    override fun linkPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent> = throw NotImplementedError()
    override suspend fun linkPhoneCredential(verificationId: String, smsCode: String): Result<Unit> = throw NotImplementedError()
    override fun hasPasswordProvider(): Boolean = throw NotImplementedError()
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = throw NotImplementedError()
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = throw NotImplementedError()
    override suspend fun signInWithGoogle(activityContext: Context): Result<SignInResult> = throw NotImplementedError()
    override suspend fun signInWithEmail(email: String, password: String): Result<SignInResult> = throw NotImplementedError()
    override suspend fun signUpWithEmail(email: String, password: String): Result<SignInResult> = throw NotImplementedError()
    override suspend fun signOut() = throw NotImplementedError()
    override suspend fun sendSignInLinkToEmail(email: String): Result<Unit> = throw NotImplementedError()
    override fun isSignInWithEmailLink(link: String): Boolean = throw NotImplementedError()
    override suspend fun signInWithEmailLink(email: String, link: String): Result<SignInResult> = throw NotImplementedError()
    override fun getPendingSignInEmail(): String? = throw NotImplementedError()
    override fun verifyPhoneNumber(activity: Activity, phoneNumber: String): Flow<PhoneVerificationEvent> = throw NotImplementedError()
    override suspend fun signInWithPhoneCredential(verificationId: String, smsCode: String): Result<SignInResult> = throw NotImplementedError()
    override fun getCurrentUser(): User? = throw NotImplementedError()
    override fun watchCurrentUser(uid: String): Flow<User> = throw NotImplementedError()
    override suspend fun updateUserRole(uid: String, role: UserRole) = throw NotImplementedError()
    override fun getAllUsers(): Flow<List<User>> = throw NotImplementedError()
    override suspend fun deleteAccount(): Result<Unit> = throw NotImplementedError()
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class UpdateUserProfileUseCaseTest {

    @Test
    fun `username is trimmed but casing is preserved before reaching the repository`() = runBlocking {
        val repo = CapturingUserRepository()
        val useCase = UpdateUserProfileUseCase(repo)

        useCase(uid = "u1", username = "  TheBigDreamer  ")

        assertEquals("TheBigDreamer", repo.capturedUsername)
    }

    @Test
    fun `username without extra spaces or casing passes through unchanged`() = runBlocking {
        val repo = CapturingUserRepository()
        val useCase = UpdateUserProfileUseCase(repo)

        useCase(uid = "u1", username = "bricio")

        assertEquals("bricio", repo.capturedUsername)
    }

    @Test
    fun `display name is trimmed when provided`() = runBlocking {
        val repo = CapturingUserRepository()
        val useCase = UpdateUserProfileUseCase(repo)

        useCase(uid = "u1", username = "bricio", displayName = "  Bricio Velázquez  ")

        assertEquals("Bricio Velázquez", repo.capturedDisplayName)
    }

    @Test
    fun `uid passes through unchanged`() = runBlocking {
        val repo = CapturingUserRepository()
        val useCase = UpdateUserProfileUseCase(repo)

        useCase(uid = "user_42", username = "bricio")

        assertEquals("user_42", repo.capturedUid)
    }
}
