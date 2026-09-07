package com.softeen.nflocospicks.presentation.account

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.AuthException
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val userRepository = mockk<UserRepository>()
    private val logger = mockk<AppLogger>(relaxed = true)

    private fun viewModel() = ChangePasswordViewModel(userRepository, logger)

    @Test
    fun `changePassword success logs analytics event and sets Success`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.changePassword(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()

        vm.changePassword("old", "new")

        assertThat(vm.changePasswordState.value).isEqualTo(ChangePasswordState.Success)
        verify { logger.logEvent(match { it.name == "password_changed" }) }
    }

    @Test
    fun `changePassword failure preserves a typed AuthException error`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.changePassword(any(), any()) } returns
            Result.failure(AuthException(AuthError.INVALID_CREDENTIALS))
        val vm = viewModel()

        vm.changePassword("wrong", "new")

        assertThat(vm.changePasswordState.value)
            .isEqualTo(ChangePasswordState.Error(AuthError.INVALID_CREDENTIALS))
        verify(exactly = 0) { logger.logEvent(any()) }
    }

    @Test
    fun `changePassword failure with an untyped exception maps to PASSWORD_CHANGE_FAILED`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.changePassword(any(), any()) } returns
            Result.failure(RuntimeException("boom"))
        val vm = viewModel()

        vm.changePassword("old", "new")

        assertThat(vm.changePasswordState.value)
            .isEqualTo(ChangePasswordState.Error(AuthError.PASSWORD_CHANGE_FAILED))
        verify(exactly = 0) { logger.logEvent(any()) }
    }
}
