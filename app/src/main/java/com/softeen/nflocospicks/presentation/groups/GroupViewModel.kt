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
import com.softeen.nflocospicks.domain.model.GroupBlockedException
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.CreateGroupUseCase
import com.softeen.nflocospicks.domain.usecase.DeleteGroupUseCase
import com.softeen.nflocospicks.domain.usecase.GetGroupsForUserUseCase
import com.softeen.nflocospicks.domain.usecase.JoinGroupUseCase
import com.softeen.nflocospicks.domain.usecase.RemoveGroupMemberUseCase
import com.softeen.nflocospicks.domain.usecase.RenameGroupUseCase
import com.softeen.nflocospicks.domain.usecase.ScoreWeekPicksUseCase
import com.softeen.nflocospicks.domain.usecase.SetGroupIconUseCase
import com.softeen.nflocospicks.domain.usecase.UnblockGroupMemberUseCase
import com.softeen.nflocospicks.domain.usecase.UploadGroupPhotoUseCase
import com.softeen.nflocospicks.domain.usecase.WatchBoardMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val createGroupUseCase        : CreateGroupUseCase,
    private val joinGroupUseCase          : JoinGroupUseCase,
    private val getGroupsForUserUseCase   : GetGroupsForUserUseCase,
    private val scoreWeekPicksUseCase     : ScoreWeekPicksUseCase,
    private val uploadGroupPhotoUseCase   : UploadGroupPhotoUseCase,
    private val setGroupIconUseCase       : SetGroupIconUseCase,
    private val renameGroupUseCase        : RenameGroupUseCase,
    private val deleteGroupUseCase        : DeleteGroupUseCase,
    private val removeGroupMemberUseCase  : RemoveGroupMemberUseCase,
    private val unblockGroupMemberUseCase : UnblockGroupMemberUseCase,
    private val watchBoardMessagesUseCase : WatchBoardMessagesUseCase,
    private val userRepository            : UserRepository,
    private val preferencesRepository     : UserPreferencesRepository,
    private val logger                    : AppLogger
) : ViewModel() {

    /** Lista completa de usuarios para resolver nombres/avatares en GroupSettingsScreen. */
    val allUsers: StateFlow<List<User>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _groupListState = MutableStateFlow<GroupListUiState>(GroupListUiState.Loading)
    val groupListState: StateFlow<GroupListUiState> = _groupListState.asStateFlow()

    /** Últimos mensajes del board del grupo global, para el panel de PR-19 en GroupsScreen. */
    private val _globalFeedMessages = MutableStateFlow<List<BoardMessage>>(emptyList())
    val globalFeedMessages: StateFlow<List<BoardMessage>> = _globalFeedMessages.asStateFlow()

    private val _actionState = MutableStateFlow<GroupActionUiState>(GroupActionUiState.Idle)
    val actionState: StateFlow<GroupActionUiState> = _actionState.asStateFlow()

    private val _photoUiState = MutableStateFlow<GroupPhotoUiState>(GroupPhotoUiState.Idle)
    val photoUiState: StateFlow<GroupPhotoUiState> = _photoUiState.asStateFlow()

    private val _groupSettingsState = MutableStateFlow<GroupSettingsUiState>(GroupSettingsUiState.Idle)
    val groupSettingsState: StateFlow<GroupSettingsUiState> = _groupSettingsState.asStateFlow()

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
                logger.logEvent(AppEvent.GroupCreated(group.id, name))
            } catch (e: Exception) {
                _actionState.value = GroupActionUiState.Error(e.message ?: "Error al crear el grupo")
            }
        }
    }

    fun joinGroup(inviteCode: String, source: String = "manual_code") {
        val userId = userRepository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _actionState.value = GroupActionUiState.Loading
            try {
                val result = joinGroupUseCase(inviteCode, userId)
                _actionState.value = GroupActionUiState.Success(result.group, result.alreadyMember)
                if (!result.alreadyMember) {
                    logger.logEvent(AppEvent.GroupJoined(result.group.id, result.group.name, source))
                }
                effects.send(GroupUiEffect.GroupJoined(result.group.name, result.alreadyMember))
            } catch (e: NoSuchElementException) {
                _actionState.value = GroupActionUiState.Error("Código de invitación inválido")
            } catch (e: GroupBlockedException) {
                _actionState.value = GroupActionUiState.BlockedFromGroup
            } catch (e: Exception) {
                _actionState.value = GroupActionUiState.Error(e.message ?: "Error al unirse al grupo")
            }
        }
    }

    /** Restablece actionState a Idle. Llamar tras navegar de regreso desde Create/Join. */
    fun resetActionState() {
        _actionState.value = GroupActionUiState.Idle
    }

    fun onGroupClicked(groupId: String, source: String = "group_list") {
        val groupName = (groupListState.value as? GroupListUiState.Success)
            ?.groups?.find { it.id == groupId }?.name
        logger.logEvent(AppEvent.GroupOpened(groupId, groupName, source))
        viewModelScope.launch { effects.send(GroupUiEffect.NavigateToGroupSession(groupId)) }
    }

    /**
     * Disparo manual del puntuado desde la tarjeta de grupo.
     * Invoca la Cloud Function "scoreGroupWeek" (vía ScoreWeekPicksUseCase) y
     * espera la respuesta en viewModelScope para dar feedback inmediato al
     * usuario vía Snackbar. El puntuado periódico ya no corre en el cliente:
     * lo dispara Cloud Scheduler cada 30 min en días de partido.
     */
    fun onScoreClicked(groupId: String, source: String = "group_card") {
        viewModelScope.launch {
            try {
                val count = scoreWeekPicksUseCase(groupId)
                effects.send(GroupUiEffect.ScoringResult(groupId, count))
                logger.logEvent(AppEvent.ScoringCompleted(groupId, count, source))
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
                .onSuccess {
                    _photoUiState.value = GroupPhotoUiState.Idle
                    logger.logEvent(AppEvent.GroupPhotoUploaded(group.id))
                }
                .onFailure { e -> _photoUiState.value = GroupPhotoUiState.Error(e.message ?: "Error al subir la foto") }
        }
    }

    /** Mismo guard de permisos que [uploadGroupPhoto], para el flujo de ícono predefinido. */
    fun setGroupIcon(group: Group, requesterUserId: String, iconId: String) {
        if (group.createdBy != requesterUserId) return
        viewModelScope.launch {
            _photoUiState.value = GroupPhotoUiState.Uploading
            setGroupIconUseCase(group.id, iconId)
                .onSuccess {
                    _photoUiState.value = GroupPhotoUiState.Idle
                    logger.logEvent(AppEvent.GroupIconSet(group.id, iconId))
                }
                .onFailure { e -> _photoUiState.value = GroupPhotoUiState.Error(e.message ?: "Error al fijar el ícono") }
        }
    }

    /** Restablece photoUiState a Idle. Llamar al cerrar el picker de imagen. */
    fun resetPhotoUiState() {
        _photoUiState.value = GroupPhotoUiState.Idle
    }

    /**
     * Renombra [group] a [newName]. Rechaza en silencio (deja el estado en Idle, sin
     * tocar el backend) si [requesterUserId] no puede administrar el grupo — defensa en
     * profundidad junto a las reglas de Firestore, que son la fuente de verdad. El nombre
     * nuevo llega a la UI por el listener en vivo; [RenameGroupUseCase] recorta espacios.
     */
    fun renameGroup(group: Group, requesterUserId: String, newName: String) {
        if (!canManageGroup(group, requesterUserId)) return
        viewModelScope.launch {
            _groupSettingsState.value = GroupSettingsUiState.Working
            try {
                renameGroupUseCase(group.id, newName)
                _groupSettingsState.value = GroupSettingsUiState.Idle
                logger.logEvent(AppEvent.GroupRenamed(group.id))
            } catch (e: Exception) {
                _groupSettingsState.value =
                    GroupSettingsUiState.Error(e.message ?: "Error al renombrar el grupo")
            }
        }
    }

    /**
     * Elimina [group] por completo vía la Cloud Function `deleteGroup`. Mismos guards que
     * [renameGroup]. Al terminar emite [GroupUiEffect.GroupDeleted] para que GroupsScreen
     * muestre el snackbar; la navegación de salida la maneja NavGraph al ver el grupo
     * desaparecer del listener en vivo.
     */
    fun deleteGroup(group: Group, requesterUserId: String) {
        if (!canManageGroup(group, requesterUserId)) return
        viewModelScope.launch {
            _groupSettingsState.value = GroupSettingsUiState.Working
            try {
                deleteGroupUseCase(group.id)
                _groupSettingsState.value = GroupSettingsUiState.Idle
                logger.logEvent(AppEvent.GroupDeleted(group.id))
                effects.send(GroupUiEffect.GroupDeleted(group.name))
            } catch (e: Exception) {
                _groupSettingsState.value =
                    GroupSettingsUiState.Error(e.message ?: "Error al eliminar el grupo")
            }
        }
    }

    /**
     * Quita a [targetUserId] del grupo y opcionalmente lo bloquea vía [removeGroupMemberUseCase].
     * Aplica el guard de permisos [canManageGroup] y evita que el admin se quite a sí mismo.
     */
    fun removeGroupMember(group: Group, requesterUserId: String, targetUserId: String, block: Boolean) {
        if (!canManageGroup(group, requesterUserId)) return
        if (targetUserId == group.createdBy) return
        viewModelScope.launch {
            _groupSettingsState.value = GroupSettingsUiState.Working
            try {
                removeGroupMemberUseCase(group.id, targetUserId, block)
                _groupSettingsState.value = GroupSettingsUiState.Idle
                logger.logEvent(AppEvent.GroupMemberRemoved(group.id, targetUserId, block))
            } catch (e: Exception) {
                _groupSettingsState.value =
                    GroupSettingsUiState.Error(e.message ?: "Error al quitar al miembro")
            }
        }
    }

    /**
     * Desbloquea a [targetUserId] del grupo vía [unblockGroupMemberUseCase].
     * Aplica el guard de permisos [canManageGroup].
     */
    fun unblockGroupMember(group: Group, requesterUserId: String, targetUserId: String) {
        if (!canManageGroup(group, requesterUserId)) return
        viewModelScope.launch {
            _groupSettingsState.value = GroupSettingsUiState.Working
            try {
                unblockGroupMemberUseCase(group.id, targetUserId)
                _groupSettingsState.value = GroupSettingsUiState.Idle
                logger.logEvent(AppEvent.GroupMemberUnblocked(group.id, targetUserId))
            } catch (e: Exception) {
                _groupSettingsState.value =
                    GroupSettingsUiState.Error(e.message ?: "Error al desbloquear al miembro")
            }
        }
    }

    /** Restablece groupSettingsState a Idle. Llamar al entrar/salir de GroupSettingsScreen. */
    fun resetGroupSettingsState() {
        _groupSettingsState.value = GroupSettingsUiState.Idle
    }

    /**
     * Solo el creador puede renombrar o eliminar un grupo, y nunca el grupo global ni el
     * grupo mock de datos de prueba (su id no existe en Firestore). Mismo espíritu que el
     * guard de [uploadGroupPhoto]/[setGroupIcon].
     */
    private fun canManageGroup(group: Group, requesterUserId: String): Boolean =
        group.createdBy == requesterUserId &&
            group.id != GlobalGroupConstants.GROUP_ID &&
            group.id != MockDataProvider.MOCK_GROUP_ID

    fun onSignOut() {
        viewModelScope.launch {
            userRepository.signOut()
            effects.send(GroupUiEffect.NavigateToLogin)
        }
    }
}
