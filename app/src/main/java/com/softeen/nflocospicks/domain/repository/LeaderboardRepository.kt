package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import kotlinx.coroutines.flow.Flow

interface LeaderboardRepository {
    fun getLeaderboard(groupId: String): Flow<List<LeaderboardEntry>>
}
