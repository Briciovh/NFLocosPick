package com.softeen.nflocospicks.presentation.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.softeen.nflocospicks.presentation.common.PhoneNumberField
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.common.composeE164
import com.softeen.nflocospicks.presentation.common.defaultCountry
import com.softeen.nflocospicks.presentation.common.supportedCountryDialCodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state   by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current

    LoginScreenContent(
        state                     = state,
        onSignInGoogle            = { viewModel.signIn(context) },
        onSignInEmail             = viewModel::signInWithEmail,
        onSignUpEmail             = viewModel::signUpWithEmail,
        onSendSignInLink          = viewModel::sendSignInLink,
        onStartPhoneVerification  = viewModel::startPhoneVerification,
        onVerifyPhoneCode         = viewModel::verifyPhoneCode,
        onSendPasswordResetEmail  = viewModel::sendPasswordResetEmail,
        onResetState              = viewModel::resetState
    )
}

@Composable
fun LoginScreenContent(
    state: AuthUiState,
    onSignInGoogle: () -> Unit,
    onSignInEmail: (String, String) -> Unit = { _, _ -> },
    onSignUpEmail: (String, String) -> Unit = { _, _ -> },
    onSendSignInLink: (String) -> Unit = {},
    onStartPhoneVerification: (Activity, String) -> Unit = { _, _ -> },
    onVerifyPhoneCode: (String) -> Unit = {},
    onSendPasswordResetEmail: (String) -> Unit = {},
    onResetState: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val activity = LocalActivity.current

    val context = LocalContext.current
    val supportedCountries = remember { supportedCountryDialCodes(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(defaultCountry(supportedCountries)) }
    var nationalNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var useEmailLink by remember { mutableStateOf(false) }
    var usePhoneAuth by remember { mutableStateOf(false) }

    val isLoading = state is AuthUiState.Loading

    Box(
        modifier         = Modifier.fillMaxSize().background(appColors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier            = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .background(appColors.primary.copy(alpha = 0.15f), CircleShape)
                )
                Image(
                    painter            = painterResource(R.drawable.nflocos_ball_logo),
                    contentDescription = stringResource(R.string.cd_app_logo),
                    modifier           = Modifier.size(160.dp)
                )
            }

            Text(
                text       = stringResource(R.string.app_name),
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
                    text  = stringResource(state.error.messageRes()),
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when {
                usePhoneAuth -> {
                    PhoneNumberField(
                        countries          = supportedCountries,
                        selectedCountry    = selectedCountry,
                        onCountrySelected  = { selectedCountry = it },
                        nationalNumber     = nationalNumber,
                        onNationalNumberChange = { nationalNumber = it },
                        label              = stringResource(R.string.login_label_phone_number),
                        modifier           = Modifier.fillMaxWidth(),
                        numberFieldModifier = Modifier.testTag(TestTags.LOGIN_PHONE_FIELD),
                        enabled            = state !is AuthUiState.PhoneCodeSent
                    )

                    Spacer(Modifier.height(8.dp))

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.testTag(TestTags.LOADING_INDICATOR))
                    } else if (state is AuthUiState.PhoneCodeSent) {
                        OutlinedTextField(
                            value          = smsCode,
                            onValueChange  = { smsCode = it },
                            label          = { Text(stringResource(R.string.login_label_sms_code)) },
                            modifier       = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_SMS_CODE_FIELD),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick  = { onVerifyPhoneCode(smsCode) },
                            modifier = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_VERIFY_CODE_BUTTON),
                            enabled  = smsCode.length == 6
                        ) {
                            Text(stringResource(R.string.login_action_verify_code))
                        }
                    } else {
                        Button(
                            onClick  = {
                                activity?.let {
                                    onStartPhoneVerification(it, composeE164(selectedCountry, nationalNumber))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_SEND_CODE_BUTTON),
                            enabled  = nationalNumber.length == selectedCountry.nationalNumberLength && activity != null
                        ) {
                            Text(stringResource(R.string.login_action_send_code))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = { usePhoneAuth = false; onResetState() }) {
                        Text(stringResource(R.string.login_action_use_password))
                    }
                }

                useEmailLink -> {
                    OutlinedTextField(
                        value          = email,
                        onValueChange  = { email = it },
                        label          = { Text(stringResource(R.string.login_label_email)) },
                        modifier       = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_EMAIL_FIELD),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(Modifier.height(8.dp))

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.testTag(TestTags.LOADING_INDICATOR))
                    } else if (state is AuthUiState.LinkSent) {
                        Text(stringResource(R.string.login_link_sent, state.email))
                    } else {
                        Button(
                            onClick  = { onSendSignInLink(email) },
                            modifier = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_SEND_LINK_BUTTON),
                            enabled  = email.isNotBlank()
                        ) {
                            Text(stringResource(R.string.login_action_send_link))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = { useEmailLink = false; onResetState() }) {
                        Text(stringResource(R.string.login_action_use_password))
                    }
                }

                else -> {
                    OutlinedTextField(
                        value          = email,
                        onValueChange  = { email = it },
                        label          = { Text(stringResource(R.string.login_label_email)) },
                        modifier       = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_EMAIL_FIELD),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value                = password,
                        onValueChange        = { password = it },
                        label                = { Text(stringResource(R.string.login_label_password)) },
                        modifier             = Modifier.fillMaxWidth().testTag(TestTags.LOGIN_PASSWORD_FIELD),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    if (!isRegistering) {
                        if (state is AuthUiState.PasswordResetSent) {
                            Text(
                                text  = stringResource(R.string.login_password_reset_sent, state.email),
                                color = appColors.secondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            TextButton(
                                onClick  = { onSendPasswordResetEmail(email) },
                                enabled  = email.isNotBlank() && !isLoading,
                                modifier = Modifier.testTag(TestTags.LOGIN_FORGOT_PASSWORD_BUTTON)
                            ) {
                                Text(stringResource(R.string.login_action_forgot_password), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (isRegistering) onSignUpEmail(email, password)
                            else onSignInEmail(email, password)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = email.isNotBlank() && password.isNotBlank() && !isLoading
                    ) {
                        Text(
                            stringResource(
                                if (isRegistering) R.string.login_action_register
                                else R.string.login_action_login
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick  = { isRegistering = !isRegistering },
                        modifier = Modifier.testTag(TestTags.LOGIN_REGISTER_TOGGLE)
                    ) {
                        Text(
                            stringResource(
                                if (isRegistering) R.string.login_toggle_to_login
                                else R.string.login_toggle_to_register
                            )
                        )
                    }

                    TextButton(
                        onClick  = { useEmailLink = true; usePhoneAuth = false; onResetState() },
                        modifier = Modifier.testTag(TestTags.LOGIN_EMAIL_LINK_TOGGLE)
                    ) {
                        Text(stringResource(R.string.login_action_use_email_link))
                    }

                    TextButton(
                        onClick  = { usePhoneAuth = true; useEmailLink = false; onResetState() },
                        modifier = Modifier.testTag(TestTags.LOGIN_PHONE_TOGGLE)
                    ) {
                        Text(stringResource(R.string.login_action_use_phone))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Button(
                        onClick  = onSignInGoogle,
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(TestTags.LOGIN_SIGN_IN_BUTTON),
                        colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        if (isLoading) {
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
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LoginScreenIdlePreview() {
    PreviewWrapper { LoginScreenContent(state = AuthUiState.Idle, onSignInGoogle = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LoginScreenLoadingPreview() {
    PreviewWrapper { LoginScreenContent(state = AuthUiState.Loading, onSignInGoogle = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LoginScreenErrorPreview() {
    PreviewWrapper {
        LoginScreenContent(
            state          = AuthUiState.Error(AuthError.INVALID_CREDENTIALS),
            onSignInGoogle = {}
        )
    }
}
