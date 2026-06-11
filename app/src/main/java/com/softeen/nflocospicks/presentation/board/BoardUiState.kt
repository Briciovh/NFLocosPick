package com.softeen.nflocospicks.presentation.board

import com.softeen.nflocospicks.domain.model.BoardMessage

data class BoardUiState(
    val messages: List<BoardMessage> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,          // error de carga inicial → reemplaza el contenido
    val snackbarMessage: String? = null, // error de operación (send/edit/delete) → Snackbar
    val currentUserId: String = "",
    val isGroupAdmin: Boolean = false,
    val inputText: String = "",
    val editingMessage: BoardMessage? = null
)
