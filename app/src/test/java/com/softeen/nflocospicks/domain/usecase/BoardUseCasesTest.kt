package com.softeen.nflocospicks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.repository.BoardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The board use cases are pure delegation onto [BoardRepository]. These tests
 * pin that contract: the exact repository method is called with the arguments
 * passed through unchanged, and Flow-returning use cases re-emit the repo flow.
 */
class BoardUseCasesTest {

    private val repo = mockk<BoardRepository>(relaxed = true)

    private fun message(id: String = "m1") = BoardMessage(
        id = id,
        groupId = "g1",
        senderId = "u1",
        senderName = "Alex",
        senderPhotoUrl = null,
        content = "hola",
        timestamp = 0L
    )

    @Test
    fun `SendBoardMessageUseCase forwards the message to the repository`() = runBlocking {
        val msg = message()
        SendBoardMessageUseCase(repo).invoke(msg)
        coVerify(exactly = 1) { repo.sendMessage(msg) }
    }

    @Test
    fun `UpdateBoardMessageUseCase forwards groupId messageId and new content`() = runBlocking {
        UpdateBoardMessageUseCase(repo).invoke("g1", "m1", "editado")
        coVerify(exactly = 1) { repo.updateMessage("g1", "m1", "editado") }
    }

    @Test
    fun `DeleteBoardMessageUseCase forwards groupId and messageId`() = runBlocking {
        DeleteBoardMessageUseCase(repo).invoke("g1", "m1")
        coVerify(exactly = 1) { repo.deleteMessage("g1", "m1") }
    }

    @Test
    fun `SetBoardAnnouncementUseCase forwards the flag verbatim`() = runBlocking {
        SetBoardAnnouncementUseCase(repo).invoke("g1", "m1", true)
        SetBoardAnnouncementUseCase(repo).invoke("g1", "m2", false)
        coVerify(exactly = 1) { repo.setAnnouncement("g1", "m1", true) }
        coVerify(exactly = 1) { repo.setAnnouncement("g1", "m2", false) }
    }

    @Test
    fun `WatchBoardMessagesUseCase re-emits the repository flow for the group`() = runBlocking {
        val messages = listOf(message("m1"), message("m2"))
        every { repo.watchMessages("g1") } returns flowOf(messages)

        val emitted = WatchBoardMessagesUseCase(repo).invoke("g1").first()

        assertThat(emitted).isEqualTo(messages)
        verify(exactly = 1) { repo.watchMessages("g1") }
    }

    @Test
    fun `every board use case propagates a repository failure unchanged`() {
        coEvery { repo.sendMessage(any()) } throws IllegalStateException("send denied")
        coEvery { repo.updateMessage(any(), any(), any()) } throws IllegalStateException("update denied")
        coEvery { repo.deleteMessage(any(), any()) } throws IllegalStateException("delete denied")
        coEvery { repo.setAnnouncement(any(), any(), any()) } throws IllegalStateException("announce denied")

        assertThat(
            assertThrows(IllegalStateException::class.java) {
                runBlocking { SendBoardMessageUseCase(repo).invoke(message()) }
            }
        ).hasMessageThat().isEqualTo("send denied")

        assertThat(
            assertThrows(IllegalStateException::class.java) {
                runBlocking { UpdateBoardMessageUseCase(repo).invoke("g1", "m1", "x") }
            }
        ).hasMessageThat().isEqualTo("update denied")

        assertThat(
            assertThrows(IllegalStateException::class.java) {
                runBlocking { DeleteBoardMessageUseCase(repo).invoke("g1", "m1") }
            }
        ).hasMessageThat().isEqualTo("delete denied")

        assertThat(
            assertThrows(IllegalStateException::class.java) {
                runBlocking { SetBoardAnnouncementUseCase(repo).invoke("g1", "m1", true) }
            }
        ).hasMessageThat().isEqualTo("announce denied")
    }
}
