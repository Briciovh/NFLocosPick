package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class CapturingJoinRepository : GroupRepository {
    var capturedInviteCode: String? = null

    private val stub = Group(
        id = "g1", name = "Test Group", inviteCode = "ABC123",
        createdBy = "u0", memberIds = listOf("u0", "u1")
    )

    override suspend fun createGroup(name: String, creatorUserId: String): Group = throw NotImplementedError()
    override fun getGroupsForUser(userId: String): Flow<List<Group>> = throw NotImplementedError()
    override suspend fun getGroupById(groupId: String): Group = throw NotImplementedError()

    override suspend fun joinGroup(inviteCode: String, userId: String): Group {
        capturedInviteCode = inviteCode
        return stub
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class JoinGroupUseCaseTest {

    @Test
    fun `invite code with spaces and lowercase letters is trimmed and uppercased`() = runBlocking {
        val repo = CapturingJoinRepository()
        val useCase = JoinGroupUseCase(repo)

        useCase(" abc123 ", userId = "u1")

        assertEquals("ABC123", repo.capturedInviteCode)
    }

    @Test
    fun `already-normalized code passes through unchanged`() = runBlocking {
        val repo = CapturingJoinRepository()
        val useCase = JoinGroupUseCase(repo)

        useCase("XYZ789", userId = "u1")

        assertEquals("XYZ789", repo.capturedInviteCode)
    }
}
