package com.softeen.nflocospicks.presentation.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.BuildConfig
import com.softeen.nflocospicks.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.domain.model.effectiveDisplayName
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.common.UserAvatar
import com.softeen.nflocospicks.presentation.common.nflTeams
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakePrefs
import com.softeen.nflocospicks.presentation.preview.fakeUser
import com.softeen.nflocospicks.presentation.theme.AppColors
import com.softeen.nflocospicks.presentation.theme.FontScaleOption
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun SettingsScreen(
    user: User,
    viewModel: SettingsViewModel,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTeamSelection: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    SettingsScreenContent(
        user                        = user,
        prefs                       = prefs,
        onSignOut                   = onSignOut,
        onNavigateBack              = onNavigateBack,
        onNavigateToTeamSelection   = onNavigateToTeamSelection,
        onToggleTestingData         = { viewModel.setUseTestingData(it) },
        onToggleSimulateGames       = { viewModel.setSimulateGamesStarted(it) },
        onNavigateToUserManagement  = onNavigateToUserManagement,
        onNavigateToAccount         = onNavigateToAccount,
        currentLanguageTag          = prefs.languageTag,
        onLanguageSelected          = { viewModel.setLanguage(it) },
        currentFontScale            = prefs.fontScalePreference,
        onFontScaleSelected         = { viewModel.setFontScale(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreenContent(
    user: User,
    prefs: UserPreferences,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTeamSelection: () -> Unit,
    onToggleTestingData: (Boolean) -> Unit,
    onToggleSimulateGames: (Boolean) -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToAccount: () -> Unit,
    currentLanguageTag: String?,
    onLanguageSelected: (String?) -> Unit,
    currentFontScale: String?,
    onFontScaleSelected: (String?) -> Unit
) {
    val appColors    = LocalAppColors.current
    val favoriteTeam = nflTeams.find { it.abbr == prefs.favoriteTeamAbbr }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_title), color = appColors.onBackground, fontWeight = FontWeight.Bold)
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
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SectionHeader(stringResource(R.string.settings_section_account), appColors.primary)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onNavigateToAccount)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        photoUrl         = user.photoUrl,
                        displayName      = user.effectiveDisplayName,
                        favoriteTeamAbbr = prefs.favoriteTeamAbbr,
                        size             = 56.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = user.effectiveDisplayName,
                            color      = appColors.onBackground,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = user.email, color = appColors.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint               = appColors.secondary
                    )
                }

                HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))

                SectionHeader(stringResource(R.string.settings_section_fav_team), appColors.primary)
                Spacer(Modifier.height(4.dp))

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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = favoriteTeam.name,
                                color      = appColors.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = favoriteTeam.abbr, color = appColors.secondary, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Box(
                            modifier         = Modifier.size(40.dp).clip(CircleShape)
                                .background(appColors.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("?", color = appColors.secondary, style = MaterialTheme.typography.titleMedium)
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

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))

                SectionHeader(stringResource(R.string.settings_section_language), appColors.primary)
                Spacer(Modifier.height(8.dp))
                LanguageSelector(
                    current    = currentLanguageTag,
                    onSelected = onLanguageSelected,
                    appColors  = appColors
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))

                SectionHeader(stringResource(R.string.settings_section_font_size), appColors.primary)
                Spacer(Modifier.height(8.dp))
                FontSizeSelector(
                    current    = currentFontScale,
                    onSelected = onFontScaleSelected,
                    appColors  = appColors
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))

                if (user.role == UserRole.INSIDER) {
                    InsiderSection(
                        useTestingData        = prefs.useTestingData,
                        simulateGamesStarted  = prefs.simulateGamesStarted,
                        onToggleTesting       = onToggleTestingData,
                        onToggleSimulate      = onToggleSimulateGames,
                        onManageUsers         = onNavigateToUserManagement,
                        appColors             = appColors
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick  = onSignOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Pinned outside the scrollable area so it sits at the very bottom of the screen
            // (above only the system insets) when content is short, instead of trailing the
            // last scrollable item. When content overflows, it simply stays fixed below the
            // now-shrunk scrollable area rather than requiring a scroll to reach it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_footer_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors.secondary
                )
                Text(
                    text = stringResource(R.string.settings_footer_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors.secondary
                )
            }
        }
    }
}

@Composable
private fun InsiderSection(
    useTestingData:       Boolean,
    simulateGamesStarted: Boolean,
    onToggleTesting:      (Boolean) -> Unit,
    onToggleSimulate:     (Boolean) -> Unit,
    onManageUsers:        () -> Unit,
    appColors:            AppColors
) {
    Spacer(Modifier.height(16.dp))
    SectionHeader(stringResource(R.string.settings_section_insider), appColors.primary)
    Spacer(Modifier.height(4.dp))

    // Toggle: datos de testing
    ToggleRow(
        title       = stringResource(R.string.settings_testing_title),
        description = stringResource(R.string.settings_testing_desc),
        checked     = useTestingData,
        onToggle    = onToggleTesting,
        appColors   = appColors
    )

    // Toggle: simular resultados (sólo visible cuando testing está activo)
    if (useTestingData) {
        ToggleRow(
            title       = stringResource(R.string.settings_simulate_title),
            description = stringResource(R.string.settings_simulate_desc),
            checked     = simulateGamesStarted,
            onToggle    = onToggleSimulate,
            appColors   = appColors
        )
    }

    // Fila: gestión de usuarios
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onManageUsers)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = stringResource(R.string.settings_user_management),
            color    = appColors.onBackground,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint               = appColors.secondary
        )
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
}

@Composable
private fun ToggleRow(
    title:     String,
    description: String,
    checked:   Boolean,
    onToggle:  (Boolean) -> Unit,
    appColors: AppColors
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                color      = appColors.onBackground,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = description,
                color = appColors.secondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionHeader(title: String, accentColor: androidx.compose.ui.graphics.Color) {
    Text(
        text       = title,
        color      = accentColor,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        modifier   = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun LanguageSelector(
    current: String?,
    onSelected: (String?) -> Unit,
    appColors: AppColors
) {
    val options = listOf(
        null to stringResource(R.string.settings_lang_system),
        "es" to stringResource(R.string.settings_lang_spanish),
        "en" to stringResource(R.string.settings_lang_english)
    )
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (tag, label) ->
            val isSelected     = current == tag
            val containerColor = if (isSelected) appColors.primary else appColors.surface
            val contentColor   = if (isSelected) appColors.onPrimary else appColors.secondary
            Button(
                onClick  = { onSelected(tag) },
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.small,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor   = contentColor
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FontSizeSelector(
    current: String?,
    onSelected: (String?) -> Unit,
    appColors: AppColors
) {
    val options = listOf(
        FontScaleOption.PEQUENO.key to stringResource(R.string.settings_font_size_small),
        null to stringResource(R.string.settings_font_size_normal),
        FontScaleOption.GRANDE.key to stringResource(R.string.settings_font_size_large)
    )
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            val isSelected     = current == key
            val containerColor = if (isSelected) appColors.primary else appColors.surface
            val contentColor   = if (isSelected) appColors.onPrimary else appColors.secondary
            Button(
                onClick  = { onSelected(key) },
                modifier = Modifier.weight(1f),
                shape    = MaterialTheme.shapes.small,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor   = contentColor
                )
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun SettingsScreenWithTeamPreview() {
    PreviewWrapper {
        SettingsScreenContent(
            user                       = fakeUser,
            prefs                      = fakePrefs,
            onSignOut                  = {},
            onNavigateBack             = {},
            onNavigateToTeamSelection  = {},
            onToggleTestingData        = {},
            onToggleSimulateGames      = {},
            onNavigateToUserManagement = {},
            onNavigateToAccount        = {},
            currentLanguageTag         = null,
            onLanguageSelected         = {},
            currentFontScale           = null,
            onFontScaleSelected        = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun SettingsScreenNoTeamPreview() {
    PreviewWrapper {
        SettingsScreenContent(
            user                       = fakeUser,
            prefs                      = UserPreferences(favoriteTeamAbbr = null),
            onSignOut                  = {},
            onNavigateBack             = {},
            onNavigateToTeamSelection  = {},
            onToggleTestingData        = {},
            onToggleSimulateGames      = {},
            onNavigateToUserManagement = {},
            onNavigateToAccount        = {},
            currentLanguageTag         = null,
            onLanguageSelected         = {},
            currentFontScale           = null,
            onFontScaleSelected        = {}
        )
    }
}

// Testing OFF — sección INSIDER sin sub-toggles
@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun SettingsScreenInsiderPreview() {
    PreviewWrapper {
        SettingsScreenContent(
            user                       = fakeUser.copy(role = UserRole.INSIDER),
            prefs                      = fakePrefs.copy(useTestingData = true),
            onSignOut                  = {},
            onNavigateBack             = {},
            onNavigateToTeamSelection  = {},
            onToggleTestingData        = {},
            onToggleSimulateGames      = {},
            onNavigateToUserManagement = {},
            onNavigateToAccount        = {},
            currentLanguageTag         = "es",
            onLanguageSelected         = {},
            currentFontScale           = null,
            onFontScaleSelected        = {}
        )
    }
}

// Simulación activa — ambos toggles ON
@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun SettingsScreenInsiderSimulatingPreview() {
    PreviewWrapper {
        SettingsScreenContent(
            user  = fakeUser.copy(role = UserRole.INSIDER),
            prefs = fakePrefs.copy(useTestingData = true, simulateGamesStarted = true),
            onSignOut                  = {},
            onNavigateBack             = {},
            onNavigateToTeamSelection  = {},
            onToggleTestingData        = {},
            onToggleSimulateGames      = {},
            onNavigateToUserManagement = {},
            onNavigateToAccount        = {},
            currentLanguageTag         = "en",
            onLanguageSelected         = {},
            currentFontScale           = FontScaleOption.GRANDE.key,
            onFontScaleSelected        = {}
        )
    }
}
