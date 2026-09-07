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

private class CapturingDeleteRenameRepository : GroupRepository {
    var deletedGroupId: String? = null
    var renamedGroupId: String? = null
    var renamedName: String? = null

    override suspend fun createGroup(name: String, creatorUserId: String): Group = throw NotImplementedError()
    override suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult = throw NotImplementedError()
    override fun getGroupsForUser(userId: String): Flow<List<Group>> = throw NotImplementedError()
    override suspend fun getGroupById(groupId: String): Group = throw NotImplementedError()
    override suspend fun uploadGroupPhoto(groupId: String, uri: Uri): Result<String> = throw NotImplementedError()
    override suspend fun setGroupIcon(groupId: String, iconId: String): Result<Unit> = throw NotImplementedError()

    override suspend fun renameGroup(groupId: String, newName: String) {
        renamedGroupId = groupId
        renamedName = newName
    }

    override suspend fun deleteGroup(groupId: String) {
        deletedGroupId = groupId
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class DeleteGroupUseCaseTest {

    @Test
    fun `invoke forwards the groupId to the repository`() = runBlocking {
        val repo = CapturingDeleteRenameRepository()
        val useCase = DeleteGroupUseCase(repo)

        useCase("g1")

        assertEquals("g1", repo.deletedGroupId)
    }
}

class RenameGroupUseCaseTest {

    @Test
    fun `name with leading and trailing spaces is trimmed before reaching the repository`() = runBlocking {
        val repo = CapturingDeleteRenameRepository()
        val useCase = RenameGroupUseCase(repo)

        useCase("g1", "  Nuevo Nombre  ")

        assertEquals("g1", repo.renamedGroupId)
        assertEquals("Nuevo Nombre", repo.renamedName)
    }

    @Test
    fun `name without extra spaces passes through unchanged`() = runBlocking {
        val repo = CapturingDeleteRenameRepository()
        val useCase = RenameGroupUseCase(repo)

        useCase("g1", "Los Locos")

        assertEquals("Los Locos", repo.renamedName)
    }
}
