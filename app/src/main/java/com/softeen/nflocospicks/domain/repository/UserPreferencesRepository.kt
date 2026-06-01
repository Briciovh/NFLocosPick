package com.softeen.nflocospicks.domain.repository

import com.softeen.nflocospicks.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setFavoriteTeam(abbr: String?)
    suspend fun setUseTestingData(enabled: Boolean)
    suspend fun setSimulateGamesStarted(enabled: Boolean)
}
