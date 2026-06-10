package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebaseBoardDataSource
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BoardRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseBoardDataSource
) : BoardRepository {

    override fun watchMessages(groupId: String): Flow<List<BoardMessage>> =
        dataSource.watchMessages(groupId)

    override suspend fun sendMessage(message: BoardMessage) =
        dataSource.sendMessage(message)

    override suspend fun updateMessage(groupId: String, messageId: String, newContent: String) =
        dataSource.updateMessage(groupId, messageId, newContent)

    override suspend fun deleteMessage(groupId: String, messageId: String) =
        dataSource.deleteMessage(groupId, messageId)

    override suspend fun setAnnouncement(groupId: String, messageId: String, isAnnouncement: Boolean) =
        dataSource.setAnnouncement(groupId, messageId, isAnnouncement)
}
