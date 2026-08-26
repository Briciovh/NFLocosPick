package com.softeen.nflocospicks.presentation.usermanagement

import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserManagementViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val userRepository = mockk<UserRepository>()
    private val logger = mockk<AppLogger>(relaxed = true)

    @Before
    fun setUp() {
        every { userRepository.getAllUsers() } returns flowOf(emptyList())
    }

    private fun viewModel() = UserManagementViewModel(userRepository, logger)

    @Test
    fun `setRole updates user role and logs analytics event`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.updateUserRole("uid1", UserRole.INSIDER) } returns Unit
        val vm = viewModel()

        vm.setRole("uid1", UserRole.INSIDER)

        verify { logger.logEvent(match { it.name == "user_role_changed" && it.params["target_user_id"] == "uid1" && it.params["new_role"] == "INSIDER" }) }
    }
}
