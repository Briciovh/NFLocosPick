package com.softeen.nflocospicks.presentation.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.GamePickResult
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeHistory
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreenContent(
        uiState        = uiState,
        onNavigateBack = onNavigateBack,
        onToggleWeek   = { viewModel.toggleWeek(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreenContent(
    uiState: HistoryUiState,
    onNavigateBack: () -> Unit,
    onToggleWeek: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Historial de picks",
                        color = appColors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = appColors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is HistoryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.primary)
                }
            }

            is HistoryUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            is HistoryUiState.Success -> {
                if (uiState.weeks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no hay picks registrados.",
                            color = appColors.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp)
                    ) {
                        items(uiState.weeks, key = { it.weekId }) { entry ->
                            WeekCard(
                                entry      = entry,
                                isExpanded = uiState.expandedWeekId == entry.weekId,
                                onToggle   = { onToggleWeek(entry.weekId) },
                                modifier   = Modifier.animateItem()
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    entry: WeekHistoryEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatWeekId(entry.weekId),
                        color = appColors.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${entry.weekPoints} pts",
                        color = appColors.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appColors.secondary
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                GamePicksList(picks = entry.picks)
            }
        }
    }
}

@Composable
private fun GamePicksList(picks: List<GamePickResult>) {
    val appColors = LocalAppColors.current
    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = appColors.secondary.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        picks.forEach { result ->
            GamePickRow(result = result)
        }
    }
}

@Composable
private fun GamePickRow(result: GamePickResult) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TeamLogo(abbr = result.game.awayTeamAbbr, size = 20.dp)
                Text(
                    text = "${result.game.awayTeamAbbr} @ ${result.game.homeTeamAbbr}",
                    color = appColors.secondary,
                    style = MaterialTheme.typography.labelSmall
                )
                TeamLogo(abbr = result.game.homeTeamAbbr, size = 20.dp)
            }
            if (result.pickedTeam != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TeamLogo(abbr = result.pickedTeam, size = 20.dp)
                    Text(
                        text = result.pickedTeam,
                        color = appColors.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Sin pick",
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (result.winnerTeamAbbr != null) {
                Text(
                    text = "Ganador: ${result.winnerTeamAbbr}",
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            text = when (result.isCorrect) {
                true  -> "✅"
                false -> "❌"
                null  -> "⏳"
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun formatWeekId(weekId: String): String {
    // "2025-week-12" → "SEMANA 12 · 2025"
    val parts = weekId.split("-")
    val year   = parts.getOrNull(0) ?: return weekId
    val number = parts.getOrNull(2) ?: return weekId
    return "SEMANA $number · $year"
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun HistoryScreenSuccessPreview() {
    PreviewWrapper {
        HistoryScreenContent(
            uiState        = HistoryUiState.Success(
                weeks          = fakeHistory,
                expandedWeekId = "2025-week-12"
            ),
            onNavigateBack = {},
            onToggleWeek   = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun HistoryScreenLoadingPreview() {
    PreviewWrapper {
        HistoryScreenContent(
            uiState        = HistoryUiState.Loading,
            onNavigateBack = {},
            onToggleWeek   = {}
        )
    }
}
