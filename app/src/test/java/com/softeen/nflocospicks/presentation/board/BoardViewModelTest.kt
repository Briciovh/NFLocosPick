package com.softeen.nflocospicks.presentation.board

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.model.GlobalGroupConstants
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.DeleteBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.SendBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.SetBoardAnnouncementUseCase
import com.softeen.nflocospicks.domain.usecase.UpdateBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.WatchBoardMessagesUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura explícita de PR-18: `isGroupAdmin` (única puerta de "quién puede marcar
 * anuncios") es `Group.createdBy == currentUserId` sin cambios de código — el grupo
 * global "NFLocos de Corazón" solo es especial porque su `createdBy` se sembró en
 * PR-16 con el uid del admin único (ver docs/plans/global-default-group.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val watchBoardMessages   = mockk<WatchBoardMessagesUseCase>()
    private val sendBoardMessage     = mockk<SendBoardMessageUseCase>()
    private val updateBoardMessage   = mockk<UpdateBoardMessageUseCase>()
    private val deleteBoardMessage   = mockk<DeleteBoardMessageUseCase>()
    private val setBoardAnnouncement = mockk<SetBoardAnnouncementUseCase>()
    private val userRepo             = mockk<UserRepository>()
    private val groupRepo            = mockk<GroupRepository>()
    private val logger               = mockk<AppLogger>(relaxed = true)

    private val adminUid  = "admin-uid"
    private val memberUid = "regular-member-uid"

    private val globalGroup = Group(
        id         = GlobalGroupConstants.GROUP_ID,
        name       = "NFLocos de Corazón",
        inviteCode = "GLOBAL",
        createdBy  = adminUid,
        memberIds  = listOf(adminUid, memberUid)
    )

    @Before
    fun setUp() {
        every { watchBoardMessages(GlobalGroupConstants.GROUP_ID) } returns flowOf(emptyList())
        coEvery { groupRepo.getGroupById(GlobalGroupConstants.GROUP_ID) } returns globalGroup
    }

    private fun viewModel(groupId: String = GlobalGroupConstants.GROUP_ID) = BoardViewModel(
        savedStateHandle   = SavedStateHandle(mapOf("groupId" to groupId)),
        watchBoardMessages = watchBoardMessages,
        sendBoardMessage   = sendBoardMessage,
        updateBoardMessage = updateBoardMessage,
        deleteBoardMessage = deleteBoardMessage,
        setBoardAnnouncement = setBoardAnnouncement,
        userRepository     = userRepo,
        groupRepository    = groupRepo,
        logger             = logger
    )

    @Test
    fun `isGroupAdmin is true for the global group's admin (createdBy)`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = adminUid, displayName = "Saúl", email = "nezaboost@gmail.com", photoUrl = null)

        val vm = viewModel()

        assertTrue(vm.uiState.value.isGroupAdmin)
    }

    @Test
    fun `isGroupAdmin is false for a regular member of the global group`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = memberUid, displayName = "Otro usuario", email = "otro@test.com", photoUrl = null)

        val vm = viewModel()

        assertFalse(vm.uiState.value.isGroupAdmin)
    }

    @Test
    fun `toggleAnnouncement is a no-op for a non-admin member of the global group`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = memberUid, displayName = "Otro usuario", email = "otro@test.com", photoUrl = null)
        val vm = viewModel()
        val message = BoardMessage(
            id = "msg1", groupId = GlobalGroupConstants.GROUP_ID, senderId = memberUid,
            senderName = "Otro usuario", senderPhotoUrl = null, content = "hola", timestamp = 0L
        )

        vm.toggleAnnouncement(message)

        coVerify(exactly = 0) { setBoardAnnouncement(any(), any(), any()) }
    }

    // ── Analytics enrichment (PR-21/22) ─────────────────────────────────────────

    @Test
    fun `sendOrSaveMessage for a new message logs board_message_sent with action sent`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = adminUid, displayName = "Saúl", email = "nezaboost@gmail.com", photoUrl = null)
        coEvery { sendBoardMessage(any()) } just Runs
        val vm = viewModel()

        vm.onInputChanged("hola grupo")
        vm.sendOrSaveMessage()

        verify { logger.logEvent(match { event ->
            event.name == "board_message_sent" &&
            event.params["action"] == "sent" &&
            event.params["is_group_admin"] == true &&
            event.params["group_name"] == "NFLocos de Corazón"
        }) }
    }

    @Test
    fun `sendOrSaveMessage while editing logs board_message_sent with action edited`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = adminUid, displayName = "Saúl", email = "nezaboost@gmail.com", photoUrl = null)
        coEvery { updateBoardMessage(any(), any(), any()) } just Runs
        val vm = viewModel()
        val message = BoardMessage(
            id = "msg1", groupId = GlobalGroupConstants.GROUP_ID, senderId = adminUid,
            senderName = "Saúl", senderPhotoUrl = null, content = "hola", timestamp = 0L
        )

        vm.startEditing(message)
        vm.onInputChanged("hola editado")
        vm.sendOrSaveMessage()

        coVerify { updateBoardMessage(GlobalGroupConstants.GROUP_ID, "msg1", "hola editado") }
        verify { logger.logEvent(match { it.name == "board_message_sent" && it.params["action"] == "edited" }) }
    }

    @Test
    fun `deleteMessage logs board_message_deleted with admin and ownership flags`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = adminUid, displayName = "Saúl", email = "nezaboost@gmail.com", photoUrl = null)
        coEvery { deleteBoardMessage(any(), any()) } just Runs
        val vm = viewModel()
        val message = BoardMessage(
            id = "msg1", groupId = GlobalGroupConstants.GROUP_ID, senderId = memberUid,
            senderName = "Otro usuario", senderPhotoUrl = null, content = "hola", timestamp = 0L
        )

        vm.deleteMessage(message)

        verify { logger.logEvent(match { event ->
            event.name == "board_message_deleted" &&
            event.params["is_group_admin"] == true &&
            event.params["is_own_message"] == false &&
            event.params["group_name"] == "NFLocos de Corazón"
        }) }
    }

    @Test
    fun `toggleAnnouncement by the admin logs board_announcement_toggled with the group name`() = runTest(coroutineRule.dispatcher) {
        every { userRepo.getCurrentUser() } returns User(uid = adminUid, displayName = "Saúl", email = "nezaboost@gmail.com", photoUrl = null)
        coEvery { setBoardAnnouncement(any(), any(), any()) } just Runs
        val vm = viewModel()
        val message = BoardMessage(
            id = "msg1", groupId = GlobalGroupConstants.GROUP_ID, senderId = memberUid,
            senderName = "Otro usuario", senderPhotoUrl = null, content = "hola", timestamp = 0L
        )

        vm.toggleAnnouncement(message)

        verify { logger.logEvent(match { event ->
            event.name == "board_announcement_toggled" &&
            event.params["is_announcement"] == true &&
            event.params["group_name"] == "NFLocos de Corazón"
        }) }
    }
}
