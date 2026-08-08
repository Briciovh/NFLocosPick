package com.softeen.nflocospicks.domain.usecase

import android.net.Uri
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// ── Fake ─────────────────────────────────────────────────────────────────────

private class CapturingPhotoRepository : GroupRepository {
    var capturedGroupId: String? = null
    var capturedUri: Uri? = null
    var capturedIconGroupId: String? = null
    var capturedIconId: String? = null

    override suspend fun createGroup(name: String, creatorUserId: String): Group = throw NotImplementedError()
    override suspend fun joinGroup(inviteCode: String, userId: String): Group = throw NotImplementedError()
    override fun getGroupsForUser(userId: String): Flow<List<Group>> = throw NotImplementedError()
    override suspend fun getGroupById(groupId: String): Group = throw NotImplementedError()

    override suspend fun uploadGroupPhoto(groupId: String, uri: Uri): Result<String> {
        capturedGroupId = groupId
        capturedUri = uri
        return Result.success("https://example.com/photo.jpg")
    }

    override suspend fun setGroupIcon(groupId: String, iconId: String): Result<Unit> {
        capturedIconGroupId = groupId
        capturedIconId = iconId
        return Result.success(Unit)
    }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class UploadGroupPhotoUseCaseTest {

    @Test
    fun `invoke forwards groupId and uri to the repository and returns its result`() = runBlocking {
        val repo = CapturingPhotoRepository()
        val useCase = UploadGroupPhotoUseCase(repo)
        val uri = mockk<Uri>()

        val result = useCase("g1", uri)

        assertEquals("g1", repo.capturedGroupId)
        assertEquals(uri, repo.capturedUri)
        assertEquals("https://example.com/photo.jpg", result.getOrNull())
    }
}

class SetGroupIconUseCaseTest {

    @Test
    fun `invoke forwards groupId and iconId to the repository`() = runBlocking {
        val repo = CapturingPhotoRepository()
        val useCase = SetGroupIconUseCase(repo)

        useCase("g1", "trophy")

        assertEquals("g1", repo.capturedIconGroupId)
        assertEquals("trophy", repo.capturedIconId)
    }
}
