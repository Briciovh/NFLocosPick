package com.softeen.nflocospicks.presentation.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSCard
import com.softeen.nflocospicks.presentation.theme.BSGold
import com.softeen.nflocospicks.presentation.theme.BSHeader
import com.softeen.nflocospicks.presentation.theme.BSMuted
import com.softeen.nflocospicks.presentation.theme.BSSurface
import com.softeen.nflocospicks.presentation.theme.BSWhite

@Composable
fun GroupsScreen(
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToSchedule: (String) -> Unit,
    onNavigateToPicks: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val listState by viewModel.groupListState.collectAsStateWithLifecycle()

    // Consume efectos de un solo disparo (navegación)
    LaunchedEffect(Unit) {
        for (effect in viewModel.effects) {
            when (effect) {
                is GroupUiEffect.NavigateToSchedule -> onNavigateToSchedule(effect.groupId)
                is GroupUiEffect.NavigateToPicks    -> onNavigateToPicks(effect.groupId)
                GroupUiEffect.NavigateToLogin       -> onSignedOut()
            }
        }
    }

    Scaffold(
        containerColor = BSBg,
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // FAB secundario: unirse con código
                FloatingActionButton(
                    onClick = onNavigateToJoinGroup,
                    containerColor = BSSurface
                ) {
                    Text("🔗", style = MaterialTheme.typography.titleMedium)
                }
                // FAB principal: crear grupo
                FloatingActionButton(
                    onClick = onNavigateToCreateGroup,
                    containerColor = BSGold
                ) {
                    Text("+", color = BSHeader, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Mis Grupos",
                color = BSGold,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            when (val state = listState) {
                is GroupListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BSGold)
                    }
                }

                is GroupListUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                is GroupListUiState.Success -> {
                    if (state.groups.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aún no tienes grupos.\nCrea uno o únete con un código.",
                                color = BSMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.groups, key = { it.id }) { group ->
                                GroupCard(
                                    group        = group,
                                    onClick      = { viewModel.onGroupClicked(group.id) },
                                    onPicksClick = { viewModel.onPicksClicked(group.id) }
                                )
                            }
                        }
                    }

                    // Cerrar sesión al fondo de la pantalla
                    TextButton(
                        onClick = { viewModel.onSignOut() },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    ) {
                        Text("Cerrar sesión", color = BSMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onClick: () -> Unit,
    onPicksClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BSCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.name,
                color = BSWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Código: ${group.inviteCode}  •  ${group.memberIds.size} miembro(s)",
                color = BSMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick  = onPicksClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("🏈 MIS PICKS", color = BSGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
