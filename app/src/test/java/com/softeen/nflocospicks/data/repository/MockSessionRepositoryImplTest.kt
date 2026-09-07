package com.softeen.nflocospicks.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MockSessionRepositoryImplTest {

    private val dataStore = FakePreferencesDataStore()
    private val repo = MockSessionRepositoryImpl(dataStore)

    // Key literals mirror MockSessionRepositoryImpl.Keys (private in the impl).
    private val scoresKey = stringPreferencesKey("mock_simulated_scores")
    private val picksKey = stringPreferencesKey("mock_real_user_picks")

    private fun game(id: String) = Game(
        id = id,
        weekId = "mock-week-01",
        homeTeam = "Home $id",
        awayTeam = "Away $id",
        homeTeamAbbr = "H$id",
        awayTeamAbbr = "A$id",
        kickoffTime = 0L,
        homeScore = null,
        awayScore = null,
        status = GameStatus.SCHEDULED,
        homeTeamRecord = null,
        awayTeamRecord = null
    )

    @Test
    fun `empty store yields empty maps`() = runTest {
        val state = repo.sessionFlow.first()
        assertThat(state.simulatedScores).isEmpty()
        assertThat(state.realUserPicks).isEmpty()
    }

    @Test
    fun `generateAndSaveScores produces one in-range score pair per game`() = runTest {
        val games = listOf(game("1"), game("2"), game("3"))

        repo.generateAndSaveScores(games)

        val scores = repo.sessionFlow.first().simulatedScores
        assertThat(scores.keys).containsExactly("1", "2", "3")
        scores.values.forEach { (home, away) ->
            assertThat(home).isIn(0..50)
            assertThat(away).isIn(0..50)
        }
    }

    @Test
    fun `simulated scores round-trip through serialization`() = runTest {
        repo.generateAndSaveScores(listOf(game("g1"), game("g2")))
        val first = repo.sessionFlow.first().simulatedScores

        // A fresh repo over the same store must deserialize to the same map.
        val reread = MockSessionRepositoryImpl(dataStore).sessionFlow.first().simulatedScores
        assertThat(reread).isEqualTo(first)
    }

    @Test
    fun `saveRealUserPick accumulates and clearRealUserPick removes a single entry`() = runTest {
        repo.saveRealUserPick("g1", "KC")
        repo.saveRealUserPick("g2", "GB")
        assertThat(repo.sessionFlow.first().realUserPicks)
            .containsExactly("g1", "KC", "g2", "GB")

        repo.clearRealUserPick("g1")
        assertThat(repo.sessionFlow.first().realUserPicks).containsExactly("g2", "GB")
    }

    @Test
    fun `saveRealUserPick overwrites the pick for the same game`() = runTest {
        repo.saveRealUserPick("g1", "KC")
        repo.saveRealUserPick("g1", "DEN")
        assertThat(repo.sessionFlow.first().realUserPicks).containsExactly("g1", "DEN")
    }

    @Test
    fun `clearRealUserPick on an empty store is a no-op`() = runTest {
        repo.clearRealUserPick("does-not-exist")
        assertThat(repo.sessionFlow.first().realUserPicks).isEmpty()
    }

    @Test
    fun `clearSession wipes both maps`() = runTest {
        repo.generateAndSaveScores(listOf(game("g1")))
        repo.saveRealUserPick("g1", "KC")

        repo.clearSession()

        val state = repo.sessionFlow.first()
        assertThat(state.simulatedScores).isEmpty()
        assertThat(state.realUserPicks).isEmpty()
    }

    @Test
    fun `a malformed scores string deserializes to an empty map instead of throwing`() = runTest {
        dataStore.edit { it[scoresKey] = "g1:notANumber:3,g2:missingThirdPart" }
        assertThat(repo.sessionFlow.first().simulatedScores).isEmpty()
    }

    @Test
    fun `a blank scores string deserializes to an empty map`() = runTest {
        dataStore.edit { it[scoresKey] = "" }
        assertThat(repo.sessionFlow.first().simulatedScores).isEmpty()
    }

    @Test
    fun `a malformed picks string deserializes to an empty map instead of throwing`() = runTest {
        dataStore.edit { it[picksKey] = "entryWithNoColon" }
        assertThat(repo.sessionFlow.first().realUserPicks).isEmpty()
    }

    @Test
    fun `picks values keep everything after the first colon`() = runTest {
        // toPicksMap splits on the FIRST ':' — a value that itself contains ':'
        // is preserved verbatim.
        dataStore.edit { it[picksKey] = "g1:KC:extra" }
        assertThat(repo.sessionFlow.first().realUserPicks).containsExactly("g1", "KC:extra")
    }

    @Test
    fun `a comma inside a game id breaks the round-trip (documented parser limitation)`() = runTest {
        // ',' is the entry delimiter, so an id containing it is split apart and
        // the resulting bad segment collapses the whole map to empty. Pins the
        // current (fragile) behavior; real ESPN ids never contain ','.
        repo.saveRealUserPick("g,1", "KC")
        assertThat(repo.sessionFlow.first().realUserPicks).isEmpty()
    }

    @Test
    fun `a single bad segment discards the whole scores dataset, not just that entry`() = runTest {
        // The parser is all-or-nothing: one un-parseable segment aborts the whole
        // associate() inside runCatching, so a partially corrupted string yields
        // an EMPTY map rather than the salvageable entries. Documented, not fixed.
        dataStore.edit { it[scoresKey] = "g1:24:17,bad_entry,g3:10:7" }
        assertThat(repo.sessionFlow.first().simulatedScores).isEmpty()
    }

    @Test
    fun `a trailing comma discards the whole scores dataset`() = runTest {
        dataStore.edit { it[scoresKey] = "g1:24:17," }
        assertThat(repo.sessionFlow.first().simulatedScores).isEmpty()
    }
}
