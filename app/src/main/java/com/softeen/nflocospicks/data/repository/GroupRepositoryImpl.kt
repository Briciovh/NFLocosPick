package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebaseGroupDataSource
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GroupRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseGroupDataSource
) : GroupRepository {

    override suspend fun createGroup(name: String, creatorUserId: String): Group =
        dataSource.createGroup(name, creatorUserId)

    override suspend fun joinGroup(inviteCode: String, userId: String): Group =
        dataSource.joinGroup(inviteCode, userId)

    override fun getGroupsForUser(userId: String): Flow<List<Group>> =
        dataSource.getGroupsForUser(userId)
}
