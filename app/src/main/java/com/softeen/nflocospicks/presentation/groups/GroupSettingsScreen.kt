package com.softeen.nflocospicks.presentation.groups

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeGroup
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Pantalla de configuración de un grupo, solo para el admin (creador). Permite
 * renombrar, cambiar la imagen (reutiliza [GroupImagePickerDialog]) y eliminar el
 * grupo por completo. El borrado corre en una Cloud Function; al completarse, el
 * listener en vivo deja de devolver el grupo y esta pantalla sale sola a Groups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupListState: GroupListUiState,
    group: Group?,
    currentUserId: String?,
    photoUiState: GroupPhotoUiState,
    settingsState: GroupSettingsUiState,
    onRename: (String) -> Unit,
    onUploadPhoto: (Uri) -> Unit,
    onSetIcon: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onDismissPhotoPicker: () -> Unit,
    onNavigateBack: () -> Unit,
    onExitToGroups: () -> Unit
) {
    val appColors = LocalAppColors.current

    var showImagePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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

                if (settingsState is GroupSettingsUiState.Error) {
                    Text(
                        text = settingsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
            photoUiState = GroupPhotoUiState.Idle,
            settingsState = GroupSettingsUiState.Idle,
            onRename = {},
            onUploadPhoto = {},
            onSetIcon = {},
            onDeleteGroup = {},
            onDismissPhotoPicker = {},
            onNavigateBack = {},
            onExitToGroups = {}
        )
    }
}
