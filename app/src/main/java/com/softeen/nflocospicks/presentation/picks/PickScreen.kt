package com.softeen.nflocospicks.presentation.picks

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSCard
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import com.softeen.nflocospicks.presentation.theme.BSMuted
import com.softeen.nflocospicks.presentation.theme.BSWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PickScreen(
    onNavigateBack: () -> Unit,
    viewModel: PickViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar snackbar al recibir un error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        containerColor = BSBg,
        snackbarHost   = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData      = data,
                    containerColor    = BSCard,
                    contentColor      = BSWhite,
                    actionColor       = BSGold
                )
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TextButton(onClick = onNavigateBack) {
                    Text("← Atrás", color = BSGold)
                }
                Text(
                    text = "NFL PICKS",
                    color = BSGold,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (uiState is PickUiState.Success) {
                    val weekId = (uiState as PickUiState.Success).weekId
                    Text(
                        text = weekIdToLabel(weekId),
                        color = BSMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is PickUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BSGold)
                }
            }

            is PickUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.loadData() }) {
                        Text("Reintentar", color = BSGold)
                    }
                }
            }

            is PickUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay partidos disponibles esta semana.",
                            color = BSMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(state.items, key = { it.game.id }) { item ->
                            GamePickCard(
                                item    = item,
                                onPick  = { teamAbbr ->
                                    viewModel.submitPick(
                                        gameId      = item.game.id,
                                        teamAbbr    = teamAbbr,
                                        kickoffTime = item.game.kickoffTime
                                    )
                                }
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
private fun GamePickCard(
    item: GamePickItem,
    onPick: (String) -> Unit
) {
    val timeFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
    val game = item.game

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = BSCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Hora del partido o chip CERRADO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = timeFormat.format(Date(game.kickoffTime)),
                    color = BSMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (item.isLocked) {
                    Text(
                        text  = "🔒 CERRADO",
                        color = BSMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Botones de selección de equipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // VISITANTE
                TeamButton(
                    abbr       = game.awayTeamAbbr,
                    fullName   = game.awayTeam,
                    isSelected = item.pickedTeam == game.awayTeamAbbr,
                    isLocked   = item.isLocked,
                    modifier   = Modifier.weight(1f),
                    onClick    = { onPick(game.awayTeamAbbr) }
                )

                Text(
                    text      = "@",
                    color     = BSMuted,
                    style     = MaterialTheme.typography.bodyMedium,
                    modifier  = Modifier.align(Alignment.CenterVertically)
                )

                // LOCAL
                TeamButton(
                    abbr       = game.homeTeamAbbr,
                    fullName   = game.homeTeam,
                    isSelected = item.pickedTeam == game.homeTeamAbbr,
                    isLocked   = item.isLocked,
                    modifier   = Modifier.weight(1f),
                    onClick    = { onPick(game.homeTeamAbbr) }
                )
            }
        }
    }
}

@Composable
private fun TeamButton(
    abbr: String,
    fullName: String,
    isSelected: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) BSGold else BSCard
    val contentColor   = if (isSelected) BSHeader else BSWhite

    Button(
        onClick  = onClick,
        enabled  = !isLocked,
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = containerColor,
            contentColor           = contentColor,
            disabledContainerColor = if (isSelected) BSGold.copy(alpha = 0.5f) else BSCard.copy(alpha = 0.7f),
            disabledContentColor   = if (isSelected) BSHeader.copy(alpha = 0.6f) else BSMuted
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = abbr,
                fontWeight = FontWeight.ExtraBold,
                style      = MaterialTheme.typography.titleMedium
            )
            Text(
                text      = fullName,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** "2025-week-05" → "2025 · WEEK · 05" */
private fun weekIdToLabel(weekId: String): String =
    weekId.replace("-week-", " · WEEK · ").uppercase()
