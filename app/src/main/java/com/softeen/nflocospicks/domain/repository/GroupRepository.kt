package com.softeen.nflocospicks.domain.repository

import android.net.Uri
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.JoinGroupResult
import kotlinx.coroutines.flow.Flow

interface GroupRepository {

    /**
     * Crea un nuevo documento de grupo en Firestore y retorna el [Group] persistido,
     * incluyendo el [Group.inviteCode] generado y el [Group.id] asignado por Firestore.
     */
    suspend fun createGroup(name: String, creatorUserId: String): Group

    /**
     * Busca un grupo por [inviteCode] y retorna un [JoinGroupResult] indicando si [userId]
     * ya era miembro antes de esta llamada. Si no lo era, lo agrega a memberIds. Lanza
     * [NoSuchElementException] si no existe ningún grupo con ese código.
     */
    suspend fun joinGroup(inviteCode: String, userId: String): JoinGroupResult

    /**
     * Emite la lista actual de grupos del usuario y re-emite en cada actualización de
     * Firestore (listener en tiempo real).
     */
    fun getGroupsForUser(userId: String): Flow<List<Group>>

    /**
     * Obtiene un grupo por su [groupId]. Necesario en PR-6 para leer
     * memberIds al puntuar, y en PR-7 para cargar el leaderboard.
     * Lanza excepción si el documento no existe.
     */
    suspend fun getGroupById(groupId: String): Group

    /**
     * Sube [uri] a Storage como la foto del grupo, guarda la URL resultante en
     * `photoUrl` y limpia cualquier `iconId` previo (mutuamente excluyentes).
     * Retorna la URL de descarga. Espejo de [UserRepository.uploadProfilePhoto].
     */
    suspend fun uploadGroupPhoto(groupId: String, uri: Uri): Result<String>

    /**
     * Fija [iconId] (clave del set de íconos predefinidos de grupo) como imagen
     * del grupo y limpia cualquier `photoUrl` previo.
     */
    suspend fun setGroupIcon(groupId: String, iconId: String): Result<Unit>

    /**
     * Renombra el grupo [groupId] a [newName]. Solo el creador puede hacerlo
     * (lo aplican las reglas de Firestore); la actualización se propaga a la
     * lista en vivo por el listener existente.
     */
    suspend fun renameGroup(groupId: String, newName: String)

    /**
     * Elimina por completo el grupo [groupId] — el documento, sus subcolecciones
     * de semanas y del muro, el árbol de standings del grupo y la foto de Storage
     * — vía la Cloud Function `deleteGroup` (Admin SDK). El cliente no tiene
     * permiso para borrar nada de eso directamente (ver `firestore.rules`). La
     * función valida que el solicitante sea el creador y rechaza el grupo global.
     */
    suspend fun deleteGroup(groupId: String)

    /**
     * Quita a [targetUserId] del grupo [groupId] y opcionalmente lo bloquea vía la
     * Cloud Function `removeGroupMember` (Admin SDK). Oculta su standing del
     * leaderboard (`hidden: true`). La función valida que el solicitante sea el
     * creador (`createdBy`), rechaza el grupo global y no permite que el admin se
     * quite a sí mismo.
     */
    suspend fun removeGroupMember(groupId: String, targetUserId: String, block: Boolean)

    /**
     * Desbloquea a [targetUserId] del grupo [groupId] vía la Cloud Function
     * `unblockGroupMember` (Admin SDK). Solo retira el ID de `blockedIds` para
     * permitirle volver a unirse. La función valida que el solicitante sea el creador
     * y rechaza el grupo global.
     */
    suspend fun unblockGroupMember(groupId: String, targetUserId: String)
}
