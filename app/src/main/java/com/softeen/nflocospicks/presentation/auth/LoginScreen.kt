package com.softeen.nflocospicks.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.softeen.nflocospicks.presentation.common.TestTags
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state   by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    LaunchedEffect(Unit) {
        for (effect in viewModel.effects) {
            when (effect) {
                AuthUiEffect.NavigateToGroups -> onAuthenticated()
            }
        }
    }

    LoginScreenContent(
        state   = state,
        onSignIn = { viewModel.signIn(context) }
    )
}

@Composable
internal fun LoginScreenContent(
    state: AuthUiState,
    onSignIn: () -> Unit
) {
    val appColors = LocalAppColors.current

    Box(
        modifier         = Modifier.fillMaxSize().background(appColors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            Image(
                painter            = painterResource(R.drawable.nflocos_picks_logo),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier           = Modifier.size(160.dp)
            )

            Text(
                text       = "NFLocos Picks",
                color      = appColors.primary,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text  = stringResource(R.string.login_tagline),
                color = appColors.secondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            if (state is AuthUiState.Error) {
                Text(
                    text  = state.message,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick  = onSignIn,
                enabled  = state !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TestTags.LOGIN_SIGN_IN_BUTTON),
                colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                shape    = RoundedCornerShape(12.dp)
            ) {
                if (state is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        color       = appColors.onPrimary,
                        modifier    = Modifier.size(24.dp).testTag(TestTags.LOADING_INDICATOR),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text       = stringResource(R.string.login_sign_in_google),
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
private fun LoginScreenIdlePreview() {
    PreviewWrapper { LoginScreenContent(state = AuthUiState.Idle, onSignIn = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LoginScreenLoadingPreview() {
    PreviewWrapper { LoginScreenContent(state = AuthUiState.Loading, onSignIn = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LoginScreenErrorPreview() {
    PreviewWrapper {
        LoginScreenContent(
            state   = AuthUiState.Error("No se pudo iniciar sesión. Intenta de nuevo."),
            onSignIn = {}
        )
    }
}
