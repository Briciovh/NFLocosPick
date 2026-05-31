package com.softeen.nflocospicks.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeGame
import com.softeen.nflocospicks.presentation.preview.fakeGameLive
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val kickoffDisplayFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScheduleScreenContent(
        uiState        = uiState,
        onNavigateBack = onNavigateBack,
        onRetry        = { viewModel.loadGames() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleScreenContent(
    uiState: ScheduleUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit
) {
    val appColors = LocalAppColors.current

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "NFL PICKS",
                            color      = appColors.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState is ScheduleUiState.Success) {
                            val weekLabel = uiState.weekId
                                .uppercase()
                                .replace("-", " · ")
                            Text(
                                text  = weekLabel,
                                color = appColors.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint               = appColors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is ScheduleUiState.Loading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.primary)
                }
            }

            is ScheduleUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text  = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors  = ButtonDefaults.buttonColors(containerColor = appColors.primary)
                    ) {
                        Text("Reintentar", color = appColors.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            is ScheduleUiState.Success -> {
                if (uiState.games.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "No hay partidos esta semana.",
                            color = appColors.secondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier            = Modifier.padding(innerPadding),
                        contentPadding      = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.games, key = { it.id }) { game ->
                            GameCard(game = game)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: Game) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Hora de kickoff + estado
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = kickoffDisplayFormat.format(Date(game.kickoffTime)),
                    color      = appColors.secondary,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(game.status)
            }

            Spacer(Modifier.height(10.dp))

            // Matchup: VISITANTE @ LOCAL
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TeamColumn(
                    abbr     = game.awayTeamAbbr,
                    name     = game.awayTeam,
                    score    = game.awayScore,
                    label    = "VISITANTE",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text       = "@",
                    color      = appColors.secondary,
                    fontWeight = FontWeight.ExtraBold
                )
                TeamColumn(
                    abbr     = game.homeTeamAbbr,
                    name     = game.homeTeam,
                    score    = game.homeScore,
                    label    = "LOCAL",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TeamColumn(
    abbr: String,
    name: String,
    score: Int?,
    label: String,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = label,
            color      = appColors.secondary,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
        TeamLogo(abbr = abbr, size = 48.dp)
        Text(
            text  = name,
            color = appColors.secondary,
            style = MaterialTheme.typography.labelSmall
        )
        score?.let {
            Text(
                text       = it.toString(),
                color      = appColors.primary,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusChip(status: GameStatus) {
    val appColors = LocalAppColors.current
    val (label, tint) = when (status) {
        GameStatus.SCHEDULED   -> "PROG"    to appColors.secondary
        GameStatus.IN_PROGRESS -> "EN VIVO" to appColors.primary
        GameStatus.FINAL       -> "FINAL"   to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = tint.copy(alpha = 0.15f)
    ) {
        Text(
            text       = label,
            color      = tint,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun ScheduleScreenSuccessPreview() {
    PreviewWrapper {
        ScheduleScreenContent(
            uiState        = ScheduleUiState.Success(
                weekId = "2025-week-12",
                games  = listOf(fakeGame, fakeGameLive)
            ),
            onNavigateBack = {},
            onRetry        = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun ScheduleScreenLoadingPreview() {
    PreviewWrapper {
        ScheduleScreenContent(
            uiState        = ScheduleUiState.Loading,
            onNavigateBack = {},
            onRetry        = {}
        )
    }
}
