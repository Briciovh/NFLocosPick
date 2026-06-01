package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.MockSessionState
import kotlinx.coroutines.flow.Flow

interface MockSessionRepository {
    val sessionFlow: Flow<MockSessionState>
    suspend fun generateAndSaveScores(games: List<Game>)
    suspend fun clearSession()
    suspend fun saveRealUserPick(gameId: String, teamAbbr: String)
    suspend fun clearRealUserPick(gameId: String)
}
