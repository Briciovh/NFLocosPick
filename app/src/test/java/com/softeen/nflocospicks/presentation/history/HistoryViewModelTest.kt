package com.softeen.nflocospicks.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetPickHistoryUseCase
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private val getPickHistoryUseCase = mockk<GetPickHistoryUseCase>()
    private val userRepo = mockk<UserRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val logger = mockk<AppLogger>(relaxed = true)

    private val testUser = User("user1", "Test", "t@t.com", null)
    private val testGroup = Group("g1", "Test Group", "ABC", "user1", listOf("user1"))

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() } returns testUser
        coEvery { groupRepo.getGroupById("g1") } returns testGroup
    }

    private fun viewModel(groupId: String = "g1") = HistoryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
        getPickHistoryUseCase = getPickHistoryUseCase,
        userRepository = userRepo,
        groupRepository = groupRepo,
        logger = logger
    )

    @Test
    fun `loadHistory logs analytics event with group name`() = runTest(coroutineRule.dispatcher) {
        coEvery { getPickHistoryUseCase("g1", "user1") } returns emptyList()

        viewModel("g1")

        verify { logger.logEvent(match { it.name == "pick_history_viewed" && it.params["group_name"] == "Test Group" }) }
    }

    @Test
    fun `toggleWeek logs analytics event`() = runTest(coroutineRule.dispatcher) {
        coEvery { getPickHistoryUseCase("g1", "user1") } returns emptyList()
        val vm = viewModel("g1")

        vm.toggleWeek("week1")

        verify { logger.logEvent(match { it.name == "history_week_toggled" && it.params["week_id"] == "week1" && it.params["expanded"] == true }) }
    }
}
