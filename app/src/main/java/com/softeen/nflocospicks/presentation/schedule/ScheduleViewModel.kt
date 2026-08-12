package com.softeen.nflocospicks.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getCurrentWeekGamesUseCase: GetCurrentWeekGamesUseCase,
    private val preferencesRepository:      UserPreferencesRepository,
    private val mockSessionRepository:      MockSessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) observeMockGames()
        else {
            loadGames()
            startAutoRefresh()
        }
    }

    // Ruta real: carga única desde ESPN API + caché Firestore.
    fun loadGames() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            try {
                val games  = getCurrentWeekGamesUseCase(groupId)
                val weekId = games.firstOrNull()?.weekId.orEmpty()
                _uiState.value = ScheduleUiState.Success(games, weekId)
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState.Error(
                    e.message ?: "No se pudo cargar el calendario"
                )
            }
        }
    }

    // Refresco silencioso: mantiene los juegos actuales visibles mientras
    // re-consulta ESPN. Usado por pull-to-refresh y por el auto-refresh
    // periódico. No-op para el grupo mock (ya es reactivo) o si aún no
    // hay datos cargados (Loading/Error usan loadGames/onRetry).
    fun refresh() {
        val current = _uiState.value
        if (groupId == MockDataProvider.MOCK_GROUP_ID || current !is ScheduleUiState.Success) return
        viewModelScope.launch {
            _uiState.value = current.copy(isRefreshing = true)
            try {
                val games  = getCurrentWeekGamesUseCase(groupId)
                val weekId = games.firstOrNull()?.weekId.orEmpty()
                _uiState.value = ScheduleUiState.Success(games, weekId, isRefreshing = false)
            } catch (e: Exception) {
                Timber.w(e, "No se pudo refrescar el calendario (no fatal)")
                _uiState.value = current.copy(isRefreshing = false)
            }
        }
    }

    // Dispatchers.Default a propósito, no el Main.immediate por defecto de
    // viewModelScope: los tests de ViewModel reemplazan Dispatchers.Main por
    // un TestDispatcher compartido, y un while(true)+delay ahí nunca deja
    // vacía la cola del scheduler de prueba (runTest se queda esperando para
    // siempre). En Default corre con tiempo real, fuera del scheduler virtual.
    private fun startAutoRefresh() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    // Ruta mock: reactiva — actualiza cuando cambia simulateGamesStarted o los scores.
    private fun observeMockGames() {
        combine(
            preferencesRepository.preferencesFlow,
            mockSessionRepository.sessionFlow
        ) { prefs, session ->
            val base = MockDataProvider.MOCK_GAMES
            if (prefs.simulateGamesStarted)
                MockDataProvider.applyScores(base, session.simulatedScores)
            else
                base
        }
            .onEach { games ->
                _uiState.value = ScheduleUiState.Success(games, MockDataProvider.MOCK_WEEK_ID)
            }
            .catch { e ->
                _uiState.value = ScheduleUiState.Error(
                    e.message ?: "Error al cargar el calendario de prueba"
                )
            }
            .launchIn(viewModelScope)
    }
}
