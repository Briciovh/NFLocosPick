package com.softeen.nflocospicks.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UserPreferencesRepositoryImplTest {

    private val dataStore = FakePreferencesDataStore()
    private val repo = UserPreferencesRepositoryImpl(dataStore)

    private suspend fun prefs(): UserPreferences = repo.preferencesFlow.first()

    @Test
    fun `empty store yields all defaults`() = runTest {
        assertThat(prefs()).isEqualTo(UserPreferences())
        assertThat(prefs().useTestingData).isFalse()
        assertThat(prefs().simulateGamesStarted).isFalse()
        assertThat(prefs().favoriteTeamAbbr).isNull()
        assertThat(prefs().languageTag).isNull()
        assertThat(prefs().fontScalePreference).isNull()
        assertThat(prefs().iconScalePreference).isNull()
    }

    @Test
    fun `each setter with a value is reflected in the flow`() = runTest {
        repo.setFavoriteTeam("KC")
        repo.setUseTestingData(true)
        repo.setSimulateGamesStarted(true)
        repo.setLanguage("es-MX")
        repo.setFontScale("grande")
        repo.setIconScale("pequeno")

        assertThat(prefs()).isEqualTo(
            UserPreferences(
                favoriteTeamAbbr = "KC",
                useTestingData = true,
                simulateGamesStarted = true,
                languageTag = "es-MX",
                fontScalePreference = "grande",
                iconScalePreference = "pequeno"
            )
        )
    }

    @Test
    fun `nullable setters remove their key rather than storing the string null`() = runTest {
        repo.setFavoriteTeam("KC")
        repo.setLanguage("en")
        repo.setFontScale("grande")
        repo.setIconScale("mediano")

        repo.setFavoriteTeam(null)
        repo.setLanguage(null)
        repo.setFontScale(null)
        repo.setIconScale(null)

        assertThat(prefs().favoriteTeamAbbr).isNull()
        assertThat(prefs().languageTag).isNull()
        assertThat(prefs().fontScalePreference).isNull()
        assertThat(prefs().iconScalePreference).isNull()

        // The keys must be absent, not present with a "null" value.
        val raw = dataStore.data.first()
        assertThat(raw.contains(stringPreferencesKey("favorite_team"))).isFalse()
        assertThat(raw.contains(stringPreferencesKey("language_tag"))).isFalse()
        assertThat(raw.contains(stringPreferencesKey("font_scale"))).isFalse()
        assertThat(raw.contains(stringPreferencesKey("icon_scale"))).isFalse()
    }

    @Test
    fun `boolean setters toggle back to false explicitly`() = runTest {
        repo.setUseTestingData(true)
        assertThat(prefs().useTestingData).isTrue()

        repo.setUseTestingData(false)
        assertThat(prefs().useTestingData).isFalse()
    }

    @Test
    fun `a preexisting key in the store is surfaced by the flow`() = runTest {
        dataStore.edit { it[stringPreferencesKey("favorite_team")] = "GB" }
        assertThat(prefs().favoriteTeamAbbr).isEqualTo("GB")
    }
}
