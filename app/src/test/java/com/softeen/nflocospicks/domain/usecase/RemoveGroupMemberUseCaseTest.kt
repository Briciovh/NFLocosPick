package com.softeen.nflocospicks.domain.usecase

import android.net.Uri
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.JoinGroupResult
import com.softeen.nflocospicks.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class CapturingMemberManagementRepository : GroupRepository {
    var removedGroupId: String? = null
    var removedTargetUserId: String? = null
    var removedBlock: Boolean? = null

    var unblockedGroupId: String? = null
    var unblockedTargetUserId: String? = null

    override suspend fun createGroup(name: String, creatorUserId: String): Group = throw NotImplementedError()
    override suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult = throw NotImplementedError()
    override fun getGroupsForUser(userId: String): Flow<List<Group>> = throw NotImplementedError()
    override suspend fun getGroupById(groupId: String): Group = throw NotImplementedError()
    override suspend fun uploadGroupPhoto(groupId: String, uri: Uri): Result<String> = throw NotImplementedError()
    override suspend fun setGroupIcon(groupId: String, iconId: String): Result<Unit> = throw NotImplementedError()
    override suspend fun renameGroup(groupId: String, newName: String) = throw NotImplementedError()
    override suspend fun deleteGroup(groupId: String) = throw NotImplementedError()

    override suspend fun removeGroupMember(groupId: String, targetUserId: String, block: Boolean) {
        removedGroupId = groupId
        removedTargetUserId = targetUserId
        removedBlock = block
    }

    override suspend fun unblockGroupMember(groupId: String, targetUserId: String) {
        unblockedGroupId = groupId
        unblockedTargetUserId = targetUserId
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class RemoveGroupMemberUseCaseTest {

    @Test
    fun `invoke forwards groupId, targetUserId, and block=false to the repository`() = runBlocking {
        val repo = CapturingMemberManagementRepository()
        val useCase = RemoveGroupMemberUseCase(repo)

        useCase("g1", "u2", block = false)

        assertEquals("g1", repo.removedGroupId)
        assertEquals("u2", repo.removedTargetUserId)
        assertEquals(false, repo.removedBlock)
    }

    @Test
    fun `invoke forwards block=true to the repository`() = runBlocking {
        val repo = CapturingMemberManagementRepository()
        val useCase = RemoveGroupMemberUseCase(repo)

        useCase("g1", "u2", block = true)

        assertEquals("g1", repo.removedGroupId)
        assertEquals("u2", repo.removedTargetUserId)
        assertEquals(true, repo.removedBlock)
    }
}

class UnblockGroupMemberUseCaseTest {

    @Test
    fun `invoke forwards groupId and targetUserId to the repository`() = runBlocking {
        val repo = CapturingMemberManagementRepository()
        val useCase = UnblockGroupMemberUseCase(repo)

        useCase("g1", "u2")

        assertEquals("g1", repo.unblockedGroupId)
        assertEquals("u2", repo.unblockedTargetUserId)
    }
}
