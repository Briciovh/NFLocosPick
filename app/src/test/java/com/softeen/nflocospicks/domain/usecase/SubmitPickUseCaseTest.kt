package com.softeen.nflocospicks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.repository.PickRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class SubmitPickUseCaseTest {

    private val pickRepository = mockk<PickRepository>()
    private val useCase = SubmitPickUseCase(pickRepository)

    @Test
    fun `invoke calls repository when kickoff is in the future`() = runBlocking {
        // Given
        val futureKickoff = System.currentTimeMillis() + 100_000
        coEvery { 
            pickRepository.submitPick(any(), any(), any(), any(), any()) 
        } returns Unit

        // When
        useCase("group1", "2025-week-01", "user1", "game1", "KC", futureKickoff)

        // Then
        coVerify { 
            pickRepository.submitPick("group1", "2025-week-01", "user1", "game1", "KC") 
        }
    }

    @Test
    fun `invoke throws IllegalStateException when kickoff has passed`() = runBlocking {
        // Given
        val pastKickoff = System.currentTimeMillis() - 100_000

        // When / Then
        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                useCase("group1", "2025-week-01", "user1", "game1", "KC", pastKickoff)
            }
        }
        assertThat(exception.message).contains("ya comenzó")
        coVerify(exactly = 0) { pickRepository.submitPick(any(), any(), any(), any(), any()) }
    }
}
