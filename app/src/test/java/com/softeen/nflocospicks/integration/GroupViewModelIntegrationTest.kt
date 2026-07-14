package com.softeen.nflocospicks.integration

import androidx.work.WorkManager
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.presentation.groups.GroupActionUiState
import com.softeen.nflocospicks.presentation.groups.GroupViewModel
import com.softeen.nflocospicks.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests: GroupViewModel + real CreateGroupUseCase / JoinGroupUseCase + MockK repo.
 *
 * Unit tests (Step 4) mocked the use cases and verified ViewModel state transitions.
 * These tests wire the real use cases between the ViewModel and a MockK GroupRepository,
 * verifying that input normalization (name.trim(), inviteCode.trim().uppercase()) actually
 * reaches the repository boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelIntegrationTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    // ── Shared mocks ──────────────────────────────────────────────────────────

    private val userRepo    = mockk<UserRepository>()
    private val prefsRepo   = mockk<UserPreferencesRepository>()
    private val scoreUseCase = mockk<ScoreWeekPicksUseCase>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val logger      = mockk<AppLogger>(relaxed = true)

    private val testUser = User(uid = "user1", displayName = "Test", email = "t@t.com", photoUrl = null)

    private val stubGroup = Group(
        id = "g1", name = "stub", inviteCode = "ABC123",
        createdBy = "user1", memberIds = listOf("user1")
    )

    @Before
    fun setUp() {
        every { userRepo.getCurrentUser() } returns testUser
        every { prefsRepo.preferencesFlow } returns flowOf(UserPreferences())
    }

    // ── Factory: real use cases wired to the provided GroupRepository mock ────

    private fun viewModel(groupRepo: GroupRepository) = GroupViewModel(
        createGroupUseCase      = CreateGroupUseCase(groupRepo),       // ← REAL
        joinGroupUseCase        = JoinGroupUseCase(groupRepo),          // ← REAL
        getGroupsForUserUseCase = GetGroupsForUserUseCase(groupRepo),   // ← REAL
        scoreWeekPicksUseCase   = scoreUseCase,
        userRepository          = userRepo,
        preferencesRepository   = prefsRepo,
        workManager             = workManager,
        logger                  = logger
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `createGroup passes trimmed name to repository through the real use case`() = runTest(coroutineRule.dispatcher) {
        val groupRepo = mockk<GroupRepository>()
        every  { groupRepo.getGroupsForUser(any()) }       returns flowOf(emptyList())
        coEvery { groupRepo.createGroup(any(), any()) }    returns stubGroup

        val vm = viewModel(groupRepo)
        vm.createGroup("  Los Locos  ")       // espacios que el use case debe recortar

        // El repositorio debe haber recibido el nombre recortado, no el original
        coVerify { groupRepo.createGroup("Los Locos", testUser.uid) }
        assertEquals(GroupActionUiState.Success(stubGroup), vm.actionState.value)
    }

    @Test
    fun `joinGroup passes trimmed and uppercased code to repository through the real use case`() = runTest(coroutineRule.dispatcher) {
        val groupRepo = mockk<GroupRepository>()
        every  { groupRepo.getGroupsForUser(any()) }    returns flowOf(emptyList())
        coEvery { groupRepo.joinGroup(any(), any()) }   returns stubGroup

        val vm = viewModel(groupRepo)
        vm.joinGroup(" abc123 ")        // minúsculas y espacios que el use case debe normalizar

        // El repositorio debe haber recibido el código normalizado
        coVerify { groupRepo.joinGroup("ABC123", testUser.uid) }
        assertEquals(GroupActionUiState.Success(stubGroup), vm.actionState.value)
    }
}
