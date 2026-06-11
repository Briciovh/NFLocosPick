package com.softeen.nflocospicks.presentation.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeBoardMessages
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BoardScreen(
    onNavigateBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BoardScreenContent(
        uiState              = uiState,
        onNavigateBack       = onNavigateBack,
        onInputChanged       = viewModel::onInputChanged,
        onSendOrSave         = viewModel::sendOrSaveMessage,
        onStartEditing       = viewModel::startEditing,
        onCancelEditing      = viewModel::cancelEditing,
        onDeleteMessage      = viewModel::deleteMessage,
        onToggleAnnouncement = viewModel::toggleAnnouncement,
        onSnackbarDismissed  = viewModel::clearSnackbar
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BoardScreenContent(
    uiState: BoardUiState,
    onNavigateBack: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSendOrSave: () -> Unit,
    onStartEditing: (BoardMessage) -> Unit,
    onCancelEditing: () -> Unit,
    onDeleteMessage: (BoardMessage) -> Unit,
    onToggleAnnouncement: (BoardMessage) -> Unit,
    onSnackbarDismissed: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    var messageToDelete by remember { mutableStateOf<BoardMessage?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onSnackbarDismissed()
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text(stringResource(R.string.board_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(messageToDelete!!)
                    messageToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.board_delete_confirm_ok),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = appColors.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.board_title),
                        color = appColors.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = appColors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = appColors.primary)
                        }
                    }
                    uiState.error != null -> {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    uiState.messages.isEmpty() -> {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.board_empty),
                                color = appColors.secondary,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                val isOwn = message.senderId == uiState.currentUserId
                                MessageItem(
                                    message              = message,
                                    isOwnMessage         = isOwn,
                                    canDelete            = isOwn || uiState.isGroupAdmin,
                                    canEdit              = isOwn,
                                    canToggleAnnouncement = uiState.isGroupAdmin,
                                    onStartEditing       = { onStartEditing(message) },
                                    onDeleteRequested    = { messageToDelete = message },
                                    onToggleAnnouncement = { onToggleAnnouncement(message) },
                                    modifier             = Modifier.animateItem()
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }

            InputBar(
                inputText      = uiState.inputText,
                editingMessage = uiState.editingMessage,
                onInputChanged = onInputChanged,
                onSendOrSave   = onSendOrSave,
                onCancelEditing = onCancelEditing
            )
        }
    }
}

// ── Message item ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: BoardMessage,
    isOwnMessage: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    canToggleAnnouncement: Boolean,
    onStartEditing: () -> Unit,
    onDeleteRequested: () -> Unit,
    onToggleAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActions = canDelete || canEdit || canToggleAnnouncement
    var showMenu by remember { mutableStateOf(false) }

    if (message.isAnnouncement) {
        AnnouncementItem(
            message              = message,
            hasActions           = hasActions,
            canEdit              = canEdit,
            canDelete            = canDelete,
            canToggleAnnouncement = canToggleAnnouncement,
            showMenu             = showMenu,
            onLongClick          = { if (hasActions) showMenu = true },
            onDismissMenu        = { showMenu = false },
            onStartEditing       = onStartEditing,
            onDeleteRequested    = onDeleteRequested,
            onToggleAnnouncement = onToggleAnnouncement,
            modifier             = modifier
        )
    } else {
        ChatItem(
            message              = message,
            isOwnMessage         = isOwnMessage,
            hasActions           = hasActions,
            canEdit              = canEdit,
            canDelete            = canDelete,
            canToggleAnnouncement = canToggleAnnouncement,
            showMenu             = showMenu,
            onLongClick          = { if (hasActions) showMenu = true },
            onDismissMenu        = { showMenu = false },
            onStartEditing       = onStartEditing,
            onDeleteRequested    = onDeleteRequested,
            onToggleAnnouncement = onToggleAnnouncement,
            modifier             = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnnouncementItem(
    message: BoardMessage,
    hasActions: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canToggleAnnouncement: Boolean,
    showMenu: Boolean,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onStartEditing: () -> Unit,
    onDeleteRequested: () -> Unit,
    onToggleAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.primary.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = appColors.primary
                    ) {
                        Text(
                            text = stringResource(R.string.board_announcement_badge),
                            color = appColors.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = message.timestamp.toDisplayTime(),
                        color = appColors.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message.content,
                    color = appColors.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.senderName,
                        color = appColors.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (message.editedAt != null) {
                        Text(
                            text = stringResource(R.string.board_edited_label),
                            color = appColors.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        MessageContextMenu(
            expanded             = showMenu,
            canEdit              = canEdit,
            canDelete            = canDelete,
            canToggleAnnouncement = canToggleAnnouncement,
            isAnnouncement       = message.isAnnouncement,
            onDismiss            = onDismissMenu,
            onEdit               = { onDismissMenu(); onStartEditing() },
            onDelete             = { onDismissMenu(); onDeleteRequested() },
            onToggleAnnouncement = { onDismissMenu(); onToggleAnnouncement() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItem(
    message: BoardMessage,
    isOwnMessage: Boolean,
    hasActions: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canToggleAnnouncement: Boolean,
    showMenu: Boolean,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onStartEditing: () -> Unit,
    onDeleteRequested: () -> Unit,
    onToggleAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val bubbleColor = if (isOwnMessage) appColors.primary.copy(alpha = 0.85f) else appColors.surfaceVariant
    val textColor = if (isOwnMessage) appColors.onPrimary else appColors.onSurface
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = modifier.fillMaxWidth()
    ) {
        if (!isOwnMessage) {
            Row(verticalAlignment = Alignment.Bottom) {
                SenderAvatar(name = message.senderName, photoUrl = message.senderPhotoUrl)
                Spacer(Modifier.width(6.dp))
                BubbleCard(
                    message              = message,
                    bubbleColor          = bubbleColor,
                    textColor            = textColor,
                    nameColor            = appColors.primary,
                    showSenderName       = true,
                    hasActions           = hasActions,
                    onLongClick          = onLongClick
                )
            }
        } else {
            Box {
                BubbleCard(
                    message        = message,
                    bubbleColor    = bubbleColor,
                    textColor      = textColor,
                    nameColor      = appColors.onPrimary.copy(alpha = 0.7f),
                    showSenderName = false,
                    hasActions     = hasActions,
                    onLongClick    = onLongClick
                )
                MessageContextMenu(
                    expanded             = showMenu,
                    canEdit              = canEdit,
                    canDelete            = canDelete,
                    canToggleAnnouncement = canToggleAnnouncement,
                    isAnnouncement       = false,
                    onDismiss            = onDismissMenu,
                    onEdit               = { onDismissMenu(); onStartEditing() },
                    onDelete             = { onDismissMenu(); onDeleteRequested() },
                    onToggleAnnouncement = { onDismissMenu(); onToggleAnnouncement() }
                )
            }
        }
        if (!isOwnMessage) {
            Box {
                Spacer(Modifier.size(1.dp))
                MessageContextMenu(
                    expanded             = showMenu,
                    canEdit              = canEdit,
                    canDelete            = canDelete,
                    canToggleAnnouncement = canToggleAnnouncement,
                    isAnnouncement       = false,
                    onDismiss            = onDismissMenu,
                    onEdit               = { onDismissMenu(); onStartEditing() },
                    onDelete             = { onDismissMenu(); onDeleteRequested() },
                    onToggleAnnouncement = { onDismissMenu(); onToggleAnnouncement() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleCard(
    message: BoardMessage,
    bubbleColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    nameColor: androidx.compose.ui.graphics.Color,
    showSenderName: Boolean,
    hasActions: Boolean,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor),
        modifier = Modifier
            .widthIn(max = 280.dp)
            .combinedClickable(onClick = {}, onLongClick = { if (hasActions) onLongClick() })
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (showSenderName) {
                Text(
                    text = message.senderName,
                    color = nameColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (message.editedAt != null) {
                    Text(
                        text = stringResource(R.string.board_edited_label),
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Text(
                    text = message.timestamp.toDisplayTime(),
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MessageContextMenu(
    expanded: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canToggleAnnouncement: Boolean,
    isAnnouncement: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAnnouncement: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (canEdit) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.board_menu_edit)) },
                onClick = onEdit
            )
        }
        if (canToggleAnnouncement) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isAnnouncement) stringResource(R.string.board_unmark_announcement)
                        else stringResource(R.string.board_mark_announcement)
                    )
                },
                onClick = onToggleAnnouncement
            )
        }
        if (canDelete) {
            if (canEdit || canToggleAnnouncement) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.board_menu_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = onDelete
            )
        }
    }
}

// ── Avatar ────────────────────────────────────────────────────────────────────

@Composable
private fun SenderAvatar(name: String, photoUrl: String?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(appColors.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = { InitialLetter(name) }
            )
        } else {
            InitialLetter(name)
        }
    }
}

@Composable
private fun InitialLetter(name: String) {
    val appColors = LocalAppColors.current
    Text(
        text = name.firstOrNull()?.uppercase() ?: "?",
        color = appColors.primary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    inputText: String,
    editingMessage: BoardMessage?,
    onInputChanged: (String) -> Unit,
    onSendOrSave: () -> Unit,
    onCancelEditing: () -> Unit
) {
    val appColors = LocalAppColors.current
    Column {
        HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
        if (editingMessage != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.board_editing_label),
                    color = appColors.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelEditing, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_cancel),
                        tint = appColors.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.background)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.board_hint),
                        color = appColors.secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary.copy(alpha = 0.4f),
                    focusedTextColor     = appColors.onSurface,
                    unfocusedTextColor   = appColors.onSurface,
                    cursorColor          = appColors.primary
                ),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSendOrSave,
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) appColors.primary
                        else appColors.secondary.copy(alpha = 0.3f)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.board_send),
                    tint = if (inputText.isNotBlank()) appColors.onPrimary else appColors.secondary
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.toDisplayTime(): String {
    val msgDate = Date(this)
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.time
    return if (msgDate.after(todayStart)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(msgDate)
    } else {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(msgDate)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun BoardScreenPreview() {
    PreviewWrapper {
        BoardScreenContent(
            uiState = BoardUiState(
                messages      = fakeBoardMessages,
                isLoading     = false,
                currentUserId = "user_1",
                isGroupAdmin  = true
            ),
            onNavigateBack       = {},
            onInputChanged       = {},
            onSendOrSave         = {},
            onStartEditing       = {},
            onCancelEditing      = {},
            onDeleteMessage      = {},
            onToggleAnnouncement = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun BoardScreenEmptyPreview() {
    PreviewWrapper {
        BoardScreenContent(
            uiState = BoardUiState(isLoading = false),
            onNavigateBack       = {},
            onInputChanged       = {},
            onSendOrSave         = {},
            onStartEditing       = {},
            onCancelEditing      = {},
            onDeleteMessage      = {},
            onToggleAnnouncement = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun BoardScreenEditingPreview() {
    PreviewWrapper {
        BoardScreenContent(
            uiState = BoardUiState(
                messages       = fakeBoardMessages,
                isLoading      = false,
                currentUserId  = "user_1",
                inputText      = "Mensaje editado...",
                editingMessage = fakeBoardMessages.first()
            ),
            onNavigateBack       = {},
            onInputChanged       = {},
            onSendOrSave         = {},
            onStartEditing       = {},
            onCancelEditing      = {},
            onDeleteMessage      = {},
            onToggleAnnouncement = {}
        )
    }
}
