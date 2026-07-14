package com.softeen.nflocospicks.data.repository

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.data.remote.firebase.FirebasePickDataSource
import com.softeen.nflocospicks.domain.model.Pick
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PickRepositoryIntegrationTest {

    private val dataSource = mockk<FirebasePickDataSource>()
    private val repository = PickRepositoryImpl(dataSource)

    @Test
    fun `getPicksForWeek returns data from dataSource`() = runBlocking {
        // Given
        val picks = mapOf("g1" to Pick("g1", "KC", null, null))
        coEvery { dataSource.getPicksForWeek("group1", "week1", "user1") } returns picks

        // When
        val result = repository.getPicksForWeek("group1", "week1", "user1")

        // Then
        assertThat(result).isEqualTo(picks)
        coVerify { dataSource.getPicksForWeek("group1", "week1", "user1") }
    }

    @Test
    fun `submitPick calls dataSource`() = runBlocking {
        // Given
        coEvery { dataSource.submitPick(any(), any(), any(), any(), any()) } returns Unit

        // When
        repository.submitPick("group1", "week1", "user1", "game1", "KC")

        // Then
        coVerify { dataSource.submitPick("group1", "week1", "user1", "game1", "KC") }
    }
}
