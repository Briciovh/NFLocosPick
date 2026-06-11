package com.softeen.nflocospicks.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.domain.usecase.GetPickHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPickHistoryUseCase: GetPickHistoryUseCase,
    private val userRepository: UserRepository,
    private val logger: AppLogger
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            runCatching { getPickHistoryUseCase(groupId, userId) }
                .onSuccess { weeks ->
                    _uiState.value = HistoryUiState.Success(weeks)
                    logger.logEvent(AppEvent.PickHistoryViewed(groupId))
                }
                .onFailure { e -> _uiState.value = HistoryUiState.Error(e.message ?: "Error al cargar historial") }
        }
    }

    fun toggleWeek(weekId: String) {
        val current = _uiState.value as? HistoryUiState.Success ?: return
        _uiState.value = current.copy(
            expandedWeekId = if (current.expandedWeekId == weekId) null else weekId
        )
    }
}
