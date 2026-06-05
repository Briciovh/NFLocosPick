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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.presentation.common.TeamLogo
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakePickItem
import com.softeen.nflocospicks.presentation.preview.fakePickItemLocked
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val kickoffDisplayFormat = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())

@Composable
fun PickScreen(
    onNavigateBack: () -> Unit,
    viewModel: PickViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    PickScreenContent(
        uiState        = uiState,
        errorMessage   = errorMessage,
        onNavigateBack = onNavigateBack,
        onRetry        = { viewModel.loadData() },
        onPick         = { gameId, teamAbbr, kickoffTime ->
            viewModel.submitPick(gameId, teamAbbr, kickoffTime)
        },
        onErrorShown   = { viewModel.onErrorShown() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickScreenContent(
    uiState: PickUiState,
    errorMessage: String?,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onPick: (String, String, Long) -> Unit,
    onErrorShown: () -> Unit
) {
    val appColors     = LocalAppColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        containerColor = appColors.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = appColors.surfaceVariant,
                    contentColor   = appColors.onSurface,
                    actionColor    = appColors.primary
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "NFL PICKS",
                            color      = appColors.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (uiState is PickUiState.Success) {
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
            is PickUiState.Loading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.primary)
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
                        text      = uiState.message,
                        color     = MaterialTheme.colorScheme.error,
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
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

            is PickUiState.Success -> {
                if (uiState.items.isEmpty()) {
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
                        items(uiState.items, key = { it.game.id }) { item ->
                            GamePickCard(
                                item   = item,
                                onPick = { teamAbbr ->
                                    onPick(item.game.id, teamAbbr, item.game.kickoffTime)
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
    val appColors = LocalAppColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Hora + chip de estado
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
                GameStatusChip(status = game.status, isLocked = item.isLocked)
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
                    record     = game.awayTeamRecord,
                    score      = game.awayScore,
                    label      = "VISITANTE",
                    isSelected = item.pickedTeam == game.awayTeamAbbr,
                    isLocked   = item.isLocked,
                    modifier   = Modifier.weight(1f),
                    onClick    = { onPick(game.awayTeamAbbr) }
                )
                Text(
                    text       = "@",
                    color      = appColors.secondary,
                    fontWeight = FontWeight.ExtraBold
                )
                TeamPickButton(
                    abbr       = game.homeTeamAbbr,
                    name       = game.homeTeam,
                    record     = game.homeTeamRecord,
                    score      = game.homeScore,
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
    record: String?,
    score: Int?,
    label: String,
    isSelected: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appColors      = LocalAppColors.current
    val containerColor = if (isSelected) appColors.primary else appColors.surface
    val contentColor   = if (isSelected) appColors.onPrimary else appColors.onSurface

    Button(
        onClick  = onClick,
        enabled  = !isLocked,
        modifier = modifier,
        shape    = RoundedCornerShape(8.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = containerColor,
            contentColor           = contentColor,
            disabledContainerColor = if (isSelected) appColors.primary.copy(alpha = 0.5f)
                                     else appColors.surface.copy(alpha = 0.7f),
            disabledContentColor   = if (isSelected) appColors.onPrimary.copy(alpha = 0.6f)
                                     else appColors.secondary
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
            if (record != null) {
                Text(
                    text      = "($record)",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = contentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            if (score != null) {
                Text(
                    text       = score.toString(),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = contentColor,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GameStatusChip(status: GameStatus, isLocked: Boolean) {
    val appColors = LocalAppColors.current
    val (label, tint) = when {
        status == GameStatus.FINAL       -> "FINAL"      to MaterialTheme.colorScheme.error
        status == GameStatus.IN_PROGRESS -> "EN VIVO"    to appColors.primary
        isLocked                         -> "🔒 CERRADO" to MaterialTheme.colorScheme.error
        else                             -> return
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = tint.copy(alpha = 0.15f)
    ) {
        Text(
            text       = label,
            color      = tint,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun PickScreenSuccessPreview() {
    PreviewWrapper {
        PickScreenContent(
            uiState        = PickUiState.Success(
                weekId = "2025-week-12",
                items  = listOf(fakePickItem, fakePickItemLocked)
            ),
            errorMessage   = null,
            onNavigateBack = {},
            onRetry        = {},
            onPick         = { _, _, _ -> },
            onErrorShown   = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun PickScreenLoadingPreview() {
    PreviewWrapper {
        PickScreenContent(
            uiState        = PickUiState.Loading,
            errorMessage   = null,
            onNavigateBack = {},
            onRetry        = {},
            onPick         = { _, _, _ -> },
            onErrorShown   = {}
        )
    }
}
