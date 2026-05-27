package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebasePickDataSource
import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.repository.PickRepository
import javax.inject.Inject

class PickRepositoryImpl @Inject constructor(
    private val dataSource: FirebasePickDataSource
) : PickRepository {

    override suspend fun submitPick(
        groupId: String,
        weekId: String,
        userId: String,
        gameId: String,
        teamAbbr: String
    ) = dataSource.submitPick(groupId, weekId, userId, gameId, teamAbbr)

    override suspend fun getPicksForWeek(
        groupId: String,
        weekId: String,
        userId: String
    ): Map<String, Pick> =
        dataSource.getPicksForWeek(groupId, weekId, userId)
}
