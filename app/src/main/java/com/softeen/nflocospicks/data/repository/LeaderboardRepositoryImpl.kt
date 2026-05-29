package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebaseLeaderboardDataSource
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LeaderboardRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseLeaderboardDataSource
) : LeaderboardRepository {

    override fun getLeaderboard(groupId: String): Flow<List<LeaderboardEntry>> =
        dataSource.getLeaderboard(groupId)
}
