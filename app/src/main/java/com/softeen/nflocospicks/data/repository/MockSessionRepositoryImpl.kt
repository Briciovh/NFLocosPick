package com.softeen.nflocospicks.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.MockSessionState
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MockSessionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : MockSessionRepository {

    private object Keys {
        val simulatedScores = stringPreferencesKey("mock_simulated_scores")
        val realUserPicks   = stringPreferencesKey("mock_real_user_picks")
    }

    override val sessionFlow: Flow<MockSessionState> = dataStore.data.map { prefs ->
        MockSessionState(
            simulatedScores = prefs[Keys.simulatedScores]?.toScoresMap() ?: emptyMap(),
            realUserPicks   = prefs[Keys.realUserPicks]?.toPicksMap()    ?: emptyMap()
        )
    }

    override suspend fun generateAndSaveScores(games: List<Game>) {
        val scores = games.associate { game ->
            game.id to Pair(Random.nextInt(0, 51), Random.nextInt(0, 51))
        }
        dataStore.edit { prefs ->
            prefs[Keys.simulatedScores] = scores.toScoresString()
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.simulatedScores)
            prefs.remove(Keys.realUserPicks)
        }
    }

    override suspend fun saveRealUserPick(gameId: String, teamAbbr: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.realUserPicks]?.toPicksMap()?.toMutableMap() ?: mutableMapOf()
            current[gameId] = teamAbbr
            prefs[Keys.realUserPicks] = current.toPicksString()
        }
    }

    override suspend fun clearRealUserPick(gameId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.realUserPicks]?.toPicksMap()?.toMutableMap() ?: return@edit
            current.remove(gameId)
            prefs[Keys.realUserPicks] = current.toPicksString()
        }
    }

    // ── Serialización: "gameId:homeScore:awayScore,..." ───────────────────────

    private fun Map<String, Pair<Int, Int>>.toScoresString(): String =
        entries.joinToString(",") { (id, pair) -> "$id:${pair.first}:${pair.second}" }

    private fun String.toScoresMap(): Map<String, Pair<Int, Int>> =
        runCatching {
            if (isBlank()) return emptyMap()
            split(",").associate { entry ->
                val parts = entry.split(":")
                parts[0] to Pair(parts[1].toInt(), parts[2].toInt())
            }
        }.getOrDefault(emptyMap())

    // ── Serialización: "gameId:abbr,..." ─────────────────────────────────────

    private fun Map<String, String>.toPicksString(): String =
        entries.joinToString(",") { (id, abbr) -> "$id:$abbr" }

    private fun String.toPicksMap(): Map<String, String> =
        runCatching {
            if (isBlank()) return emptyMap()
            split(",").associate { entry ->
                val idx = entry.indexOf(':')
                entry.substring(0, idx) to entry.substring(idx + 1)
            }
        }.getOrDefault(emptyMap())
}
