package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Pick
import com.softeen.nflocospicks.domain.repository.PickRepository
import javax.inject.Inject

class GetWeekPicksUseCase @Inject constructor(
    private val pickRepository: PickRepository
) {
    suspend operator fun invoke(
        groupId: String,
        weekId: String,
        userId: String
    ): Map<String, Pick> =
        pickRepository.getPicksForWeek(groupId, weekId, userId)
}
