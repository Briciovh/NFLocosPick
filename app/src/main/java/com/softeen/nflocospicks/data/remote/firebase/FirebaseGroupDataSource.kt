package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.softeen.nflocospicks.domain.model.Group
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseGroupDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION = "groups"
        private const val CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun generateInviteCode(): String =
            (1..6).map { CODE_CHARS.random() }.joinToString("")
    }

    suspend fun createGroup(name: String, creatorUserId: String): Group {
        val code = generateInviteCode()
        val doc = mapOf(
            "name"      to name,
            "inviteCode" to code,
            "createdBy"  to creatorUserId,
            "memberIds"  to listOf(creatorUserId)
        )
        val ref = firestore.collection(COLLECTION).add(doc).await()
        return Group(
            id         = ref.id,
            name       = name,
            inviteCode = code,
            createdBy  = creatorUserId,
            memberIds  = listOf(creatorUserId)
        )
    }

    suspend fun joinGroup(inviteCode: String, userId: String): Group {
        val snapshot = firestore.collection(COLLECTION)
            .whereEqualTo("inviteCode", inviteCode)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull()
            ?: throw NoSuchElementException("No group found for invite code: $inviteCode")

        firestore.collection(COLLECTION).document(doc.id)
            .update("memberIds", FieldValue.arrayUnion(userId))
            .await()

        // Re-leemos el documento tras el update para retornar el estado fresco.
        val updated = firestore.collection(COLLECTION).document(doc.id).get().await()
        return updated.toGroup()
    }

    fun getGroupsForUser(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = firestore.collection(COLLECTION)
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents
                    ?.mapNotNull { runCatching { it.toGroup() }.getOrNull() }
                    ?: emptyList()
                trySend(groups)
            }
        awaitClose { listener.remove() }
    }
}

// ── Extensiones privadas para mapear DocumentSnapshot → Group ────────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toGroup(): Group = Group(
    id         = id,
    name       = getString("name").orEmpty(),
    inviteCode = getString("inviteCode").orEmpty(),
    createdBy  = getString("createdBy").orEmpty(),
    memberIds  = (get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
)
