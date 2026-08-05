package com.softeen.nflocospicks.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val createGroupUseCase      : CreateGroupUseCase,
    private val joinGroupUseCase        : JoinGroupUseCase,
    private val getGroupsForUserUseCase : GetGroupsForUserUseCase,
    private val scoreWeekPicksUseCase   : ScoreWeekPicksUseCase,
    private val userRepository          : UserRepository,
    private val preferencesRepository   : UserPreferencesRepository,
    private val logger                  : AppLogger
) : ViewModel() {

    private val _groupListState = MutableStateFlow<GroupListUiState>(GroupListUiState.Loading)
    val groupListState: StateFlow<GroupListUiState> = _groupListState.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionUiState>(GroupActionUiState.Idle)
    val actionState: StateFlow<GroupActionUiState> = _actionState.asStateFlow()

    /**
     * Efectos de un solo disparo (navegación).
     * Solo GroupsScreen los consume — CreateGroupScreen y JoinGroupScreen
     * reaccionan a [actionState] directamente para evitar contención del Channel.
     */
    val effects = Channel<GroupUiEffect>(Channel.BUFFERED)

    init {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            viewModelScope.launch { effects.send(GroupUiEffect.NavigateToLogin) }
        } else {
            observeGroups(currentUser.uid)
        }
    }

    private fun observeGroups(userId: String) {
        combine(
            getGroupsForUserUseCase(userId),
            preferencesRepository.preferencesFlow
        ) { realGroups, prefs ->
            if (prefs.useTestingData) {
                // Inyectar el grupo mock al inicio de la lista, con el UID real incluido.
                val mockGroup = MockDataProvider.MOCK_GROUP.copy(
                    memberIds = listOf(userId) + MockDataProvider.MOCK_GROUP.memberIds
                )
                listOf(mockGroup) + realGroups
            } else {
                realGroups
            }
        }
            .onEach { groups ->
                _groupListState.value = GroupListUiState.Success(groups)
            }
            .catch { e -> _groupListState.value = GroupListUiState.Error(e.message ?: "Error al cargar grupos") }
            .launchIn(viewModelScope)
    }

    fun createGroup(name: String) {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _actionState.value = GroupActionUiState.Loading
            try {
                val group = createGroupUseCase(name, userId)
                _actionState.value = GroupActionUiState.Success(group)
                logger.logEvent(AppEvent.GroupCreated(group.id))
            } catch (e: Exception) {
                _actionState.value = GroupActionUiState.Error(e.message ?: "Error al crear el grupo")
            }
        }
    }

    fun joinGroup(inviteCode: String) {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _actionState.value = GroupActionUiState.Loading
            try {
                val group = joinGroupUseCase(inviteCode, userId)
                _actionState.value = GroupActionUiState.Success(group)
                logger.logEvent(AppEvent.GroupJoined(group.id))
            } catch (e: NoSuchElementException) {
                _actionState.value = GroupActionUiState.Error("Código de invitación inválido")
            } catch (e: Exception) {
                _actionState.value = GroupActionUiState.Error(e.message ?: "Error al unirse al grupo")
            }
        }
    }

    /** Restablece actionState a Idle. Llamar tras navegar de regreso desde Create/Join. */
    fun resetActionState() {
        _actionState.value = GroupActionUiState.Idle
    }

    fun onGroupClicked(groupId: String) {
        logger.logEvent(AppEvent.GroupOpened(groupId))
        viewModelScope.launch { effects.send(GroupUiEffect.NavigateToGroupSession(groupId)) }
    }

    /**
     * Disparo manual del puntuado desde la tarjeta de grupo.
     * Invoca la Cloud Function "scoreGroupWeek" (vía ScoreWeekPicksUseCase) y
     * espera la respuesta en viewModelScope para dar feedback inmediato al
     * usuario vía Snackbar. El puntuado periódico ya no corre en el cliente:
     * lo dispara Cloud Scheduler cada 30 min en días de partido.
     */
    fun onScoreClicked(groupId: String) {
        viewModelScope.launch {
            try {
                val count = scoreWeekPicksUseCase(groupId)
                effects.send(GroupUiEffect.ScoringResult(groupId, count))
                logger.logEvent(AppEvent.ScoringCompleted(groupId, count))
            } catch (e: Exception) {
                effects.send(GroupUiEffect.ScoringError(e.message ?: "Error al puntuar"))
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            userRepository.signOut()
            effects.send(GroupUiEffect.NavigateToLogin)
        }
    }
}
