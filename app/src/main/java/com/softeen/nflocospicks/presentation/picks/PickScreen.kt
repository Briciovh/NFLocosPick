package com.softeen.nflocospicks.presentation.picks

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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSCard
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import com.softeen.nflocospicks.presentation.theme.BSMuted
import com.softeen.nflocospicks.presentation.theme.BSWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val kickoffDisplayFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickScreen(
    onNavigateBack: () -> Unit,
    viewModel: PickViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar Snackbar al recibir un error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        containerColor = BSBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = BSCard,
                    contentColor   = BSWhite,
                    actionColor    = BSGold
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "MIS PICKS",
                            color      = BSGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState is PickUiState.Success) {
                            val weekLabel = (uiState as PickUiState.Success)
                                .weekId
                                .uppercase()
                                .replace("-", " · ")
                            Text(
                                text  = weekLabel,
                                color = BSMuted,
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
                            tint               = BSGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BSHeader)
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is PickUiState.Loading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BSGold)
                }
            }

            is PickUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text      = state.message,
                        color     = MaterialTheme.colorScheme.error,
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadData() },
                        colors  = ButtonDefaults.buttonColors(containerColor = BSGold)
                    ) {
                        Text("Reintentar", color = BSHeader, fontWeight = FontWeight.Bold)
                    }
                }
            }

            is PickUiState.Success -> {
                if (state.items.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "No hay partidos esta semana.",
                            color = BSMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier            = Modifier.padding(innerPadding),
                        contentPadding      = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.items, key = { it.game.id }) { item ->
                            GamePickCard(
                                item   = item,
                                onPick = { teamAbbr ->
                                    viewModel.submitPick(
                                        gameId      = item.game.id,
                                        teamAbbr    = teamAbbr,
                                        kickoffTime = item.game.kickoffTime
                                    )
                                }
                            )
                        }
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
    val game = item.game

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BSCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Hora + chip CERRADO (si aplica)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = kickoffDisplayFormat.format(Date(game.kickoffTime)),
                    color      = BSMuted,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                if (item.isLocked) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text       = "🔒 CERRADO",
                            color      = MaterialTheme.colorScheme.error,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Botones de selección: VISITANTE @ LOCAL
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TeamPickButton(
                    abbr       = game.awayTeamAbbr,
                    name       = game.awayTeam,
                    label      = "VISITANTE",
                    isSelected = item.pickedTeam == game.awayTeamAbbr,
                    isLocked   = item.isLocked,
                    modifier   = Modifier.weight(1f),
                    onClick    = { onPick(game.awayTeamAbbr) }
                )
                Text(
                    text       = "@",
                    color      = BSMuted,
                    fontWeight = FontWeight.ExtraBold
                )
                TeamPickButton(
                    abbr       = game.homeTeamAbbr,
                    name       = game.homeTeam,
                    label      = "LOCAL",
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
private fun TeamPickButton(
    abbr: String,
    name: String,
    label: String,
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
            disabledContainerColor = if (isSelected) BSGold.copy(alpha = 0.5f)
                                     else BSCard.copy(alpha = 0.7f),
            disabledContentColor   = if (isSelected) BSHeader.copy(alpha = 0.6f)
                                     else BSMuted
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
            TeamLogo(abbr = abbr, size = 40.dp)
            Text(
                text      = name,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
