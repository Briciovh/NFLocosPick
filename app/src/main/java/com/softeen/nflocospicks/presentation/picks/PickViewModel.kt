package com.softeen.nflocospicks.presentation.picks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SubmitPickUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PickViewModel @Inject constructor(
    private val getCurrentWeekGamesUseCase: GetCurrentWeekGamesUseCase,
    private val getWeekPicksUseCase:        GetWeekPicksUseCase,
    private val submitPickUseCase:          SubmitPickUseCase,
    private val scoreWeekPicksUseCase:      ScoreWeekPicksUseCase,
    private val userRepository:             UserRepository,
    private val preferencesRepository:      UserPreferencesRepository,
    private val mockSessionRepository:      MockSessionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<PickUiState>(PickUiState.Loading)
    val uiState: StateFlow<PickUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) observeMockPicks()
        else loadData()
    }

    // ── Ruta real ─────────────────────────────────────────────────────────────

    fun loadData() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _uiState.value = PickUiState.Loading
            try {
                val games  = getCurrentWeekGamesUseCase(groupId)
                val weekId = games.firstOrNull()?.weekId ?: ""
                val picks  = if (weekId.isNotEmpty()) {
                    getWeekPicksUseCase(groupId, weekId, userId)
                } else {
                    emptyMap()
                }
                val now   = System.currentTimeMillis()
                val items = games.map { game ->
                    GamePickItem(
                        game       = game,
                        pickedTeam = picks[game.id]?.pickedTeam,
                        isLocked   = now >= game.kickoffTime
                    )
                }
                _uiState.value = PickUiState.Success(items, weekId)
            } catch (e: Exception) {
                _uiState.value = PickUiState.Error(
                    e.message ?: "Error al cargar los partidos"
                )
            }
        }
    }

    // ── Ruta mock ─────────────────────────────────────────────────────────────

    // Reactiva: se actualiza cuando el usuario hace un pick o cuando simulateGamesStarted cambia.
    private fun observeMockPicks() {
        combine(
            preferencesRepository.preferencesFlow,
            mockSessionRepository.sessionFlow
        ) { prefs, session ->
            val games = if (prefs.simulateGamesStarted)
                MockDataProvider.applyScores(MockDataProvider.MOCK_GAMES, session.simulatedScores)
            else
                MockDataProvider.MOCK_GAMES

            val items = games.map { game ->
                GamePickItem(
                    game       = game,
                    pickedTeam = session.realUserPicks[game.id],
                    isLocked   = prefs.simulateGamesStarted  // bloqueado si se está simulando
                )
            }
            Pair(items, MockDataProvider.MOCK_WEEK_ID)
        }
            .onEach { (items, weekId) ->
                _uiState.value = PickUiState.Success(items, weekId)
            }
            .catch { e ->
                _uiState.value = PickUiState.Error(
                    e.message ?: "Error al cargar partidos de prueba"
                )
            }
            .launchIn(viewModelScope)
    }

    // ── Submit pick (bifurcado) ───────────────────────────────────────────────

    /**
     * Envía el pick para [gameId] con [teamAbbr].
     * - Ruta mock: escribe en MockSessionRepository (flow reactivo actualiza la UI).
     * - Ruta real: update optimista + escritura Firestore con revert en caso de error.
     */
    fun submitPick(gameId: String, teamAbbr: String, kickoffTime: Long) {
        val userId = userRepository.getCurrentUser()?.uid ?: return

        if (groupId == MockDataProvider.MOCK_GROUP_ID) {
            viewModelScope.launch {
                mockSessionRepository.saveRealUserPick(gameId, teamAbbr)
                // observeMockPicks() recibe la emisión del sessionFlow y actualiza la UI.
            }
            return
        }

        // Ruta real — update optimista con revert en caso de error.
        val currentState = _uiState.value as? PickUiState.Success ?: return
        val previousItems = currentState.items

        _uiState.update { state ->
            if (state !is PickUiState.Success) return@update state
            state.copy(
                items = state.items.map { item ->
                    if (item.game.id == gameId) item.copy(pickedTeam = teamAbbr) else item
                }
            )
        }

        viewModelScope.launch {
            try {
                submitPickUseCase(
                    groupId     = groupId,
                    weekId      = currentState.weekId,
                    userId      = userId,
                    gameId      = gameId,
                    teamAbbr    = teamAbbr,
                    kickoffTime = kickoffTime
                )
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state is PickUiState.Success) state.copy(items = previousItems) else state
                }
                _errorMessage.value = e.message ?: "No se pudo guardar tu pick"
            }
        }
    }

    fun onErrorShown() {
        _errorMessage.value = null
    }

    fun triggerSync() {
        if (groupId == MockDataProvider.MOCK_GROUP_ID) return
        viewModelScope.launch {
            try {
                scoreWeekPicksUseCase(groupId)
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Error al sincronizar"
            }
        }
    }
}
