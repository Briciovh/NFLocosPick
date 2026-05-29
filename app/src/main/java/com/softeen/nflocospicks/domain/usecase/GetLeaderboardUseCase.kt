package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLeaderboardUseCase @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository
) {
    operator fun invoke(groupId: String): Flow<List<LeaderboardEntry>> =
        leaderboardRepository.getLeaderboard(groupId)
}
