package com.softeen.nflocospicks.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.presentation.auth.messageRes
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun ChangePasswordScreen(
    viewModel: ChangePasswordViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val changePasswordState by viewModel.changePasswordState.collectAsStateWithLifecycle()

    ChangePasswordScreenContent(
        changePasswordState = changePasswordState,
        onChangePassword    = { current, new -> viewModel.changePassword(current, new) },
        onNavigateBack       = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangePasswordScreenContent(
    changePasswordState: ChangePasswordState,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val appColors = LocalAppColors.current

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(changePasswordState) {
        if (changePasswordState is ChangePasswordState.Success) {
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
    }

    val isChangingPassword = changePasswordState is ChangePasswordState.Saving
    val isPasswordChangeValid = currentPassword.isNotBlank() &&
        newPassword.length >= 6 &&
        newPassword == confirmPassword

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = stringResource(R.string.account_change_password_nav_button),
                        color      = appColors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint               = appColors.onBackground
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            OutlinedTextField(
                value                = currentPassword,
                onValueChange        = { currentPassword = it },
                label                = { Text(stringResource(R.string.account_current_password_label)) },
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier             = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary,
                    focusedTextColor     = appColors.onBackground,
                    unfocusedTextColor   = appColors.onBackground,
                    cursorColor          = appColors.primary
                )
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value                = newPassword,
                onValueChange        = { newPassword = it },
                label                = { Text(stringResource(R.string.account_new_password_label)) },
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier             = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary,
                    focusedTextColor     = appColors.onBackground,
                    unfocusedTextColor   = appColors.onBackground,
                    cursorColor          = appColors.primary
                )
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value                = confirmPassword,
                onValueChange        = { confirmPassword = it },
                label                = { Text(stringResource(R.string.account_confirm_password_label)) },
                singleLine           = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier             = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary,
                    focusedTextColor     = appColors.onBackground,
                    unfocusedTextColor   = appColors.onBackground,
                    cursorColor          = appColors.primary
                )
            )

            if (changePasswordState is ChangePasswordState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(changePasswordState.error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (changePasswordState is ChangePasswordState.Success) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.account_password_changed),
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = { onChangePassword(currentPassword, newPassword) },
                enabled  = isPasswordChangeValid && !isChangingPassword,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                shape    = MaterialTheme.shapes.medium
            ) {
                if (isChangingPassword) {
                    CircularProgressIndicator(
                        color       = appColors.onPrimary,
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text       = stringResource(R.string.account_change_password_button),
                        color      = appColors.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun ChangePasswordScreenPreview() {
    PreviewWrapper {
        ChangePasswordScreenContent(
            changePasswordState = ChangePasswordState.Idle,
            onChangePassword    = { _, _ -> },
            onNavigateBack      = {}
        )
    }
}
