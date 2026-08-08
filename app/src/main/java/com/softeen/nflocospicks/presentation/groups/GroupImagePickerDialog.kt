package com.softeen.nflocospicks.presentation.groups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.presentation.common.groupIconMap
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Selector de imagen de grupo: foto de galería o ícono predefinido. Compartido por el
 * badge de edición en [GroupsScreenContent] y el paso opcional posterior a crear un
 * grupo en [CreateGroupScreen].
 */
@Composable
fun GroupImagePickerDialog(
    photoUiState: GroupPhotoUiState,
    onPickPhoto: (Uri) -> Unit,
    onPickIcon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isUploading = photoUiState is GroupPhotoUiState.Uploading

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickPhoto) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.group_photo_picker_title),
                color = appColors.onSurface
            )
        },
        text = {
            Column {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.group_photo_choose_gallery))
                }

                Text(
                    text = stringResource(R.string.group_photo_choose_icon),
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().size(160.dp)
                ) {
                    items(groupIconMap.entries.toList()) { (iconId, icon) ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(appColors.primary.copy(alpha = 0.18f))
                                .clickable(enabled = !isUploading) { onPickIcon(iconId) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = iconId,
                                tint = appColors.primary
                            )
                        }
                    }
                }

                if (isUploading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = appColors.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                if (photoUiState is GroupPhotoUiState.Error) {
                    Text(
                        text = photoUiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_done), color = appColors.primary)
            }
        }
    )
}
