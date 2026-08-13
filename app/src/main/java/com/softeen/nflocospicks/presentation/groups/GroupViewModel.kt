package com.softeen.nflocospicks.presentation.groups

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.analytics.AppEvent
import com.softeen.nflocospicks.analytics.AppLogger
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.model.GlobalGroupConstants
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SetGroupIconUseCase
import com.softeen.nflocospicks.domain.usecase.UploadGroupPhotoUseCase
import com.softeen.nflocospicks.domain.usecase.WatchBoardMessagesUseCase
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
import timber.log.Timber

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val createGroupUseCase      : CreateGroupUseCase,
    private val joinGroupUseCase        : JoinGroupUseCase,
    private val getGroupsForUserUseCase : GetGroupsForUserUseCase,
    private val scoreWeekPicksUseCase   : ScoreWeekPicksUseCase,
    private val uploadGroupPhotoUseCase : UploadGroupPhotoUseCase,
    private val setGroupIconUseCase     : SetGroupIconUseCase,
    private val watchBoardMessagesUseCase : WatchBoardMessagesUseCase,
    private val userRepository          : UserRepository,
    private val preferencesRepository   : UserPreferencesRepository,
    private val logger                  : AppLogger
) : ViewModel() {

    private val _groupListState = MutableStateFlow<GroupListUiState>(GroupListUiState.Loading)
    val groupListState: StateFlow<GroupListUiState> = _groupListState.asStateFlow()

    /** Últimos mensajes del board del grupo global, para el panel de PR-19 en GroupsScreen. */
    private val _globalFeedMessages = MutableStateFlow<List<BoardMessage>>(emptyList())
    val globalFeedMessages: StateFlow<List<BoardMessage>> = _globalFeedMessages.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionUiState>(GroupActionUiState.Idle)
    val actionState: StateFlow<GroupActionUiState> = _actionState.asStateFlow()

    private val _photoUiState = MutableStateFlow<GroupPhotoUiState>(GroupPhotoUiState.Idle)
    val photoUiState: StateFlow<GroupPhotoUiState> = _photoUiState.asStateFlow()

    /** Read once per composition — used to gate the group-photo edit badge to the creator. */
    val currentUserId: String? get() = userRepository.getCurrentUser()?.uid

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
            observeGlobalFeed()
        }
    }

    /** Alimenta [globalFeedMessages] — falla en silencio, el panel es un accesorio no crítico. */
    private fun observeGlobalFeed() {
        watchBoardMessagesUseCase(GlobalGroupConstants.GROUP_ID)
            .onEach { messages -> _globalFeedMessages.value = messages }
            .catch { e -> Timber.w(e, "observeGlobalFeed: no se pudo cargar el feed del grupo global") }
            .launchIn(viewModelScope)
    }

    private fun observeGroups(userId: String) {
        combine(
            getGroupsForUserUseCase(userId),
            preferencesRepository.preferencesFlow
        ) { realGroups, prefs ->
            val groups = if (prefs.useTestingData) {
                // Inyectar el grupo mock al inicio de la lista, con el UID real incluido.
                val mockGroup = MockDataProvider.MOCK_GROUP.copy(
                    memberIds = listOf(userId) + MockDataProvider.MOCK_GROUP.memberIds
                )
                listOf(mockGroup) + realGroups
            } else {
                realGroups
            }
            // El grupo global (PR-16) siempre queda primero, sin importar el orden en que
            // Firestore lo haya devuelto — sortedByDescending es estable, así que el resto
            // conserva su orden relativo.
            groups.sortedByDescending { it.id == GlobalGroupConstants.GROUP_ID }
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

    /**
     * Sube [uri] como foto del grupo. Rechaza silenciosamente (deja el estado en Idle,
     * sin llamar al backend) si [requesterUserId] no es el creador del grupo — defensa en
     * profundidad junto a las reglas de Firestore/Storage, que son la fuente de verdad real.
     */
    fun uploadGroupPhoto(group: Group, requesterUserId: String, uri: Uri) {
        if (group.createdBy != requesterUserId) return
        viewModelScope.launch {
            _photoUiState.value = GroupPhotoUiState.Uploading
            uploadGroupPhotoUseCase(group.id, uri)
                .onSuccess { _photoUiState.value = GroupPhotoUiState.Idle }
                .onFailure { e -> _photoUiState.value = GroupPhotoUiState.Error(e.message ?: "Error al subir la foto") }
        }
    }

    /** Mismo guard de permisos que [uploadGroupPhoto], para el flujo de ícono predefinido. */
    fun setGroupIcon(group: Group, requesterUserId: String, iconId: String) {
        if (group.createdBy != requesterUserId) return
        viewModelScope.launch {
            _photoUiState.value = GroupPhotoUiState.Uploading
            setGroupIconUseCase(group.id, iconId)
                .onSuccess { _photoUiState.value = GroupPhotoUiState.Idle }
                .onFailure { e -> _photoUiState.value = GroupPhotoUiState.Error(e.message ?: "Error al fijar el ícono") }
        }
    }

    /** Restablece photoUiState a Idle. Llamar al cerrar el picker de imagen. */
    fun resetPhotoUiState() {
        _photoUiState.value = GroupPhotoUiState.Idle
    }

    fun onSignOut() {
        viewModelScope.launch {
            userRepository.signOut()
            effects.send(GroupUiEffect.NavigateToLogin)
        }
    }
}
