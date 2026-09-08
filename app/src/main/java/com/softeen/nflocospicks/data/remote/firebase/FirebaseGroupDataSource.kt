package com.softeen.nflocospicks.data.remote.firebase

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.GroupBlockedException
import com.softeen.nflocospicks.domain.model.JoinGroupResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseGroupDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions
) {
    companion object {
        private const val COLLECTION = "groups"
        private const val CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun generateInviteCode(): String =
            (1..6).map { CODE_CHARS.random() }.joinToString("")
    }

    suspend fun createGroup(name: String, creatorUserId: String): Group {
        val code = generateUniqueInviteCode()
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

    /**
     * Check-and-retry, not a transaction — a genuinely simultaneous createGroup
     * collision is a negligible risk at this app's scale (a few dozen users,
     * group creation is a deliberate infrequent action). This closes the
     * overwhelmingly common case: two sequential creates never end up sharing
     * a code.
     */
    private suspend fun generateUniqueInviteCode(maxAttempts: Int = 5): String {
        repeat(maxAttempts) {
            val candidate = generateInviteCode()
            val collision = firestore.collection(COLLECTION)
                .whereEqualTo("inviteCode", candidate)
                .limit(1)
                .get()
                .await()
            if (collision.isEmpty) return candidate
        }
        throw IllegalStateException("No se pudo generar un código de invitación único tras $maxAttempts intentos")
    }

    suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult {
        val snapshot = firestore.collection(COLLECTION)
            .whereEqualTo("inviteCode", inviteCode)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull()
            ?: throw NoSuchElementException("No group found for invite code: $inviteCode")

        val isBlocked = (doc.get("blockedIds") as? List<*>)?.contains(userId) == true
        if (isBlocked) {
            throw GroupBlockedException()
        }

        val alreadyMember = (doc.get("memberIds") as? List<*>)?.contains(userId) == true
        if (alreadyMember) {
            return JoinGroupResult(doc.toGroup(), alreadyMember = true)
        }

        firestore.collection(COLLECTION).document(doc.id)
            .update("memberIds", FieldValue.arrayUnion(userId))
            .await()

        // Si el usuario tenía standing previo oculto en este grupo, lo des-ocultamos (best-effort)
        runCatching {
            firestore.collection("standings").document(doc.id)
                .collection("members").document(userId)
                .update(mapOf("hidden" to FieldValue.delete(), "hiddenAt" to FieldValue.delete()))
                .await()
        }

        // Re-leemos el documento tras el update para retornar el estado fresco.
        val updated = firestore.collection(COLLECTION).document(doc.id).get().await()
        return JoinGroupResult(updated.toGroup(), alreadyMember = false)
    }

    suspend fun getGroupById(groupId: String): Group =
        firestore.collection(COLLECTION).document(groupId).get().await().toGroup()

    suspend fun uploadPhoto(groupId: String, uri: Uri): String {
        val photoRef = storage.reference.child("group_photos/$groupId")
        photoRef.putFile(uri).await()
        val downloadUrl = photoRef.downloadUrl.await().toString()
        firestore.collection(COLLECTION).document(groupId)
            .update(mapOf("photoUrl" to downloadUrl, "iconId" to FieldValue.delete()))
            .await()
        return downloadUrl
    }

    suspend fun setIcon(groupId: String, iconId: String) {
        firestore.collection(COLLECTION).document(groupId)
            .update(mapOf("iconId" to iconId, "photoUrl" to FieldValue.delete()))
            .await()
    }

    suspend fun renameGroup(groupId: String, newName: String) {
        firestore.collection(COLLECTION).document(groupId)
            .update("name", newName)
            .await()
    }

    /**
     * Invoca la Cloud Function "deleteGroup" (Admin SDK): borra el doc del grupo,
     * sus subcolecciones, el árbol de standings y la foto de Storage. El cliente no
     * puede hacer ese barrido — las reglas niegan el delete directo y las escrituras
     * a standings. La función valida que el usuario autenticado sea el creador.
     */
    suspend fun deleteGroup(groupId: String) {
        functions.getHttpsCallable("deleteGroup")
            .call(mapOf("groupId" to groupId))
            .await()
    }

    /**
     * Quita a un miembro de un grupo y opcionalmente lo bloquea (Cloud Function Admin SDK).
     * Oculta el standing en standings/{groupId}/members/{targetUserId}.
     */
    suspend fun removeGroupMember(groupId: String, targetUserId: String, block: Boolean) {
        functions.getHttpsCallable("removeGroupMember")
            .call(mapOf("groupId" to groupId, "targetUid" to targetUserId, "block" to block))
            .await()
    }

    /**
     * Desbloquea a un miembro de un grupo (Cloud Function Admin SDK).
     * Solo retira a targetUserId de blockedIds.
     */
    suspend fun unblockGroupMember(groupId: String, targetUserId: String) {
        functions.getHttpsCallable("unblockGroupMember")
            .call(mapOf("groupId" to groupId, "targetUid" to targetUserId))
            .await()
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
    memberIds  = (get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    photoUrl   = getString("photoUrl"),
    iconId     = getString("iconId"),
    blockedIds = (get("blockedIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
)
