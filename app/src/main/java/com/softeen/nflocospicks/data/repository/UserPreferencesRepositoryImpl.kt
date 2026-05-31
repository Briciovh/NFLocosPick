package com.softeen.nflocospicks.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object Keys {
        val favoriteTeam = stringPreferencesKey("favorite_team")
    }

    override val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            favoriteTeamAbbr = prefs[Keys.favoriteTeam]
        )
    }

    override suspend fun setFavoriteTeam(abbr: String?) {
        dataStore.edit { prefs ->
            if (abbr != null) prefs[Keys.favoriteTeam] = abbr
            else prefs.remove(Keys.favoriteTeam)
        }
    }
}
