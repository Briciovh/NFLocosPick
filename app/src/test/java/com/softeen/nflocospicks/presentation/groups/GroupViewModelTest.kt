package com.softeen.nflocospicks.presentation.groups

import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.JoinGroupResult
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SetGroupIconUseCase
import com.softeen.nflocospicks.domain.usecase.UploadGroupPhotoUseCase
import com.softeen.nflocospicks.domain.usecase.WatchBoardMessagesUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val uploadGroupPhotoUseCase = mockk<UploadGroupPhotoUseCase>()
    private val setGroupIconUseCase = mockk<SetGroupIconUseCase>()
    private val watchBoardMessagesUseCase = mockk<WatchBoardMessagesUseCase>()
    private val userRepo            = mockk<UserRepository>()
    private val prefsRepo           = mockk<UserPreferencesRepository>()
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
        every { watchBoardMessagesUseCase(any()) } returns flowOf(emptyList())
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private fun viewModel() = GroupViewModel(
        createGroupUseCase      = createGroupUseCase,
        joinGroupUseCase        = joinGroupUseCase,
        getGroupsForUserUseCase = getGroupsUseCase,
        scoreWeekPicksUseCase   = scoreUseCase,
        uploadGroupPhotoUseCase = uploadGroupPhotoUseCase,
        setGroupIconUseCase     = setGroupIconUseCase,
        watchBoardMessagesUseCase = watchBoardMessagesUseCase,
        userRepository          = userRepo,
        preferencesRepository   = prefsRepo,
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
        verify { logger.logEvent(match { it.name == "group_created" && it.params["group_name"] == "Los Locos" }) }
    }

    @Test
    fun `joinGroup transitions actionState from Idle to Success with the joined group`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinGroupUseCase(any(), any()) } returns JoinGroupResult(stubGroup, alreadyMember = false)

        val vm = viewModel()
        vm.joinGroup("ABC123")

        val state = vm.actionState.value as GroupActionUiState.Success
        assertEquals(stubGroup, state.group)
        assertEquals(false, state.alreadyMember)
        verify {
            logger.logEvent(match {
                it.name == "group_joined" && it.params["group_name"] == "Los Locos" && it.params["source"] == "manual_code"
            })
        }
        val effect = vm.effects.receive()
        assertEquals(GroupUiEffect.GroupJoined("Los Locos", alreadyMember = false), effect)
    }

    @Test
    fun `joinGroup logs the given source instead of the manual_code default`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinGroupUseCase(any(), any()) } returns JoinGroupResult(stubGroup, alreadyMember = false)

        val vm = viewModel()
        vm.joinGroup("ABC123", source = "invite_link")

        verify { logger.logEvent(match { it.name == "group_joined" && it.params["source"] == "invite_link" }) }
    }

    @Test
    fun `joinGroup for an already-member result sets alreadyMember true, sends the effect, and does not log analytics`() = runTest(coroutineRule.dispatcher) {
        coEvery { joinGroupUseCase(any(), any()) } returns JoinGroupResult(stubGroup, alreadyMember = true)

        val vm = viewModel()
        vm.joinGroup("ABC123")

        val state = vm.actionState.value as GroupActionUiState.Success
        assertEquals(true, state.alreadyMember)
        val effect = vm.effects.receive()
        assertEquals(GroupUiEffect.GroupJoined("Los Locos", alreadyMember = true), effect)
        verify(exactly = 0) { logger.logEvent(match { it.name == "group_joined" }) }
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
    fun `onGroupClicked sends NavigateToGroupSession effect and logs event`() = runTest(coroutineRule.dispatcher) {
        val vm = viewModel()

        vm.onGroupClicked("group-xyz", source = "group_list")

        val effect = vm.effects.receive()
        assertEquals(GroupUiEffect.NavigateToGroupSession("group-xyz"), effect)
        io.mockk.verify { logger.logEvent(match { event ->
            event.name == "group_opened" &&
            event.params["group_id"] == "group-xyz" &&
            event.params["source"] == "group_list"
        }) }
    }

    @Test
    fun `onScoreClicked logs scoring_completed with source group_card`() = runTest(coroutineRule.dispatcher) {
        coEvery { scoreUseCase("g1") } returns 5
        val vm = viewModel()

        vm.onScoreClicked("g1")

        verify { logger.logEvent(match { event ->
            event.name == "scoring_completed" &&
            event.params["source"] == "group_card" &&
            event.params["scored_count"] == 5
        }) }
    }

    @Test
    fun `uploadGroupPhoto logs group_photo_uploaded on success`() = runTest(coroutineRule.dispatcher) {
        coEvery { uploadGroupPhotoUseCase("g1", any()) } returns Result.success("https://example.com/photo.jpg")
        val vm = viewModel()

        vm.uploadGroupPhoto(stubGroup, requesterUserId = "user1", uri = mockk())

        verify { logger.logEvent(match { it.name == "group_photo_uploaded" && it.params["group_id"] == "g1" }) }
    }

    @Test
    fun `setGroupIcon logs group_icon_set on success`() = runTest(coroutineRule.dispatcher) {
        coEvery { setGroupIconUseCase("g1", "icon_1") } returns Result.success(Unit)
        val vm = viewModel()

        vm.setGroupIcon(stubGroup, requesterUserId = "user1", iconId = "icon_1")

        verify { logger.logEvent(match { it.name == "group_icon_set" && it.params["icon_id"] == "icon_1" }) }
    }
}
