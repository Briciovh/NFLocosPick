package com.softeen.nflocospicks.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.common.nflTeams
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakePrefs
import com.softeen.nflocospicks.presentation.preview.fakeUser
import com.softeen.nflocospicks.presentation.theme.AppColors
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun SettingsScreen(
    user: User,
    viewModel: SettingsViewModel,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToTeamSelection: () -> Unit,
    onNavigateToUserManagement: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    SettingsScreenContent(
        user                       = user,
        prefs                      = prefs,
        onSignOut                  = onSignOut,
        onNavigateBack             = onNavigateBack,
        onNavigateToTeamSelection  = onNavigateToTeamSelection,
        onToggleTestingData        = { viewModel.setUseTestingData(it) },
        onToggleSimulateGames      = { viewModel.setSimulateGamesStarted(it) },
        onNavigateToUserManagement = onNavigateToUserManagement
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
    onNavigateToUserManagement: () -> Unit
) {
    val appColors    = LocalAppColors.current
    val favoriteTeam = nflTeams.find { it.abbr == prefs.favoriteTeamAbbr }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Configuración", color = appColors.onBackground, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionHeader("MI CUENTA", appColors.primary)

            Row(
                modifier          = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user = user, size = 56, appColors = appColors)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text       = user.displayName,
                        color      = appColors.onBackground,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = user.email, color = appColors.secondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(color = appColors.secondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            SectionHeader("EQUIPO FAVORITO", appColors.primary)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onNavigateToTeamSelection)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (favoriteTeam != null) {
                    TeamLogo(abbr = favoriteTeam.abbr, size = 40.dp)
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
                        text     = "Ninguno",
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
                Text(text = "Cerrar sesión", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
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
    SectionHeader("MODO INSIDER", appColors.primary)
    Spacer(Modifier.height(4.dp))

    // Toggle: datos de testing
    ToggleRow(
        title       = "Datos de testing",
        description = "Usa un grupo y juegos de prueba",
        checked     = useTestingData,
        onToggle    = onToggleTesting,
        appColors   = appColors
    )

    // Toggle: simular resultados (sólo visible cuando testing está activo)
    if (useTestingData) {
        ToggleRow(
            title       = "Simular resultados",
            description = "Bloquea los juegos y genera marcadores aleatorios",
            checked     = simulateGamesStarted,
            onToggle    = onToggleSimulate,
            appColors   = appColors
        )
    }

    // Fila: gestión de usuarios
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onManageUsers)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = "Gestión de usuarios",
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
private fun UserAvatar(user: User, size: Int, appColors: AppColors) {
    val sizeDp = size.dp
    if (user.photoUrl != null) {
        AsyncImage(
            model              = user.photoUrl,
            contentDescription = user.displayName,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(sizeDp).clip(CircleShape)
        )
    } else {
        Box(
            modifier         = Modifier.size(sizeDp).clip(CircleShape).background(appColors.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color      = appColors.header,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
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
            onNavigateToUserManagement = {}
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
            onNavigateToUserManagement = {}
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
            onNavigateToUserManagement = {}
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
            onNavigateToUserManagement = {}
        )
    }
}
