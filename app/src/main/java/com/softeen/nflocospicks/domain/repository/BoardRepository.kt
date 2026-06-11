package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.BoardMessage
import kotlinx.coroutines.flow.Flow

interface BoardRepository {

    /**
     * Emite la lista de mensajes del tablero en tiempo real, ordenados por timestamp ascendente.
     */
    fun watchMessages(groupId: String): Flow<List<BoardMessage>>

    /**
     * Persiste un nuevo mensaje en Firestore.
     */
    suspend fun sendMessage(message: BoardMessage)

    /**
     * Actualiza el contenido de un mensaje existente y registra el timestamp de edición.
     */
    suspend fun updateMessage(groupId: String, messageId: String, newContent: String)

    /**
     * Elimina un mensaje. El caller es responsable de validar los permisos antes de invocar.
     */
    suspend fun deleteMessage(groupId: String, messageId: String)

    /**
     * Activa o desactiva el flag de anuncio en un mensaje. Solo el admin del grupo debe invocar esto.
     */
    suspend fun setAnnouncement(groupId: String, messageId: String, isAnnouncement: Boolean)
}
