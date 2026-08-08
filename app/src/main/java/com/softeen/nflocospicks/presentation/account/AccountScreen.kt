package com.softeen.nflocospicks.presentation.account

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.AuthError
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.isProfileComplete
import com.softeen.nflocospicks.domain.model.suggestedUsername
import com.softeen.nflocospicks.presentation.auth.messageRes
import com.softeen.nflocospicks.presentation.common.PhoneNumberField
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.common.UserAvatar
import com.softeen.nflocospicks.presentation.common.composeE164
import com.softeen.nflocospicks.presentation.common.defaultCountry
import com.softeen.nflocospicks.presentation.common.nflTeams
import com.softeen.nflocospicks.presentation.common.supportedCountryDialCodes
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeUser
import com.softeen.nflocospicks.presentation.preview.fakeUserNoUsername
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun AccountScreen(
    user: User,
    favoriteTeamAbbr: String?,
    viewModel: AccountViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit,
    onDeleteAccount: () -> Unit,
    deleteAccountError: AuthError?,
    onDismissDeleteAccountError: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTeamSelection: () -> Unit,
    onNavigateToChangePassword: () -> Unit
) {
    // Recomputed every recomposition (not remembered) so a snapshot listener delivering a
    // now-complete profile mid-session correctly flips this — remembering it against only
    // user.uid previously let it go stale if username/email/phone changed underneath it.
    val isFirstTimeSetup = !user.isProfileComplete
    // Falls back to a suggested username (derived from displayName/email) only when the
    // account has none yet — an existing username is never overridden.
    val initialUsername = remember(user.uid) { user.username ?: user.suggestedUsername() ?: "" }
    val usernameAvailability by viewModel.usernameAvailability.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val photoUploadState by viewModel.photoUploadState.collectAsStateWithLifecycle()
    val emailLinkState by viewModel.emailLinkState.collectAsStateWithLifecycle()
    val phoneLinkState by viewModel.phoneLinkState.collectAsStateWithLifecycle()
    val hasPasswordProvider = remember(user.uid) { viewModel.hasPasswordProvider }
    var showSavedConfirmation by remember { mutableStateOf(false) }
    // Overrides user.photoUrl the instant an upload succeeds, instead of waiting on the
    // Firestore snapshot listener (watchCurrentUser) to round-trip the new value back down.
    var localPhotoUrl by remember(user.uid) { mutableStateOf(user.photoUrl) }

    // Seeds the availability check for whatever username is already showing in the field
    // (saved or suggested) — without this, usernameAvailability stays Unknown until the
    // user retypes the field, leaving Save permanently disabled.
    LaunchedEffect(user.uid) {
        viewModel.onUsernameChanged(initialUsername, user.username)
    }

    LaunchedEffect(Unit) {
        for (effect in viewModel.effects) {
            when (effect) {
                AccountUiEffect.Saved -> {
                    if (isFirstTimeSetup) onSetupComplete() else showSavedConfirmation = true
                }
                is AccountUiEffect.PhotoUploaded -> localPhotoUrl = effect.photoUrl
            }
        }
    }

    LaunchedEffect(showSavedConfirmation) {
        if (showSavedConfirmation) {
            delay(2000)
            showSavedConfirmation = false
        }
    }

    AccountScreenContent(
        isFirstTimeSetup         = isFirstTimeSetup,
        initialUsername           = initialUsername,
        initialDisplayName        = user.displayName,
        usernameAvailability      = usernameAvailability,
        saveState                 = saveState,
        showSavedConfirmation     = showSavedConfirmation,
        photoUrl                  = localPhotoUrl,
        isUploadingPhoto          = photoUploadState is PhotoUploadState.Uploading,
        favoriteTeamAbbr          = favoriteTeamAbbr,
        onUsernameChanged         = { viewModel.onUsernameChanged(it, user.username) },
        onSave                    = { username, displayName ->
            viewModel.saveProfile(uid = user.uid, username = username, displayName = displayName)
        },
        onPhotoPicked             = { uri -> viewModel.uploadPhoto(user.uid, uri) },
        hasPasswordProvider       = hasPasswordProvider,
        email                     = user.email,
        phoneNumber               = user.phoneNumber,
        emailLinkState            = emailLinkState,
        onSendEmailLink           = { email -> viewModel.sendEmailLink(email) },
        phoneLinkState            = phoneLinkState,
        onStartPhoneLink          = viewModel::startPhoneLink,
        onVerifyPhoneLinkCode     = { code -> viewModel.verifyPhoneLinkCode(code) },
        onDeleteAccount            = onDeleteAccount,
        deleteAccountError         = deleteAccountError,
        onDismissDeleteAccountError = onDismissDeleteAccountError,
        onNavigateBack            = onNavigateBack,
        onNavigateToTeamSelection = onNavigateToTeamSelection,
        onNavigateToChangePassword = onNavigateToChangePassword
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreenContent(
    isFirstTimeSetup: Boolean,
    initialUsername: String,
    initialDisplayName: String,
    usernameAvailability: UsernameAvailability,
    saveState: AccountSaveState,
    showSavedConfirmation: Boolean,
    photoUrl: String?,
    isUploadingPhoto: Boolean,
    favoriteTeamAbbr: String?,
    onUsernameChanged: (String) -> Unit,
    onSave: (username: String, displayName: String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    hasPasswordProvider: Boolean,
    email: String,
    phoneNumber: String?,
    emailLinkState: EmailLinkState,
    onSendEmailLink: (String) -> Unit,
    phoneLinkState: PhoneLinkState,
    onStartPhoneLink: (Activity, String) -> Unit,
    onVerifyPhoneLinkCode: (String) -> Unit,
    onDeleteAccount: () -> Unit = {},
    deleteAccountError: AuthError? = null,
    onDismissDeleteAccountError: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToTeamSelection: () -> Unit,
    onNavigateToChangePassword: () -> Unit
) {
    val appColors = LocalAppColors.current
    val activity = LocalActivity.current
    val favoriteTeam = nflTeams.find { it.abbr == favoriteTeamAbbr }

    var username by remember { mutableStateOf(initialUsername) }
    var displayName by remember { mutableStateOf(initialDisplayName) }
    // Once the user edits displayName by hand, username no longer overwrites it on blur.
    var displayNameManuallyEdited by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isSaving = saveState is AccountSaveState.Saving
    val isUsernameValid = username.isNotBlank() &&
        (usernameAvailability is UsernameAvailability.Available || usernameAvailability is UsernameAvailability.Unchanged)
    val hasEmail = email.isNotBlank()
    val hasPhone = !phoneNumber.isNullOrBlank()
    val isContactInfoValid = hasEmail || hasPhone

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account_dialog_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteAccount()
                }) {
                    Text(
                        text = stringResource(R.string.settings_delete_account_dialog_confirm),
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

    val deleteErrorMessage = deleteAccountError?.let { stringResource(it.messageRes()) }
    LaunchedEffect(deleteAccountError) {
        if (deleteErrorMessage != null) {
            snackbarHostState.showSnackbar(deleteErrorMessage)
            onDismissDeleteAccountError()
        }
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
                        text       = stringResource(if (isFirstTimeSetup) R.string.account_setup_title else R.string.account_title),
                        color      = appColors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (!isFirstTimeSetup) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint               = appColors.onBackground
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isFirstTimeSetup) {
                Text(
                    text  = stringResource(R.string.account_setup_intro),
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }

            val photoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri -> uri?.let(onPhotoPicked) }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isUploadingPhoto) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(
                            photoUrl         = photoUrl,
                            displayName      = displayName,
                            favoriteTeamAbbr = favoriteTeamAbbr,
                            size             = 88.dp
                        )
                        if (isUploadingPhoto) {
                            Box(
                                modifier         = Modifier.size(88.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    Box(
                        modifier          = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(appColors.primary),
                        contentAlignment  = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_avatar),
                            tint               = appColors.onPrimary,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value         = username,
                onValueChange = {
                    username = it
                    onUsernameChanged(it)
                },
                label         = { Text(requiredFieldLabel(stringResource(R.string.account_username_label), isRequired = true)) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    // Copies username into displayName the moment this field loses focus, unless
                    // the user has already edited displayName by hand — that edit always wins.
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && !displayNameManuallyEdited) {
                            displayName = username
                        }
                    },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary,
                    focusedTextColor     = appColors.onBackground,
                    unfocusedTextColor   = appColors.onBackground,
                    cursorColor          = appColors.primary
                )
            )

            when (usernameAvailability) {
                UsernameAvailability.Checking -> Text(
                    text  = stringResource(R.string.account_username_checking),
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                UsernameAvailability.Available -> Text(
                    text  = stringResource(R.string.account_username_available),
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
                UsernameAvailability.Taken -> Text(
                    text  = stringResource(R.string.account_username_taken),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                UsernameAvailability.Error -> Text(
                    text  = stringResource(R.string.account_username_check_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                UsernameAvailability.Unchanged, UsernameAvailability.Unknown -> Unit
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = displayName,
                onValueChange = {
                    displayNameManuallyEdited = true
                    displayName = it
                },
                label         = { Text(requiredFieldLabel(stringResource(R.string.account_display_name_label), isRequired = false)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = appColors.primary,
                    unfocusedBorderColor = appColors.secondary,
                    focusedTextColor     = appColors.onBackground,
                    unfocusedTextColor   = appColors.onBackground,
                    cursorColor          = appColors.primary
                )
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(
                text       = stringResource(R.string.account_email_section),
                color      = appColors.primary,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))

            if (email.isNotBlank()) {
                Text(text = email, color = appColors.onBackground, style = MaterialTheme.typography.bodyMedium)
            } else {
                var emailInput by remember { mutableStateOf("") }
                val isSendingLink = emailLinkState is EmailLinkState.Sending

                OutlinedTextField(
                    value          = emailInput,
                    onValueChange  = { emailInput = it },
                    label          = { Text(requiredFieldLabel(stringResource(R.string.login_label_email), isRequired = !hasPhone)) },
                    singleLine     = true,
                    modifier       = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = appColors.primary,
                        unfocusedBorderColor = appColors.secondary,
                        focusedTextColor     = appColors.onBackground,
                        unfocusedTextColor   = appColors.onBackground,
                        cursorColor          = appColors.primary
                    )
                )

                when (emailLinkState) {
                    is EmailLinkState.Sent -> Text(
                        text  = stringResource(R.string.account_link_email_sent, emailLinkState.email),
                        color = appColors.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    is EmailLinkState.Error -> Text(
                        text  = stringResource(emailLinkState.error.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> Unit
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick  = { onSendEmailLink(emailInput.trim()) },
                    enabled  = emailInput.isNotBlank() && !isSendingLink,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    if (isSendingLink) {
                        CircularProgressIndicator(color = appColors.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.account_link_email_button), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(
                text       = stringResource(R.string.account_phone_section),
                color      = appColors.primary,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))

            if (!phoneNumber.isNullOrBlank()) {
                Text(text = phoneNumber, color = appColors.onBackground, style = MaterialTheme.typography.bodyMedium)
            } else {
                val context = LocalContext.current
                val supportedCountries = remember { supportedCountryDialCodes(context) }
                var selectedCountry by remember { mutableStateOf(defaultCountry(supportedCountries)) }
                var nationalNumber by remember { mutableStateOf("") }
                var codeInput by remember { mutableStateOf("") }
                // Sticks once a code has been sent, even if phoneLinkState later moves to
                // Verifying or Error — otherwise a plain `is CodeSent` check on the current
                // state alone would bounce the UI back to the phone-number step on any
                // non-CodeSent state (verifying, or a failed verification attempt).
                var showCodeStage by remember { mutableStateOf(false) }
                LaunchedEffect(phoneLinkState) {
                    if (phoneLinkState is PhoneLinkState.CodeSent) showCodeStage = true
                }

                if (showCodeStage) {
                    val isVerifying = phoneLinkState is PhoneLinkState.Verifying
                    val sentTo = (phoneLinkState as? PhoneLinkState.CodeSent)?.phoneNumber
                        ?: composeE164(selectedCountry, nationalNumber)

                    Text(
                        text  = stringResource(R.string.account_link_phone_code_sent, sentTo),
                        color = appColors.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value          = codeInput,
                        onValueChange  = { codeInput = it },
                        label          = { Text(stringResource(R.string.login_label_sms_code)) },
                        singleLine     = true,
                        modifier       = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = appColors.primary,
                            unfocusedBorderColor = appColors.secondary,
                            focusedTextColor     = appColors.onBackground,
                            unfocusedTextColor   = appColors.onBackground,
                            cursorColor          = appColors.primary
                        )
                    )
                    if (phoneLinkState is PhoneLinkState.Error) {
                        Text(
                            text  = stringResource(phoneLinkState.error.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = { onVerifyPhoneLinkCode(codeInput) },
                        enabled  = codeInput.length == 6 && !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = appColors.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.account_verify_and_link), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val isSendingCode = phoneLinkState is PhoneLinkState.Sending

                    // PhoneNumberField.label is a plain String (shared with LoginScreen), so the
                    // required/optional suffix here can't be bolded the way the other fields are.
                    val phoneRequiredSuffix = stringResource(
                        if (!hasEmail) R.string.field_required_suffix else R.string.field_optional_suffix
                    )
                    PhoneNumberField(
                        countries              = supportedCountries,
                        selectedCountry        = selectedCountry,
                        onCountrySelected      = { selectedCountry = it },
                        nationalNumber         = nationalNumber,
                        onNationalNumberChange = { nationalNumber = it },
                        label                  = "${stringResource(R.string.login_label_phone_number)}  $phoneRequiredSuffix",
                        modifier               = Modifier.fillMaxWidth()
                    )
                    if (phoneLinkState is PhoneLinkState.Error) {
                        Text(
                            text  = stringResource(phoneLinkState.error.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = {
                            activity?.let {
                                onStartPhoneLink(it, composeE164(selectedCountry, nationalNumber))
                            }
                        },
                        enabled  = nationalNumber.length == 10 && activity != null && !isSendingCode,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        if (isSendingCode) {
                            CircularProgressIndicator(color = appColors.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.login_action_send_code), color = appColors.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onNavigateToTeamSelection)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (favoriteTeam != null) {
                    TeamLogo(abbr = favoriteTeam.abbr, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text     = favoriteTeam.name,
                        color    = appColors.onSurface,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier         = Modifier.size(32.dp).clip(CircleShape)
                            .background(appColors.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = appColors.secondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text     = stringResource(R.string.settings_no_team),
                        color    = appColors.secondary,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint               = appColors.secondary
                )
            }

            Spacer(Modifier.height(16.dp))

            if (saveState is AccountSaveState.Error) {
                Text(
                    text  = stringResource(saveState.error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            if (showSavedConfirmation) {
                Text(
                    text  = stringResource(R.string.account_saved_confirmation),
                    color = Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick  = { onSave(username.trim(), displayName.trim()) },
                enabled  = isUsernameValid && isContactInfoValid && !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = appColors.primary),
                shape    = MaterialTheme.shapes.medium
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color       = appColors.onPrimary,
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text       = stringResource(R.string.account_save_button),
                        color      = appColors.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (hasPasswordProvider) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick  = onNavigateToChangePassword,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = appColors.primary),
                    border   = BorderStroke(1.dp, appColors.primary),
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text       = stringResource(R.string.account_change_password_nav_button),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Deletion is only offered on an existing account, not mid-setup.
            if (!isFirstTimeSetup) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text  = stringResource(R.string.settings_delete_account),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Appends a bold "*Obligatorio"/"(Opcional)" suffix to a field's label, per the AC that every
 *  field on this screen state explicitly whether it's required. */
@Composable
private fun requiredFieldLabel(text: String, isRequired: Boolean) = buildAnnotatedString {
    append(text)
    append("  ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(stringResource(if (isRequired) R.string.field_required_suffix else R.string.field_optional_suffix))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun AccountScreenSetupPreview() {
    PreviewWrapper {
        AccountScreenContent(
            isFirstTimeSetup         = true,
            initialUsername           = "",
            initialDisplayName        = fakeUserNoUsername.displayName,
            usernameAvailability      = UsernameAvailability.Unknown,
            saveState                 = AccountSaveState.Idle,
            showSavedConfirmation     = false,
            photoUrl                  = fakeUserNoUsername.photoUrl,
            favoriteTeamAbbr          = null,
            isUploadingPhoto          = false,
            onUsernameChanged         = {},
            onSave                    = { _, _ -> },
            onPhotoPicked             = {},
            hasPasswordProvider       = false,
            email                     = fakeUser.email,
            phoneNumber               = fakeUser.phoneNumber,
            emailLinkState            = EmailLinkState.Idle,
            onSendEmailLink           = {},
            phoneLinkState            = PhoneLinkState.Idle,
            onStartPhoneLink          = { _, _ -> },
            onVerifyPhoneLinkCode     = {},
            onNavigateBack            = {},
            onNavigateToTeamSelection = {},
            onNavigateToChangePassword = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun AccountScreenEditPreview() {
    PreviewWrapper {
        AccountScreenContent(
            isFirstTimeSetup         = false,
            initialUsername           = fakeUser.username ?: "",
            initialDisplayName        = fakeUser.displayName,
            usernameAvailability      = UsernameAvailability.Unchanged,
            saveState                 = AccountSaveState.Idle,
            showSavedConfirmation     = false,
            photoUrl                  = fakeUser.photoUrl,
            favoriteTeamAbbr          = "KC",
            isUploadingPhoto          = false,
            onUsernameChanged         = {},
            onSave                    = { _, _ -> },
            onPhotoPicked             = {},
            hasPasswordProvider       = false,
            email                     = fakeUser.email,
            phoneNumber               = fakeUser.phoneNumber,
            emailLinkState            = EmailLinkState.Idle,
            onSendEmailLink           = {},
            phoneLinkState            = PhoneLinkState.Idle,
            onStartPhoneLink          = { _, _ -> },
            onVerifyPhoneLinkCode     = {},
            onNavigateBack            = {},
            onNavigateToTeamSelection = {},
            onNavigateToChangePassword = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun AccountScreenUsernameTakenPreview() {
    PreviewWrapper {
        AccountScreenContent(
            isFirstTimeSetup         = true,
            initialUsername           = "bricio",
            initialDisplayName        = fakeUserNoUsername.displayName,
            usernameAvailability      = UsernameAvailability.Taken,
            saveState                 = AccountSaveState.Idle,
            showSavedConfirmation     = false,
            photoUrl                  = null,
            favoriteTeamAbbr          = null,
            isUploadingPhoto          = false,
            onUsernameChanged         = {},
            onSave                    = { _, _ -> },
            onPhotoPicked             = {},
            hasPasswordProvider       = false,
            email                     = fakeUser.email,
            phoneNumber               = fakeUser.phoneNumber,
            emailLinkState            = EmailLinkState.Idle,
            onSendEmailLink           = {},
            phoneLinkState            = PhoneLinkState.Idle,
            onStartPhoneLink          = { _, _ -> },
            onVerifyPhoneLinkCode     = {},
            onNavigateBack            = {},
            onNavigateToTeamSelection = {},
            onNavigateToChangePassword = {}
        )
    }
}
