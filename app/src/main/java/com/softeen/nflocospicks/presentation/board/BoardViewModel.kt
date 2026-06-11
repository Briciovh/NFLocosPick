package com.softeen.nflocospicks.presentation.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softeen.nflocospicks.data.mock.MockDataProvider
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import com.softeen.nflocospicks.domain.usecase.DeleteBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.SendBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.SetBoardAnnouncementUseCase
import com.softeen.nflocospicks.domain.usecase.UpdateBoardMessageUseCase
import com.softeen.nflocospicks.domain.usecase.WatchBoardMessagesUseCase
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardViewModel @Inject constructor(
    savedStateHandle:                    SavedStateHandle,
    private val watchBoardMessages:      WatchBoardMessagesUseCase,
    private val sendBoardMessage:        SendBoardMessageUseCase,
    private val updateBoardMessage:      UpdateBoardMessageUseCase,
    private val deleteBoardMessage:      DeleteBoardMessageUseCase,
    private val setBoardAnnouncement:    SetBoardAnnouncementUseCase,
    private val userRepository:          UserRepository,
    private val groupRepository:         GroupRepository
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val isMockMode = groupId == MockDataProvider.MOCK_GROUP_ID

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // Local state for mock mode so send/edit/delete work in memory.
    private val _mockMessages = MutableStateFlow(emptyList<BoardMessage>())

    init {
        loadInitialData()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    private fun loadInitialData() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            val currentUserId = currentUser?.uid.orEmpty()
            Log.d(TAG, "loadInitialData: groupId=$groupId currentUserId=$currentUserId isMockMode=$isMockMode")

            val isGroupAdmin = if (isMockMode) {
                false // Real user is never mock_user_1
            } else {
                runCatching { groupRepository.getGroupById(groupId).createdBy == currentUserId }
                    .onFailure { Log.w(TAG, "getGroupById failed", it) }
                    .getOrDefault(false)
            }

            _uiState.update { it.copy(currentUserId = currentUserId, isGroupAdmin = isGroupAdmin) }

            if (isMockMode) observeMockMessages()
            else observeRealMessages()
        }
    }

    private fun observeRealMessages() {
        watchBoardMessages(groupId)
            .onEach { messages -> _uiState.update { it.copy(messages = messages, isLoading = false) } }
            .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun observeMockMessages() {
        _mockMessages.value = MockDataProvider.MOCK_BOARD_MESSAGES
        flowOf(Unit)
            .onEach { _uiState.update { it.copy(isLoading = false) } }
            .launchIn(viewModelScope)
        _mockMessages
            .onEach { messages -> _uiState.update { it.copy(messages = messages) } }
            .launchIn(viewModelScope)
    }

    // ── Input ────────────────────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ── Send / Edit ──────────────────────────────────────────────────────────

    fun sendOrSaveMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return
        val editing = _uiState.value.editingMessage
        val currentUser = userRepository.getCurrentUser()
        if (currentUser == null) {
            Log.e(TAG, "sendOrSaveMessage: currentUser is null — user not authenticated?")
            return
        }
        Log.d(TAG, "sendOrSaveMessage: groupId=$groupId senderId=${currentUser.uid} editing=${editing?.id}")

        viewModelScope.launch {
            runCatching {
                if (editing != null) {
                    if (isMockMode) {
                        _mockMessages.update { list ->
                            list.map { m ->
                                if (m.id == editing.id) m.copy(content = text, editedAt = System.currentTimeMillis())
                                else m
                            }
                        }
                    } else {
                        updateBoardMessage(groupId, editing.id, text)
                    }
                    _uiState.update { it.copy(editingMessage = null, inputText = "") }
                } else {
                    val newMessage = BoardMessage(
                        groupId        = groupId,
                        senderId       = currentUser.uid,
                        senderName     = currentUser.displayName,
                        senderPhotoUrl = currentUser.photoUrl,
                        content        = text,
                        timestamp      = System.currentTimeMillis()
                    )
                    if (isMockMode) {
                        _mockMessages.update { it + newMessage.copy(id = "mock_msg_${System.currentTimeMillis()}") }
                    } else {
                        Log.d(TAG, "sendOrSaveMessage: calling Firestore add on groups/$groupId/board")
                        sendBoardMessage(newMessage)
                        Log.d(TAG, "sendOrSaveMessage: Firestore add succeeded")
                    }
                    _uiState.update { it.copy(inputText = "") }
                }
            }.onFailure { e ->
                Log.e(TAG, "sendOrSaveMessage failed: ${e::class.simpleName} — ${e.message}", e)
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
        }
    }

    fun startEditing(message: BoardMessage) {
        if (message.senderId != _uiState.value.currentUserId) return
        _uiState.update { it.copy(editingMessage = message, inputText = message.content) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessage = null, inputText = "") }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    fun deleteMessage(message: BoardMessage) {
        val state = _uiState.value
        if (message.senderId != state.currentUserId && !state.isGroupAdmin) return

        viewModelScope.launch {
            runCatching {
                if (isMockMode) {
                    _mockMessages.update { it.filter { m -> m.id != message.id } }
                } else {
                    deleteBoardMessage(groupId, message.id)
                }
            }.onFailure { e ->
                Log.e(TAG, "deleteMessage failed: ${e.message}", e)
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
        }
    }

    // ── Announcements ────────────────────────────────────────────────────────

    fun toggleAnnouncement(message: BoardMessage) {
        if (!_uiState.value.isGroupAdmin) return

        viewModelScope.launch {
            runCatching {
                val newValue = !message.isAnnouncement
                if (isMockMode) {
                    _mockMessages.update { list ->
                        list.map { m -> if (m.id == message.id) m.copy(isAnnouncement = newValue) else m }
                    }
                } else {
                    setBoardAnnouncement(groupId, message.id, newValue)
                }
            }.onFailure { e ->
                Log.e(TAG, "toggleAnnouncement failed: ${e.message}", e)
                _uiState.update { it.copy(snackbarMessage = e.message) }
            }
        }
    }

    companion object {
        private const val TAG = "BoardViewModel"
    }
}
