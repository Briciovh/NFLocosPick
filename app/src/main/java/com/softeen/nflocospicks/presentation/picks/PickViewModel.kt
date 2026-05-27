package com.softeen.nflocospicks.presentation.picks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import com.softeen.nflocospicks.domain.usecase.GetWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SubmitPickUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PickViewModel @Inject constructor(
    private val getCurrentWeekGamesUseCase: GetCurrentWeekGamesUseCase,
    private val getWeekPicksUseCase: GetWeekPicksUseCase,
    private val submitPickUseCase: SubmitPickUseCase,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<PickUiState>(PickUiState.Loading)
    val uiState: StateFlow<PickUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _uiState.value = PickUiState.Loading
            try {
                val games = getCurrentWeekGamesUseCase(groupId)
                val weekId = games.firstOrNull()?.weekId ?: ""
                val picks  = if (weekId.isNotEmpty()) {
                    getWeekPicksUseCase(groupId, weekId, userId)
                } else {
                    emptyMap()
                }
                val now = System.currentTimeMillis()
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

    /**
     * Envía el pick para [gameId] con [teamAbbr]. Aplica un update optimista
     * para respuesta inmediata en la UI. Revierte si la escritura falla.
     */
    fun submitPick(gameId: String, teamAbbr: String, kickoffTime: Long) {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        val currentState = _uiState.value as? PickUiState.Success ?: return

        // Guardar el estado anterior para poder revertir
        val previousItems = currentState.items

        // Update optimista
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
                // Revertir al estado anterior
                _uiState.update { state ->
                    if (state is PickUiState.Success) {
                        state.copy(items = previousItems)
                    } else state
                }
                _errorMessage.value = e.message ?: "No se pudo guardar tu pick"
            }
        }
    }

    fun onErrorShown() {
        _errorMessage.value = null
    }
}
