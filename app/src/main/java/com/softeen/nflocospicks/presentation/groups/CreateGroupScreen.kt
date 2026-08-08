package com.softeen.nflocospicks.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.common.GroupAvatar
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeGroup
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupViewModel
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val photoUiState by viewModel.photoUiState.collectAsStateWithLifecycle()
    var createdGroup by remember { mutableStateOf<Group?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    val currentUserId = viewModel.currentUserId

    // Al crear el grupo con éxito, no navegamos de inmediato: dejamos un paso opcional
    // para personalizar la imagen del grupo recién creado (necesita su groupId).
    LaunchedEffect(actionState) {
        val state = actionState
        if (state is GroupActionUiState.Success) {
            createdGroup = state.group
            viewModel.resetActionState()
        }
    }

    val group = createdGroup
    if (group != null) {
        CreateGroupCustomizeContent(
            group             = group,
            onEditPhotoClick  = { showImagePicker = true },
            onFinish          = onNavigateBack
        )
        if (showImagePicker) {
            GroupImagePickerDialog(
                photoUiState = photoUiState,
                onPickPhoto  = { uri -> currentUserId?.let { viewModel.uploadGroupPhoto(group, it, uri) } },
                onPickIcon   = { iconId -> currentUserId?.let { viewModel.setGroupIcon(group, it, iconId) } },
                onDismiss    = {
                    showImagePicker = false
                    viewModel.resetPhotoUiState()
                }
            )
        }
    } else {
        CreateGroupScreenContent(
            actionState    = actionState,
            onNavigateBack = onNavigateBack,
            onCreateGroup  = { viewModel.createGroup(it) }
        )
    }
}

@Composable
internal fun CreateGroupScreenContent(
    actionState: GroupActionUiState,
    onNavigateBack: () -> Unit,
    onCreateGroup: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val appColors   = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.create_group_heading),
            color = appColors.primary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text(stringResource(R.string.create_group_name_hint), color = appColors.secondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = appColors.primary,
                unfocusedBorderColor = appColors.secondary,
                focusedTextColor = appColors.onBackground,
                unfocusedTextColor = appColors.onBackground,
                cursorColor = appColors.primary
            )
        )

        Spacer(Modifier.height(8.dp))

        if (actionState is GroupActionUiState.Error) {
            Text(
                text = actionState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onCreateGroup(groupName) },
            enabled = groupName.isNotBlank() && actionState !is GroupActionUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
            shape = MaterialTheme.shapes.medium
        ) {
            if (actionState is GroupActionUiState.Loading) {
                CircularProgressIndicator(
                    color = appColors.onPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.create_group_btn), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.btn_cancel), color = appColors.secondary)
        }
    }
}

/** Paso opcional post-creación: personalizar la imagen del grupo recién creado, u omitirlo. */
@Composable
internal fun CreateGroupCustomizeContent(
    group: Group,
    onEditPhotoClick: () -> Unit,
    onFinish: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.group_photo_customize_heading),
            color = appColors.primary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.group_photo_customize_subtitle),
            color = appColors.secondary,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            GroupAvatar(
                photoUrl = group.photoUrl,
                iconId   = group.iconId,
                name     = group.name,
                size     = 120.dp
            )
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(appColors.primary)
                    .clickable(onClick = onEditPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_group_photo),
                    tint               = appColors.onPrimary,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.btn_done), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onFinish) {
            Text(stringResource(R.string.btn_skip), color = appColors.secondary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun CreateGroupScreenIdlePreview() {
    PreviewWrapper {
        CreateGroupScreenContent(
            actionState    = GroupActionUiState.Idle,
            onNavigateBack = {},
            onCreateGroup  = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun CreateGroupScreenLoadingPreview() {
    PreviewWrapper {
        CreateGroupScreenContent(
            actionState    = GroupActionUiState.Loading,
            onNavigateBack = {},
            onCreateGroup  = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun CreateGroupCustomizePreview() {
    PreviewWrapper {
        CreateGroupCustomizeContent(
            group            = fakeGroup,
            onEditPhotoClick = {},
            onFinish         = {}
        )
    }
}
