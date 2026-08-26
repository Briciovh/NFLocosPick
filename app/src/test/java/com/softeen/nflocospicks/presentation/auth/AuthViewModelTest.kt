package com.softeen.nflocospicks.presentation.auth

import android.app.Activity
import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.model.PhoneVerificationEvent
import com.softeen.nflocospicks.domain.model.SignInResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val logger = mockk<AppLogger>(relaxed = true)

    @Before
    fun setUp() {
        // AuthViewModel's init reactively collects this flow; a relaxed mock's auto-generated
        // StateFlow throws on collect(), so every test needs a real (non-mocked) instance here.
        every { userRepository.currentUserFlow } returns MutableStateFlow(null)
    }

    @Test
    fun `init sets uiState to Authenticated when user exists`() = runTest {
        // Given
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        every { userRepository.getCurrentUser() } returns user
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        // Consistent with getCurrentUser() above — in production both read from the same
        // underlying FirebaseAuth.currentUser, so they never disagree like this on init.
        every { userRepository.currentUserFlow } returns MutableStateFlow(user)

        // When
        val viewModel = AuthViewModel(userRepository, logger)

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(AuthUiState.Authenticated::class.java)
            assertThat((state as AuthUiState.Authenticated).user).isEqualTo(user)
        }
    }

    @Test
    fun `signOut updates uiState to Idle`() = runTest {
        // Given
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        every { userRepository.getCurrentUser() } returns user
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.signOut()

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(AuthUiState.Idle)
        }
    }

    @Test
    fun `signInWithEmail success sets uiState to Authenticated`() = runTest {
        // Given
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        coEvery { userRepository.signInWithEmail("email", "pw") } returns
            Result.success(SignInResult(user, isNewUser = false))
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.signInWithEmail("email", "pw")

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(AuthUiState.Authenticated::class.java)
        }
    }

    @Test
    fun `signInWithEmail failure sets uiState to mapped Error`() = runTest {
        // Given
        coEvery { userRepository.signInWithEmail("email", "wrong") } returns
            Result.failure(AuthException(AuthError.INVALID_CREDENTIALS))
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.signInWithEmail("email", "wrong")

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state).isEqualTo(AuthUiState.Error(AuthError.INVALID_CREDENTIALS))
        }
    }

    @Test
    fun `sendSignInLink success sets uiState to LinkSent`() = runTest {
        // Given
        coEvery { userRepository.sendSignInLinkToEmail("email") } returns Result.success(Unit)
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.sendSignInLink("email")

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(AuthUiState.LinkSent("email"))
        }
    }

    @Test
    fun `startPhoneVerification CodeSent sets uiState to PhoneCodeSent`() = runTest {
        // Given
        val activity = mockk<Activity>(relaxed = true)
        every { userRepository.verifyPhoneNumber(activity, "+15551234567") } returns
            flowOf(PhoneVerificationEvent.CodeSent("verification-id"))
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.startPhoneVerification(activity, "+15551234567")

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(AuthUiState.PhoneCodeSent("+15551234567"))
        }
    }

    @Test
    fun `verifyPhoneCode with no pending verification sets Error VERIFICATION_SESSION_EXPIRED`() = runTest {
        // Given
        val viewModel = AuthViewModel(userRepository, logger)

        // When
        viewModel.verifyPhoneCode("123456")

        // Then
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(AuthUiState.Error(AuthError.VERIFICATION_SESSION_EXPIRED))
        }
    }

    // ── Analytics: User-ID + GlobalGroupAutoJoined (PR-21/22) ──────────────────

    @Test
    fun `init sets the analytics user id when a session is restored synchronously`() = runTest {
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        every { userRepository.getCurrentUser() } returns user
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        every { userRepository.currentUserFlow } returns MutableStateFlow(user)

        AuthViewModel(userRepository, logger)

        verify { logger.setUserId("uid") }
    }

    @Test
    fun `signOut clears the analytics user id`() = runTest {
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        every { userRepository.getCurrentUser() } returns user
        val viewModel = AuthViewModel(userRepository, logger)

        viewModel.signOut()

        verify { logger.setUserId(null) }
    }

    @Test
    fun `signIn with google for a new user logs SignUp and GlobalGroupAutoJoined`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        coEvery { userRepository.signInWithGoogle(context) } returns
            Result.success(SignInResult(user, isNewUser = true))
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        val viewModel = AuthViewModel(userRepository, logger)

        viewModel.signIn(context)

        verify { logger.logEvent(match { it.name == "sign_up" && it.params["method"] == "google" }) }
        verify { logger.logEvent(match { it.name == "global_group_auto_joined" }) }
    }

    @Test
    fun `signIn with google for a returning user does not log GlobalGroupAutoJoined`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        coEvery { userRepository.signInWithGoogle(context) } returns
            Result.success(SignInResult(user, isNewUser = false))
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        val viewModel = AuthViewModel(userRepository, logger)

        viewModel.signIn(context)

        verify(inverse = true) { logger.logEvent(match { it.name == "global_group_auto_joined" }) }
    }

    @Test
    fun `signUpWithEmail always logs GlobalGroupAutoJoined`() = runTest {
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        coEvery { userRepository.signUpWithEmail("email", "pw") } returns
            Result.success(SignInResult(user, isNewUser = true))
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)
        val viewModel = AuthViewModel(userRepository, logger)

        viewModel.signUpWithEmail("email", "pw")

        verify { logger.logEvent(match { it.name == "sign_up" && it.params["method"] == "email" }) }
        verify { logger.logEvent(match { it.name == "global_group_auto_joined" }) }
    }
}
