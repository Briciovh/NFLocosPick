package com.softeen.nflocospicks.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun JoinGroupScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupViewModel
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    // Navegar de regreso cuando la acción sea exitosa
    LaunchedEffect(actionState) {
        if (actionState is GroupActionUiState.Success) {
            viewModel.resetActionState()
            onNavigateBack()
        }
    }

    JoinGroupScreenContent(
        actionState    = actionState,
        onNavigateBack = onNavigateBack,
        onJoinGroup    = { viewModel.joinGroup(it) }
    )
}

@Composable
internal fun JoinGroupScreenContent(
    actionState: GroupActionUiState,
    onNavigateBack: () -> Unit,
    onJoinGroup: (String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
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
            text = stringResource(R.string.join_group_heading),
            color = appColors.primary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase().take(6) },
            label = { Text(stringResource(R.string.join_group_code_hint), color = appColors.secondary) },
            placeholder = { Text("XXXXXX", color = appColors.secondary) },
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
            onClick = { onJoinGroup(inviteCode) },
            enabled = inviteCode.length == 6 && actionState !is GroupActionUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (actionState is GroupActionUiState.Loading) {
                CircularProgressIndicator(
                    color = appColors.onPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.join_group_btn), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.btn_cancel), color = appColors.secondary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun JoinGroupScreenIdlePreview() {
    PreviewWrapper {
        JoinGroupScreenContent(
            actionState    = GroupActionUiState.Idle,
            onNavigateBack = {},
            onJoinGroup    = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun JoinGroupScreenLoadingPreview() {
    PreviewWrapper {
        JoinGroupScreenContent(
            actionState    = GroupActionUiState.Loading,
            onNavigateBack = {},
            onJoinGroup    = {}
        )
    }
}
