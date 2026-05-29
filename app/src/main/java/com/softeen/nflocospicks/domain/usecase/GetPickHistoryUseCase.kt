package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import com.softeen.nflocospicks.domain.repository.HistoryRepository
import javax.inject.Inject

class GetPickHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    suspend operator fun invoke(groupId: String, userId: String): List<WeekHistoryEntry> =
        historyRepository.getPickHistory(groupId, userId)
}
