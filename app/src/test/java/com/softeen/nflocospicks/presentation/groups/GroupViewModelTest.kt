package com.softeen.nflocospicks.presentation.groups

import androidx.work.WorkManager
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val createGroupUseCase  = mockk<CreateGroupUseCase>()
    private val joinGroupUseCase    = mockk<JoinGroupUseCase>()
    private val getGroupsUseCase    = mockk<GetGroupsForUserUseCase>()
    private val scoreUseCase        = mockk<ScoreWeekPicksUseCase>()
    private val userRepo            = mockk<UserRepository>()
    private val prefsRepo           = mockk<UserPreferencesRepository>()
    private val workManager         = mockk<WorkManager>(relaxed = true)
    private val logger              = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    private val stubGroup = Group(
        id         = "g1",
        name       = "Los Locos",
        inviteCode = "ABC123",
        createdBy  = "user1",
        memberIds  = listOf("user1")
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Base setup shared by tests that need a logged-in user with no groups yet.
        every { userRepo.getCurrentUser() }   returns testUser
        every { getGroupsUseCase(any()) }     returns flowOf(emptyList())
        every { prefsRepo.preferencesFlow }   returns flowOf(UserPreferences())
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private fun viewModel() = GroupViewModel(
        createGroupUseCase      = createGroupUseCase,
        joinGroupUseCase        = joinGroupUseCase,
        getGroupsForUserUseCase = getGroupsUseCase,
        scoreWeekPicksUseCase   = scoreUseCase,
        userRepository          = userRepo,
        preferencesRepository   = prefsRepo,
        workManager             = workManager,
        logger                  = logger
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `createGroup transitions actionState from Idle to Success with the created group`() = runTest(coroutineRule.dispatcher) {
        coEvery { createGroupUseCase(any(), any()) } returns stubGroup

        val vm = viewModel()
        assertEquals(GroupActionUiState.Idle, vm.actionState.value)

        vm.createGroup("Los Locos")

        val state = vm.actionState.value as GroupActionUiState.Success
        assertEquals(stubGroup, state.group)
    }

    @Test
    fun `joinGroup transitions actionState from Idle to Success with the joined group`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinGroupUseCase(any(), any()) } returns stubGroup

        val vm = viewModel()
        vm.joinGroup("ABC123")

        val state = vm.actionState.value as GroupActionUiState.Success
        assertEquals(stubGroup, state.group)
    }

    @Test
    fun `joinGroup with invalid invite code sets Error with the specific Spanish message`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinGroupUseCase(any(), any()) } throws NoSuchElementException()

        val vm = viewModel()
        vm.joinGroup("BAD-CODE")

        val state = vm.actionState.value as GroupActionUiState.Error
        assertEquals("Código de invitación inválido", state.message)
    }

    @Test
    fun `null current user sends NavigateToLogin effect during init`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns null

        val vm = viewModel()

        val effect = vm.effects.receive()
        assertEquals(GroupUiEffect.NavigateToLogin, effect)
    }

    @Test
    fun `onGroupClicked sends NavigateToGroupSession effect with the correct groupId`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()

        vm.onGroupClicked("group-xyz")

        val effect = vm.effects.receive()
        assertEquals(GroupUiEffect.NavigateToGroupSession("group-xyz"), effect)
    }
}
