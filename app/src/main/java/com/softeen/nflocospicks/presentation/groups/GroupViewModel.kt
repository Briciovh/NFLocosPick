package com.softeen.nflocospicks.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val createGroupUseCase: CreateGroupUseCase,
    private val joinGroupUseCase: JoinGroupUseCase,
    private val getGroupsForUserUseCase: GetGroupsForUserUseCase,
    private val userRepository: UserRepository
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
        getGroupsForUserUseCase(userId)
            .onEach { groups -> _groupListState.value = GroupListUiState.Success(groups) }
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
        viewModelScope.launch { effects.send(GroupUiEffect.NavigateToSchedule(groupId)) }
    }

    fun onPicksClicked(groupId: String) {
        viewModelScope.launch { effects.send(GroupUiEffect.NavigateToPicks(groupId)) }
    }

    fun onSignOut() {
        viewModelScope.launch {
            userRepository.signOut()
            effects.send(GroupUiEffect.NavigateToLogin)
        }
    }
}
