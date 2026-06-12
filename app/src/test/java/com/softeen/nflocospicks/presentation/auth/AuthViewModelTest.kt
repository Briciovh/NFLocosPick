package com.softeen.nflocospicks.presentation.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val logger = mockk<AppLogger>(relaxed = true)

    @Test
    fun `init sets uiState to Authenticated when user exists`() = runTest {
        // Given
        val user = User("uid", "name", "email", null, role = UserRole.REGULAR)
        every { userRepository.getCurrentUser() } returns user
        every { userRepository.watchCurrentUser("uid") } returns flowOf(user)

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
}
