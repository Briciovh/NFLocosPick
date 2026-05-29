package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebaseHistoryDataSource
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import com.softeen.nflocospicks.domain.repository.HistoryRepository
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseHistoryDataSource
) : HistoryRepository {

    override suspend fun getPickHistory(groupId: String, userId: String): List<WeekHistoryEntry> =
        dataSource.getPickHistory(groupId, userId)
}
