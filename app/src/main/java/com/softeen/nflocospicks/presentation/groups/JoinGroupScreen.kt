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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import com.softeen.nflocospicks.presentation.theme.BSMuted
import com.softeen.nflocospicks.presentation.theme.BSWhite

/**
 * El ViewModel se pasa desde NavGraph scoped al back-stack entry de Groups.
 * El campo fuerza uppercase y limita a 6 caracteres en la UI.
 */
@Composable
fun JoinGroupScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupViewModel
) {
    var inviteCode by remember { mutableStateOf("") }
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    // Navegar de regreso cuando la acción sea exitosa
    LaunchedEffect(actionState) {
        if (actionState is GroupActionUiState.Success) {
            viewModel.resetActionState()
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BSBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Unirse a Grupo",
            color = BSGold,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase().take(6) },
            label = { Text("Código de invitación", color = BSMuted) },
            placeholder = { Text("XXXXXX", color = BSMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BSGold,
                unfocusedBorderColor = BSMuted,
                focusedTextColor = BSWhite,
                unfocusedTextColor = BSWhite,
                cursorColor = BSGold
            )
        )

        Spacer(Modifier.height(8.dp))

        if (actionState is GroupActionUiState.Error) {
            Text(
                text = (actionState as GroupActionUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.joinGroup(inviteCode) },
            enabled = inviteCode.length == 6 && actionState !is GroupActionUiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BSGold),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (actionState is GroupActionUiState.Loading) {
                CircularProgressIndicator(
                    color = BSHeader,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Unirme", color = BSHeader, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onNavigateBack) {
            Text("Cancelar", color = BSMuted)
        }
    }
}
