package com.softeen.nflocospicks.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.softeen.nflocospicks.domain.model.BoardMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseBoardDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun boardCollection(groupId: String) =
        firestore.collection("groups").document(groupId).collection("board")

    fun watchMessages(groupId: String): Flow<List<BoardMessage>> = callbackFlow {
        val listener = boardCollection(groupId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents
                    ?.mapNotNull { runCatching { it.toBoardMessage(groupId) }.getOrNull() }
                    ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(message: BoardMessage) {
        val doc = mapOf(
            "senderId"       to message.senderId,
            "senderName"     to message.senderName,
            "senderPhotoUrl" to message.senderPhotoUrl,
            "content"        to message.content,
            "timestamp"      to message.timestamp,
            "editedAt"       to message.editedAt,
            "isAnnouncement" to message.isAnnouncement
        )
        boardCollection(message.groupId).add(doc).await()
    }

    suspend fun updateMessage(groupId: String, messageId: String, newContent: String) {
        boardCollection(groupId).document(messageId)
            .update(
                "content",  newContent,
                "editedAt", System.currentTimeMillis()
            )
            .await()
    }

    suspend fun deleteMessage(groupId: String, messageId: String) {
        boardCollection(groupId).document(messageId).delete().await()
    }

    suspend fun setAnnouncement(groupId: String, messageId: String, isAnnouncement: Boolean) {
        boardCollection(groupId).document(messageId)
            .update("isAnnouncement", isAnnouncement)
            .await()
    }
}

// ── Extensión privada para mapear DocumentSnapshot → BoardMessage ─────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toBoardMessage(groupId: String): BoardMessage =
    BoardMessage(
        id             = id,
        groupId        = groupId,
        senderId       = getString("senderId").orEmpty(),
        senderName     = getString("senderName").orEmpty(),
        senderPhotoUrl = getString("senderPhotoUrl"),
        content        = getString("content").orEmpty(),
        timestamp      = getLong("timestamp") ?: 0L,
        editedAt       = getLong("editedAt"),
        isAnnouncement = getBoolean("isAnnouncement") ?: false
    )
