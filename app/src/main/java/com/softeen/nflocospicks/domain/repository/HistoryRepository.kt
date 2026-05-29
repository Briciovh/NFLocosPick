package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.WeekHistoryEntry

interface HistoryRepository {
    suspend fun getPickHistory(groupId: String, userId: String): List<WeekHistoryEntry>
}
