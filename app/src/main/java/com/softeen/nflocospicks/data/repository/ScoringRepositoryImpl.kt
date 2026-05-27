package com.softeen.nflocospicks.data.repository

import com.softeen.nflocospicks.data.remote.firebase.FirebaseScoringDataSource
import com.softeen.nflocospicks.domain.repository.ScoringRepository
import javax.inject.Inject

class ScoringRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseScoringDataSource
) : ScoringRepository {

    override suspend fun scoreWeek(
        groupId   : String,
        weekId    : String,
        memberIds : List<String>,
        winners   : Map<String, String?>
    ): Int = dataSource.scoreWeek(groupId, weekId, memberIds, winners)
}
