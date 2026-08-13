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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
}
