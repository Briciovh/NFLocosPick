package com.softeen.nflocospicks.presentation.account

import com.softeen.nflocospicks.analytics.AppLogger
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
    fun `changePassword success logs analytics event`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.changePassword(any(), any()) } returns Result.success(Unit)
        val vm = viewModel()

        vm.changePassword("old", "new")

        verify { logger.logEvent(match { it.name == "password_changed" }) }
    }
}
