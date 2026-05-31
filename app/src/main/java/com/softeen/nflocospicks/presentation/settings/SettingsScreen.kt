package com.softeen.nflocospicks.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.presentation.common.NflTeam
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.common.nflTeams
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import com.softeen.nflocospicks.presentation.theme.BSMuted
import com.softeen.nflocospicks.presentation.theme.BSWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: User,
    viewModel: SettingsViewModel,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BSBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración",
                        color = BSWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = BSWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BSHeader)
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

            // ── MI CUENTA ────────────────────────────────────────────────
            SectionHeader("MI CUENTA")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user = user, size = 56)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = user.displayName,
                        color = BSWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email,
                        color = BSMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider(color = BSMuted.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            // ── EQUIPO FAVORITO ──────────────────────────────────────────
            SectionHeader("EQUIPO FAVORITO")
            Spacer(Modifier.height(8.dp))

            TeamGrid(
                teams          = nflTeams,
                selectedAbbr   = prefs.favoriteTeamAbbr,
                onTeamSelected = { abbr ->
                    val newSelection = if (prefs.favoriteTeamAbbr == abbr) null else abbr
                    viewModel.setFavoriteTeam(newSelection)
                }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = BSMuted.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            // ── CERRAR SESIÓN ────────────────────────────────────────────
            TextButton(
                onClick  = onSignOut,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text  = "Cerrar sesión",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text      = title,
        color     = BSGold,
        style     = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        modifier  = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun TeamGrid(
    teams: List<NflTeam>,
    selectedAbbr: String?,
    onTeamSelected: (String) -> Unit
) {
    val rows = teams.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { team ->
                    TeamCell(
                        team       = team,
                        isSelected = team.abbr == selectedAbbr,
                        onClick    = { onTeamSelected(team.abbr) }
                    )
                }
                // Rellenar si la última fila tiene menos de 4
                repeat(4 - row.size) {
                    Spacer(Modifier.size(64.dp))
                }
            }
        }
    }
}

@Composable
private fun TeamCell(
    team: NflTeam,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor     = if (isSelected) BSGold.copy(alpha = 0.15f) else BSBg
    val borderColor = if (isSelected) BSGold else BSBg

    Column(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TeamLogo(abbr = team.abbr, size = 36.dp)
        Text(
            text  = team.abbr,
            color = if (isSelected) BSGold else BSMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
private fun UserAvatar(user: User, size: Int) {
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
            modifier         = Modifier.size(sizeDp).clip(CircleShape).background(BSGold),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color      = BSHeader,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
