package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class CapturingGroupRepository : GroupRepository {
    var capturedName: String? = null

    override suspend fun createGroup(name: String, creatorUserId: String): Group {
        capturedName = name
        return Group(id = "g1", name = name, inviteCode = "XYZ000",
                     createdBy = creatorUserId, memberIds = listOf(creatorUserId))
    }

    override suspend fun joinGroup(inviteCode: String, userId: String): Group = throw NotImplementedError()
    override fun getGroupsForUser(userId: String): Flow<List<Group>> = throw NotImplementedError()
    override suspend fun getGroupById(groupId: String): Group = throw NotImplementedError()
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class CreateGroupUseCaseTest {

    @Test
    fun `name with leading and trailing spaces is trimmed before reaching the repository`() = runBlocking {
        val repo = CapturingGroupRepository()
        val useCase = CreateGroupUseCase(repo)

        useCase("  Los Locos  ", creatorUserId = "u1")

        assertEquals("Los Locos", repo.capturedName)
    }

    @Test
    fun `name without extra spaces passes through unchanged`() = runBlocking {
        val repo = CapturingGroupRepository()
        val useCase = CreateGroupUseCase(repo)

        useCase("Fantasy League", creatorUserId = "u1")

        assertEquals("Fantasy League", repo.capturedName)
    }
}
