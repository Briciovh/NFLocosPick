package com.softeen.nflocospicks.presentation.usermanagement

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

        coVerify(exactly = 1) { userRepository.updateUserRole("uid1", UserRole.INSIDER) }
        verify { logger.logEvent(match { it.name == "user_role_changed" && it.params["target_user_id"] == "uid1" && it.params["new_role"] == "INSIDER" }) }
    }

    @Test
    fun `setRole passes REGULAR through to the repository and analytics`() = runTest(coroutineRule.dispatcher) {
        coEvery { userRepository.updateUserRole("uid2", UserRole.REGULAR) } returns Unit
        val vm = viewModel()

        vm.setRole("uid2", UserRole.REGULAR)

        coVerify(exactly = 1) { userRepository.updateUserRole("uid2", UserRole.REGULAR) }
        verify { logger.logEvent(match { it.params["new_role"] == "REGULAR" }) }
    }

    @Test
    fun `users exposes the repository stream`() = runTest(coroutineRule.dispatcher) {
        val roster = listOf(
            User("u1", "Alex", "a@t.com", null),
            User("u2", "Sam", "s@t.com", null)
        )
        every { userRepository.getAllUsers() } returns flowOf(roster)
        val vm = viewModel()

        val collector = launch { vm.users.collect {} } // trigger WhileSubscribed sharing
        assertThat(vm.users.value).isEqualTo(roster)
        collector.cancel()
    }
}
