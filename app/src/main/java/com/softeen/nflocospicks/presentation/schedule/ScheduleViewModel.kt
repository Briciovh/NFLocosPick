package com.softeen.nflocospicks.presentation.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.usecase.GetCurrentWeekGamesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getCurrentWeekGamesUseCase: GetCurrentWeekGamesUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadGames()
    }

    fun loadGames() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            try {
                val games = getCurrentWeekGamesUseCase(groupId)
                val weekId = games.firstOrNull()?.weekId.orEmpty()
                _uiState.value = ScheduleUiState.Success(games, weekId)
            } catch (e: Exception) {
                _uiState.value = ScheduleUiState.Error(
                    e.message ?: "No se pudo cargar el calendario"
                )
            }
        }
    }
}
