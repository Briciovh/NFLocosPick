package com.softeen.nflocospicks.domain.usecase

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UploadProfilePhotoUseCaseTest {

    private val repo = mockk<UserRepository>()
    private val uri = mockk<Uri>()
    private val useCase = UploadProfilePhotoUseCase(repo)

    @Test
    fun `forwards uid and uri and returns the repository success result`() = runBlocking {
        coEvery { repo.uploadProfilePhoto("u1", uri) } returns Result.success("https://cdn/p.jpg")

        val result = useCase.invoke("u1", uri)

        assertThat(result.getOrNull()).isEqualTo("https://cdn/p.jpg")
        coVerify(exactly = 1) { repo.uploadProfilePhoto("u1", uri) }
    }

    @Test
    fun `propagates a repository failure result unchanged`() = runBlocking {
        val boom = IllegalStateException("upload failed")
        coEvery { repo.uploadProfilePhoto("u1", uri) } returns Result.failure(boom)

        val result = useCase.invoke("u1", uri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(boom)
    }
}
