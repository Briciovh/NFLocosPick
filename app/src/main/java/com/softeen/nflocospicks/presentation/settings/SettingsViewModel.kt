package com.softeen.nflocospicks.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo:            UserPreferencesRepository,
    private val mockSessionRepo: MockSessionRepository,
    private val logger:          AppLogger
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repo.preferencesFlow.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    fun setFavoriteTeam(abbr: String?) {
        viewModelScope.launch {
            repo.setFavoriteTeam(abbr)
            if (abbr != null) logger.logEvent(AppEvent.FavoriteTeamSet(abbr))
        }
    }

    fun setUseTestingData(enabled: Boolean) {
        viewModelScope.launch {
            // Al desactivar testing, limpiar también la simulación.
            if (!enabled) {
                mockSessionRepo.clearSession()
                repo.setSimulateGamesStarted(false)
            }
            repo.setUseTestingData(enabled)
        }
    }

    fun setSimulateGamesStarted(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Generar scores primero para que estén listos al activar el flag.
                mockSessionRepo.generateAndSaveScores(MockDataProvider.MOCK_GAMES)
            } else {
                mockSessionRepo.clearSession()
            }
            repo.setSimulateGamesStarted(enabled)
        }
    }

    fun setLanguage(tag: String?) {
        viewModelScope.launch {
            repo.setLanguage(tag)
            val localeList = if (tag.isNullOrEmpty()) LocaleListCompat.getEmptyLocaleList()
                             else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(localeList)
            if (!tag.isNullOrEmpty()) logger.logEvent(AppEvent.LanguageChanged(tag))
        }
    }
}
