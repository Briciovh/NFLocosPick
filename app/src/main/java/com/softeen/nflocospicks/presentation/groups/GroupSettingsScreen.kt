package com.softeen.nflocospicks.presentation.groups

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.GlobalGroupConstants
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.effectiveDisplayName
import com.softeen.nflocospicks.presentation.common.UserAvatar
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeGroup
import com.softeen.nflocospicks.presentation.preview.fakeUser
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Pantalla de configuración de un grupo, solo para el admin (creador). Permite
 * renombrar, cambiar la imagen (reutiliza [GroupImagePickerDialog]), gestionar miembros
 * (eliminar o bloquear/desbloquear) y eliminar el grupo por completo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupListState: GroupListUiState,
    group: Group?,
    currentUserId: String?,
    members: List<User>,
    blockedMembers: List<User>,
    photoUiState: GroupPhotoUiState,
    settingsState: GroupSettingsUiState,
    onRename: (String) -> Unit,
    onUploadPhoto: (Uri) -> Unit,
    onSetIcon: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onRemoveMember: (targetUserId: String, block: Boolean) -> Unit,
    onUnblockMember: (targetUserId: String) -> Unit,
    onDismissPhotoPicker: () -> Unit,
    onNavigateBack: () -> Unit,
    onExitToGroups: () -> Unit
) {
    val appColors = LocalAppColors.current

    var showImagePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<User?>(null) }
    var nameInput by remember(group?.name) { mutableStateOf(group?.name.orEmpty()) }

    val isAdmin = group != null && currentUserId != null && group.createdBy == currentUserId

    // Salida automática una vez que la lista está cargada (gate en Success — durante
    // Loading `group` siempre es null):
    //  · grupo ausente (borrado propio desde aquí, o externo) → directo a Groups; regresar
    //    un nivel dejaría al usuario en otra pantalla del grupo ya inexistente.
    //  · grupo presente pero el usuario no es su creador, o es el grupo global (no
    //    configurable) → regresar. No debería llegarse por la UI; es defensa en profundidad.
    LaunchedEffect(groupListState, group, currentUserId) {
        if (groupListState !is GroupListUiState.Success) return@LaunchedEffect
        when {
            group == null -> onExitToGroups()
            !isAdmin || group.id == GlobalGroupConstants.GROUP_ID -> onNavigateBack()
        }
    }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.group_settings_title),
                        color = appColors.onBackground,
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
        if (group == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = appColors.primary)
            }
        } else {
            val working = settingsState is GroupSettingsUiState.Working

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Renombrar ──
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.group_settings_rename_label)) },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onRename(nameInput) },
                    enabled = !working && nameInput.isNotBlank() && nameInput.trim() != group.name,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primary,
                        contentColor = appColors.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.group_settings_rename_action))
                }

                HorizontalDivider()

                // ── Imagen ──
                OutlinedButton(
                    onClick = { showImagePicker = true },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.group_settings_change_image))
                }

                HorizontalDivider()

                // ── Miembros ──
                Text(
                    text = stringResource(R.string.group_settings_members),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appColors.onBackground
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    members.forEach { member ->
                        val isCreator = member.uid == group.createdBy
                        val isSelf = member.uid == currentUserId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                photoUrl = member.photoUrl,
                                displayName = member.effectiveDisplayName.ifBlank { member.uid },
                                favoriteTeamAbbr = null,
                                size = 36.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.effectiveDisplayName.ifBlank { member.uid },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = appColors.onBackground
                                    )
                                    if (isCreator) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.group_member_tag_admin),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = appColors.primary
                                        )
                                    }
                                    if (isSelf) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.group_member_tag_you),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = appColors.secondary
                                        )
                                    }
                                }
                                if (member.email.isNotBlank()) {
                                    Text(
                                        text = member.email,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appColors.secondary
                                    )
                                }
                            }
                            if (!isCreator) {
                                IconButton(
                                    onClick = { selectedMember = member },
                                    enabled = !working
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.cd_group_member_actions),
                                        tint = appColors.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Bloqueados ──
                if (blockedMembers.isNotEmpty()) {
                    HorizontalDivider()

                    Text(
                        text = stringResource(R.string.group_settings_blocked),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = appColors.onBackground
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        blockedMembers.forEach { blocked ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    photoUrl = blocked.photoUrl,
                                    displayName = blocked.effectiveDisplayName.ifBlank { blocked.uid },
                                    favoriteTeamAbbr = null,
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = blocked.effectiveDisplayName.ifBlank { blocked.uid },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = appColors.onBackground
                                    )
                                    if (blocked.email.isNotBlank()) {
                                        Text(
                                            text = blocked.email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = appColors.secondary
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onUnblockMember(blocked.uid) },
                                    enabled = !working
                                ) {
                                    Text(
                                        text = stringResource(R.string.group_member_unblock),
                                        color = appColors.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Errores de renombrar / gestión de miembros / eliminar: se muestran aquí,
                // debajo de la sección de miembros y encima de la zona de peligro, para que
                // un fallo al quitar/bloquear/desbloquear quede a la vista sin hacer scroll.
                if (settingsState is GroupSettingsUiState.Error) {
                    Text(
                        text = settingsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()

                // ── Zona de peligro ──
                Button(
                    onClick = { showDeleteDialog = true },
                    enabled = !working,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.group_settings_delete))
                }
            }
        }
    }

    if (showImagePicker) {
        GroupImagePickerDialog(
            photoUiState = photoUiState,
            onPickPhoto = onUploadPhoto,
            onPickIcon = onSetIcon,
            onDismiss = {
                showImagePicker = false
                onDismissPhotoPicker()
            }
        )
    }

    selectedMember?.let { member ->
        AlertDialog(
            onDismissRequest = { selectedMember = null },
            title = {
                Text(
                    text = member.effectiveDisplayName.ifBlank { member.uid },
                    color = appColors.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.group_member_actions_body),
                    color = appColors.onBackground
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        val uid = member.uid
                        selectedMember = null
                        onRemoveMember(uid, false)
                    }) {
                        Text(
                            text = stringResource(R.string.group_member_remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = {
                        val uid = member.uid
                        selectedMember = null
                        onRemoveMember(uid, true)
                    }) {
                        Text(
                            text = stringResource(R.string.group_member_block),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMember = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.group_settings_delete_dialog_title)) },
            text = { Text(stringResource(R.string.group_settings_delete_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteGroup()
                }) {
                    Text(
                        text = stringResource(R.string.group_settings_delete_dialog_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun GroupSettingsScreenPreview() {
    PreviewWrapper {
        GroupSettingsScreen(
            groupListState = GroupListUiState.Success(listOf(fakeGroup)),
            group = fakeGroup,
            currentUserId = fakeGroup.createdBy,
            members = listOf(fakeUser),
            blockedMembers = emptyList(),
            photoUiState = GroupPhotoUiState.Idle,
            settingsState = GroupSettingsUiState.Idle,
            onRename = {},
            onUploadPhoto = {},
            onSetIcon = {},
            onDeleteGroup = {},
            onRemoveMember = { _, _ -> },
            onUnblockMember = {},
            onDismissPhotoPicker = {},
            onNavigateBack = {},
            onExitToGroups = {}
        )
    }
}
