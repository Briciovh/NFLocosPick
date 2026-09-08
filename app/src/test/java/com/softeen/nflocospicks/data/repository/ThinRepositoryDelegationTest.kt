package com.softeen.nflocospicks.data.repository

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.data.remote.firebase.FirebaseBoardDataSource
import com.softeen.nflocospicks.data.remote.firebase.FirebaseGroupDataSource
import com.softeen.nflocospicks.data.remote.firebase.FirebaseHistoryDataSource
import com.softeen.nflocospicks.data.remote.firebase.FirebaseLeaderboardDataSource
import com.softeen.nflocospicks.data.remote.firebase.FirebaseScoringDataSource
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.JoinGroupResult
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The repository impls in `data/repository/` that are pure 1:1 delegation onto a
 * Firebase data source. Tests pin the delegation contract; the only impl with
 * real logic here is [GroupRepositoryImpl], whose `uploadGroupPhoto` /
 * `setGroupIcon` wrap the data-source call in `runCatching`.
 */
class ThinRepositoryDelegationTest {

    // ── BoardRepositoryImpl ──────────────────────────────────────────────────
    private val boardDs = mockk<FirebaseBoardDataSource>(relaxed = true)
    private val boardRepo = BoardRepositoryImpl(boardDs)

    private fun message() = BoardMessage(
        id = "m1", groupId = "g1", senderId = "u1", senderName = "Alex",
        senderPhotoUrl = null, content = "hola", timestamp = 0L
    )

    @Test
    fun `BoardRepositoryImpl delegates every method to the data source`() = runBlocking {
        val msg = message()
        val flow = flowOf(listOf(msg))
        every { boardDs.watchMessages("g1") } returns flow

        assertThat(boardRepo.watchMessages("g1").first()).containsExactly(msg)
        boardRepo.sendMessage(msg)
        boardRepo.updateMessage("g1", "m1", "editado")
        boardRepo.deleteMessage("g1", "m1")
        boardRepo.setAnnouncement("g1", "m1", true)

        verify(exactly = 1) { boardDs.watchMessages("g1") }
        coVerify(exactly = 1) { boardDs.sendMessage(msg) }
        coVerify(exactly = 1) { boardDs.updateMessage("g1", "m1", "editado") }
        coVerify(exactly = 1) { boardDs.deleteMessage("g1", "m1") }
        coVerify(exactly = 1) { boardDs.setAnnouncement("g1", "m1", true) }
    }

    // ── HistoryRepositoryImpl ────────────────────────────────────────────────
    @Test
    fun `HistoryRepositoryImpl delegates getPickHistory and returns the data-source result`() = runBlocking {
        val ds = mockk<FirebaseHistoryDataSource>()
        val history = listOf(WeekHistoryEntry("2025-week-01", 3, emptyList()))
        coEvery { ds.getPickHistory("g1", "u1") } returns history

        val result = HistoryRepositoryImpl(ds).getPickHistory("g1", "u1")

        assertThat(result).isEqualTo(history)
        coVerify(exactly = 1) { ds.getPickHistory("g1", "u1") }
    }

    // ── LeaderboardRepositoryImpl ────────────────────────────────────────────
    @Test
    fun `LeaderboardRepositoryImpl re-emits the data-source flow`() = runBlocking {
        val ds = mockk<FirebaseLeaderboardDataSource>()
        val entries = listOf(
            LeaderboardEntry("u1", "Alex", null, 10, 0, emptyMap(), 1)
        )
        every { ds.getLeaderboard("g1") } returns flowOf(entries)

        val emitted = LeaderboardRepositoryImpl(ds).getLeaderboard("g1").first()

        assertThat(emitted).isEqualTo(entries)
        verify(exactly = 1) { ds.getLeaderboard("g1") }
    }

    // ── ScoringRepositoryImpl ────────────────────────────────────────────────
    @Test
    fun `ScoringRepositoryImpl delegates scoreWeek and returns the scored count`() = runBlocking {
        val ds = mockk<FirebaseScoringDataSource>()
        coEvery { ds.scoreWeek("g1") } returns 7

        assertThat(ScoringRepositoryImpl(ds).scoreWeek("g1")).isEqualTo(7)
        coVerify(exactly = 1) { ds.scoreWeek("g1") }
    }

    // ── GroupRepositoryImpl ──────────────────────────────────────────────────
    private val groupDs = mockk<FirebaseGroupDataSource>(relaxUnitFun = true)
    private val groupRepo = GroupRepositoryImpl(groupDs)
    private val group = Group("g1", "Los Locos", "ABC123", "u1", listOf("u1"))

    @Test
    fun `GroupRepositoryImpl delegates the straight pass-through methods`() = runBlocking {
        coEvery { groupDs.createGroup("Los Locos", "u1") } returns group
        coEvery { groupDs.joinGroup("ABC123", "u2") } returns JoinGroupResult(group, alreadyMember = false)
        coEvery { groupDs.getGroupById("g1") } returns group
        every { groupDs.getGroupsForUser("u1") } returns flowOf(listOf(group))

        assertThat(groupRepo.createGroup("Los Locos", "u1")).isEqualTo(group)
        assertThat(groupRepo.joinGroup("ABC123", "u2").alreadyMember).isFalse()
        assertThat(groupRepo.getGroupById("g1")).isEqualTo(group)
        assertThat(groupRepo.getGroupsForUser("u1").first()).containsExactly(group)
        groupRepo.renameGroup("g1", "Nuevo")
        groupRepo.deleteGroup("g1")
        groupRepo.removeGroupMember("g1", "u2", true)
        groupRepo.unblockGroupMember("g1", "u2")

        coVerify(exactly = 1) { groupDs.createGroup("Los Locos", "u1") }
        coVerify(exactly = 1) { groupDs.joinGroup("ABC123", "u2") }
        coVerify(exactly = 1) { groupDs.getGroupById("g1") }
        verify(exactly = 1) { groupDs.getGroupsForUser("u1") }
        coVerify(exactly = 1) { groupDs.renameGroup("g1", "Nuevo") }
        coVerify(exactly = 1) { groupDs.deleteGroup("g1") }
        coVerify(exactly = 1) { groupDs.removeGroupMember("g1", "u2", true) }
        coVerify(exactly = 1) { groupDs.unblockGroupMember("g1", "u2") }
    }

    @Test
    fun `GroupRepositoryImpl uploadGroupPhoto wraps success and failure in a Result`() = runBlocking {
        val uri = mockk<Uri>()
        coEvery { groupDs.uploadPhoto("g1", uri) } returns "https://cdn/g.jpg"
        assertThat(groupRepo.uploadGroupPhoto("g1", uri).getOrNull()).isEqualTo("https://cdn/g.jpg")

        val boom = RuntimeException("storage down")
        coEvery { groupDs.uploadPhoto("g2", uri) } throws boom
        val failed = groupRepo.uploadGroupPhoto("g2", uri)
        assertThat(failed.isFailure).isTrue()
        assertThat(failed.exceptionOrNull()).isSameInstanceAs(boom)
    }

    @Test
    fun `GroupRepositoryImpl setGroupIcon wraps success and failure in a Result`() = runBlocking {
        coEvery { groupDs.setIcon("g1", "icon_a") } returns Unit
        assertThat(groupRepo.setGroupIcon("g1", "icon_a").isSuccess).isTrue()

        val boom = RuntimeException("permission denied")
        coEvery { groupDs.setIcon("g2", "icon_b") } throws boom
        val failed = groupRepo.setGroupIcon("g2", "icon_b")
        assertThat(failed.isFailure).isTrue()
        assertThat(failed.exceptionOrNull()).isSameInstanceAs(boom)
    }
}
