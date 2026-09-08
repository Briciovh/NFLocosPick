package com.softeen.nflocospicks.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import com.softeen.nflocospicks.domain.model.GroupBlockedException
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
    private val functions = mockk<FirebaseFunctions>()
    private val collection = mockk<CollectionReference>()
    private val query = mockk<Query>()

    private val dataSource = FirebaseGroupDataSource(firestore, storage, functions)

    private fun stubInviteCodeQuery() {
        every { firestore.collection("groups") } returns collection
        every { collection.whereEqualTo("inviteCode", any<String>()) } returns query
        every { query.limit(1) } returns query
    }

    // joinGroup's query chain doesn't call .limit(1) — separate stub, not reusing stubInviteCodeQuery().
    private fun stubJoinInviteCodeQuery() {
        every { firestore.collection("groups") } returns collection
        every { collection.whereEqualTo("inviteCode", any<String>()) } returns query
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

    private fun stubStandingsUnhide(groupId: String, userId: String, succeeds: Boolean = true) {
        val standingsColl = mockk<CollectionReference>()
        val groupDoc = mockk<DocumentReference>()
        val membersColl = mockk<CollectionReference>()
        val memberDoc = mockk<DocumentReference>()

        every { firestore.collection("standings") } returns standingsColl
        every { standingsColl.document(groupId) } returns groupDoc
        every { groupDoc.collection("members") } returns membersColl
        every { membersColl.document(userId) } returns memberDoc
        if (succeeds) {
            every { memberDoc.update(any<Map<String, Any>>()) } returns Tasks.forResult<Void>(null)
        } else {
            every { memberDoc.update(any<Map<String, Any>>()) } returns Tasks.forException(RuntimeException("standing missing"))
        }
    }

    @Test
    fun `joinGroup adds the user and returns alreadyMember false when not previously a member`() = runBlocking {
        stubJoinInviteCodeQuery()
        stubStandingsUnhide("g1", "u1")
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns "g1"
        every { doc.get("blockedIds") } returns emptyList<String>()
        every { doc.get("memberIds") } returns listOf("u0")
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc) }
        every { query.get() } returns Tasks.forResult(snapshot)

        val docRef = mockk<DocumentReference>()
        every { collection.document("g1") } returns docRef
        every { docRef.update("memberIds", any()) } returns Tasks.forResult<Void>(null)

        val updatedDoc = mockk<DocumentSnapshot>()
        every { updatedDoc.id } returns "g1"
        every { updatedDoc.getString("name") } returns "Los Locos"
        every { updatedDoc.getString("inviteCode") } returns "ABC123"
        every { updatedDoc.getString("createdBy") } returns "u0"
        every { updatedDoc.get("memberIds") } returns listOf("u0", "u1")
        every { updatedDoc.getString("photoUrl") } returns null
        every { updatedDoc.getString("iconId") } returns null
        every { updatedDoc.get("blockedIds") } returns emptyList<String>()
        every { docRef.get() } returns Tasks.forResult(updatedDoc)

        val result = dataSource.joinGroup("ABC123", "u1")

        assertEquals(false, result.alreadyMember)
        assertEquals("g1", result.group.id)
        assertEquals(listOf("u0", "u1"), result.group.memberIds)
        verify(exactly = 1) { docRef.update("memberIds", any()) }
        verify(exactly = 1) { docRef.get() }
    }

    @Test
    fun `joinGroup succeeds even if standings unhide throws an exception`() = runBlocking {
        stubJoinInviteCodeQuery()
        stubStandingsUnhide("g1", "u1", succeeds = false)
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns "g1"
        every { doc.get("blockedIds") } returns emptyList<String>()
        every { doc.get("memberIds") } returns listOf("u0")
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc) }
        every { query.get() } returns Tasks.forResult(snapshot)

        val docRef = mockk<DocumentReference>()
        every { collection.document("g1") } returns docRef
        every { docRef.update("memberIds", any()) } returns Tasks.forResult<Void>(null)

        val updatedDoc = mockk<DocumentSnapshot>()
        every { updatedDoc.id } returns "g1"
        every { updatedDoc.getString("name") } returns "Los Locos"
        every { updatedDoc.getString("inviteCode") } returns "ABC123"
        every { updatedDoc.getString("createdBy") } returns "u0"
        every { updatedDoc.get("memberIds") } returns listOf("u0", "u1")
        every { updatedDoc.getString("photoUrl") } returns null
        every { updatedDoc.getString("iconId") } returns null
        every { updatedDoc.get("blockedIds") } returns emptyList<String>()
        every { docRef.get() } returns Tasks.forResult(updatedDoc)

        val result = dataSource.joinGroup("ABC123", "u1")

        assertEquals(false, result.alreadyMember)
        assertEquals("g1", result.group.id)
    }

    @Test
    fun `joinGroup throws GroupBlockedException when the user is blocked`() {
        stubJoinInviteCodeQuery()
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns "g1"
        every { doc.get("blockedIds") } returns listOf("u1")
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc) }
        every { query.get() } returns Tasks.forResult(snapshot)

        assertThrows(GroupBlockedException::class.java) {
            runBlocking { dataSource.joinGroup("ABC123", "u1") }
        }
    }

    @Test
    fun `joinGroup returns alreadyMember true without writing or re-reading when the user is already a member`() = runBlocking {
        stubJoinInviteCodeQuery()
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns "g1"
        every { doc.getString("name") } returns "Los Locos"
        every { doc.getString("inviteCode") } returns "ABC123"
        every { doc.getString("createdBy") } returns "u0"
        every { doc.get("memberIds") } returns listOf("u0", "u1")
        every { doc.getString("photoUrl") } returns null
        every { doc.getString("iconId") } returns null
        every { doc.get("blockedIds") } returns emptyList<String>()
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(doc) }
        every { query.get() } returns Tasks.forResult(snapshot)

        val result = dataSource.joinGroup("ABC123", "u1")

        assertEquals(true, result.alreadyMember)
        assertEquals(listOf("u0", "u1"), result.group.memberIds)
        verify(exactly = 0) { collection.document(any()) }
    }

    @Test
    fun `joinGroup throws NoSuchElementException when no group matches the invite code`() {
        stubJoinInviteCodeQuery()
        val emptySnapshot = mockk<QuerySnapshot> { every { documents } returns emptyList() }
        every { query.get() } returns Tasks.forResult(emptySnapshot)

        assertThrows(NoSuchElementException::class.java) {
            runBlocking { dataSource.joinGroup("BAD123", "u1") }
        }
    }

    @Test
    fun `renameGroup writes the new name onto the group doc`() = runBlocking {
        every { firestore.collection("groups") } returns collection
        val docRef = mockk<DocumentReference>()
        every { collection.document("g1") } returns docRef
        every { docRef.update("name", "Nuevo Nombre") } returns Tasks.forResult<Void>(null)

        dataSource.renameGroup("g1", "Nuevo Nombre")

        verify(exactly = 1) { docRef.update("name", "Nuevo Nombre") }
    }

    @Test
    fun `deleteGroup invokes the deleteGroup callable with the groupId`() = runBlocking {
        val callableRef = mockk<HttpsCallableReference>()
        val callResult = mockk<HttpsCallableResult>()
        every { functions.getHttpsCallable("deleteGroup") } returns callableRef
        every { callableRef.call(any()) } returns Tasks.forResult(callResult)

        dataSource.deleteGroup("g1")

        verify(exactly = 1) { functions.getHttpsCallable("deleteGroup") }
        verify(exactly = 1) { callableRef.call(mapOf("groupId" to "g1")) }
    }

    @Test
    fun `removeGroupMember invokes the removeGroupMember callable with groupId, targetUid, and block`() = runBlocking {
        val callableRef = mockk<HttpsCallableReference>()
        val callResult = mockk<HttpsCallableResult>()
        every { functions.getHttpsCallable("removeGroupMember") } returns callableRef
        every { callableRef.call(any()) } returns Tasks.forResult(callResult)

        dataSource.removeGroupMember("g1", "u2", true)

        verify(exactly = 1) { functions.getHttpsCallable("removeGroupMember") }
        verify(exactly = 1) { callableRef.call(mapOf("groupId" to "g1", "targetUid" to "u2", "block" to true)) }
    }

    @Test
    fun `unblockGroupMember invokes the unblockGroupMember callable with groupId and targetUid`() = runBlocking {
        val callableRef = mockk<HttpsCallableReference>()
        val callResult = mockk<HttpsCallableResult>()
        every { functions.getHttpsCallable("unblockGroupMember") } returns callableRef
        every { callableRef.call(any()) } returns Tasks.forResult(callResult)

        dataSource.unblockGroupMember("g1", "u2")

        verify(exactly = 1) { functions.getHttpsCallable("unblockGroupMember") }
        verify(exactly = 1) { callableRef.call(mapOf("groupId" to "g1", "targetUid" to "u2")) }
    }
}
