package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface GroupRepository {

    /**
     * Crea un nuevo documento de grupo en Firestore y retorna el [Group] persistido,
     * incluyendo el [Group.inviteCode] generado y el [Group.id] asignado por Firestore.
     */
    suspend fun createGroup(name: String, creatorUserId: String): Group

    /**
     * Busca un grupo por [inviteCode], agrega [userId] a memberIds y retorna el [Group]
     * actualizado. Lanza [NoSuchElementException] si no existe ningún grupo con ese código.
     */
    suspend fun joinGroup(inviteCode: String, userId: String): Group

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
}
