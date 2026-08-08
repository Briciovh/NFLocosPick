package com.softeen.nflocospicks.presentation.teamselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.presentation.common.NflTeam
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.common.nflTeams
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.settings.SettingsViewModel
import com.softeen.nflocospicks.presentation.theme.AppColors
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import kotlin.math.floor

// Column count derives from actually-measured width (BoxWithConstraints), not
// WindowSizeClass — every phone in portrait stays COMPACT regardless of
// physical size, so a bucketed breakpoint never distinguishes a small phone
// from a large one. Cell size stays fixed; more/fewer columns is what adapts.
private val CELL_SIZE = 104.dp
private val GRID_MAX_WIDTH = 900.dp
private val GRID_HORIZONTAL_PADDING = 12.dp

@Composable
fun TeamSelectionScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    TeamSelectionScreenContent(
        favoriteTeamAbbr = prefs.favoriteTeamAbbr,
        onNavigateBack   = onNavigateBack,
        onSelect         = { abbr ->
            val next = if (prefs.favoriteTeamAbbr == abbr) null else abbr
            viewModel.setFavoriteTeam(next)
            onNavigateBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamSelectionScreenContent(
    favoriteTeamAbbr: String?,
    onNavigateBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Elige tu equipo",
                        color      = appColors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
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
        BoxWithConstraints(
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val gridWidth = minOf(maxWidth, GRID_MAX_WIDTH)
            val columns = floor((gridWidth - GRID_HORIZONTAL_PADDING * 2) / CELL_SIZE).toInt().coerceAtLeast(1)
            val rows = nflTeams.chunked(columns)

            LazyColumn(
                modifier            = Modifier.fillMaxHeight().widthIn(max = GRID_MAX_WIDTH),
                contentPadding      = PaddingValues(horizontal = GRID_HORIZONTAL_PADDING, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(rows) { _, row ->
                    TeamRow(
                        teams        = row,
                        columns      = columns,
                        selectedAbbr = favoriteTeamAbbr,
                        appColors    = appColors,
                        onSelect     = onSelect
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamRow(
    teams: List<NflTeam>,
    columns: Int,
    selectedAbbr: String?,
    appColors: AppColors,
    onSelect: (String) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        teams.forEach { team ->
            TeamCell(
                team       = team,
                isSelected = team.abbr == selectedAbbr,
                appColors  = appColors,
                onClick    = { onSelect(team.abbr) }
            )
        }
        repeat(columns - teams.size) { Spacer(Modifier.size(CELL_SIZE)) }
    }
}

@Composable
private fun TeamCell(
    team: NflTeam,
    isSelected: Boolean,
    appColors: AppColors,
    onClick: () -> Unit
) {
    val bgColor     = if (isSelected) appColors.primary.copy(alpha = 0.15f) else appColors.background
    val borderColor = if (isSelected) appColors.primary else appColors.background

    Column(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .border(1.dp, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TeamLogo(abbr = team.abbr, size = 76.dp)
        Text(
            text       = team.abbr,
            color      = if (isSelected) appColors.primary else appColors.secondary,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun TeamSelectionScreenPreview() {
    PreviewWrapper {
        TeamSelectionScreenContent(
            favoriteTeamAbbr = "KC",
            onNavigateBack   = {},
            onSelect         = {}
        )
    }
}
