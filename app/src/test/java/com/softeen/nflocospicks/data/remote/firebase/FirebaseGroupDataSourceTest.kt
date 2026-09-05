package com.softeen.nflocospicks.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseGroupDataSourceTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val storage = mockk<FirebaseStorage>()
    private val collection = mockk<CollectionReference>()
    private val query = mockk<Query>()

    private val dataSource = FirebaseGroupDataSource(firestore, storage)

    private fun stubInviteCodeQuery() {
        every { firestore.collection("groups") } returns collection
        every { collection.whereEqualTo("inviteCode", any<String>()) } returns query
        every { query.limit(1) } returns query
    }

    @Test
    fun `createGroup retries invite code generation when the first candidate collides`() = runBlocking {
        stubInviteCodeQuery()
        val collisionSnapshot = mockk<QuerySnapshot> { every { isEmpty } returns false }
        val freeSnapshot = mockk<QuerySnapshot> { every { isEmpty } returns true }
        every { query.get() } returnsMany listOf(
            Tasks.forResult(collisionSnapshot),
            Tasks.forResult(freeSnapshot)
        )
        val addedRef = mockk<DocumentReference> { every { id } returns "g1" }
        every { collection.add(any()) } returns Tasks.forResult(addedRef)

        val group = dataSource.createGroup("Los Locos", "u1")

        assertEquals("g1", group.id)
        assertEquals("Los Locos", group.name)
        verify(exactly = 2) { query.get() }
        verify(exactly = 1) { collection.add(any()) }
    }

    @Test
    fun `createGroup succeeds on the first try when there is no collision`() = runBlocking {
        stubInviteCodeQuery()
        val freeSnapshot = mockk<QuerySnapshot> { every { isEmpty } returns true }
        every { query.get() } returns Tasks.forResult(freeSnapshot)
        val addedRef = mockk<DocumentReference> { every { id } returns "g1" }
        every { collection.add(any()) } returns Tasks.forResult(addedRef)

        dataSource.createGroup("Los Locos", "u1")

        verify(exactly = 1) { query.get() }
    }

    @Test
    fun `createGroup throws after exhausting max attempts when every candidate collides`() {
        stubInviteCodeQuery()
        val collisionSnapshot = mockk<QuerySnapshot> { every { isEmpty } returns false }
        every { query.get() } returns Tasks.forResult(collisionSnapshot)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { dataSource.createGroup("Los Locos", "u1") }
        }
        verify(exactly = 5) { query.get() }
        verify(exactly = 0) { collection.add(any()) }
    }
}
